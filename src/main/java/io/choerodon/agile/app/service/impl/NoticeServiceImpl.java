package io.choerodon.agile.app.service.impl;

import io.choerodon.agile.infra.feign.NotifyFeignClient;
import io.choerodon.core.domain.Page;
import io.choerodon.agile.api.vo.*;
import io.choerodon.agile.app.assembler.NoticeMessageAssembler;
import io.choerodon.agile.app.service.NoticeService;
import io.choerodon.agile.app.service.UserService;
import io.choerodon.agile.infra.dto.MessageDTO;
import io.choerodon.agile.infra.dto.MessageDetailDTO;
import io.choerodon.agile.infra.feign.NotifyFeignClient;
import io.choerodon.agile.infra.feign.vo.MessageSettingVO;
import io.choerodon.agile.infra.mapper.NoticeDetailMapper;
import io.choerodon.agile.infra.mapper.NoticeMapper;
import io.choerodon.core.exception.CommonException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by HuangFuqiang@choerodon.io on 2018/10/9.
 * Email: fuqianghuang01@gmail.com
 */
@Service
public class NoticeServiceImpl implements NoticeService {

    private static final String USERS = "specifier";

    @Autowired
    private NoticeMapper noticeMapper;

    @Autowired
    private NoticeDetailMapper noticeDetailMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private NoticeMessageAssembler noticeMessageAssembler;

    @Autowired
    private NotifyFeignClient notifyFeignClient;

    private void getIds(List<MessageDTO> result, List<Long> ids) {
        for (MessageDTO messageDTO : result) {
            if (USERS.equals(messageDTO.getNoticeType()) && messageDTO.getEnable() && messageDTO.getUser() != null && messageDTO.getUser().length() != 0 && !"null".equals(messageDTO.getUser())) {
                String[] strs = messageDTO.getUser().split(",");
                for (String str : strs) {
                    Long id = Long.parseLong(str);
                    if (!ids.contains(id)) {
                        ids.add(id);
                    }
                }
            }
        }
    }

    @Override
    public List<MessageVO> queryByProjectId(Long projectId) {
        List<MessageDTO> result = new ArrayList<>();
        List<MessageDTO> originMessageList = noticeMapper.selectAll();
        List<MessageDTO> changeMessageList = noticeMapper.selectChangeMessageByProjectId(projectId);
        for (MessageDTO messageDTO : originMessageList) {
            int flag = 0;
            for (MessageDTO changeMessageDTO : changeMessageList) {
                if (messageDTO.getEvent().equals(changeMessageDTO.getEvent()) && messageDTO.getNoticeType().equals(changeMessageDTO.getNoticeType())) {
                    flag = 1;
                    result.add(changeMessageDTO);
                    break;
                }
            }
            if (flag == 0) {
                result.add(messageDTO);
            }
        }
        List<Long> ids = new ArrayList<>();
        getIds(result, ids);
        return noticeMessageAssembler.messageDTOToVO(result, ids);
    }

    @Override
    public void updateNotice(Long projectId, List<MessageVO> messageVOList) {
        for (MessageVO messageVO : messageVOList) {
            MessageDetailDTO messageDetailDTO = new MessageDetailDTO();
            messageDetailDTO.setProjectId(projectId);
            messageDetailDTO.setEnable(messageVO.getEnable());
            messageDetailDTO.setEvent(messageVO.getEvent());
            messageDetailDTO.setNoticeType(messageVO.getNoticeType());
            messageDetailDTO.setNoticeName(messageVO.getNoticeName());
            messageDetailDTO.setUser(messageVO.getUser());
            if (noticeMapper.selectChangeMessageByDetail(projectId, messageVO.getEvent(), messageVO.getNoticeType()) == null) {
                if (noticeDetailMapper.insert(messageDetailDTO) != 1) {
                    throw new CommonException("error.messageDetailDTO.insert");
                }
            } else {
                messageDetailDTO.setId(messageVO.getId());
                messageDetailDTO.setObjectVersionNumber(messageVO.getObjectVersionNumber());
                if (noticeDetailMapper.updateByPrimaryKeySelective(messageDetailDTO) != 1) {
                    throw new CommonException("error.messageDetailDTO.update");
                }
            }
        }
    }

    private void addUsersByReporter(List<String> res, List<Long> result, IssueVO issueVO) {
        if (res.contains("reporter") && !result.contains(issueVO.getReporterId())) {
            result.add(issueVO.getReporterId());
        }
    }

    private void addUsersByAssigneer(List<String> res, List<Long> result, IssueVO issueVO) {
        if (res.contains("assignee") && issueVO.getAssigneeId() != null && !result.contains(issueVO.getAssigneeId())) {
            result.add(issueVO.getAssigneeId());
        }
    }

    private void addUsersByProjectOwner(Long projectId, List<String> res, List<Long> result) {
        if (res.contains("projectOwner")) {
            RoleAssignmentSearchVO roleAssignmentSearchVO = new RoleAssignmentSearchVO();
            Long roleId = null;
            List<RoleVO> roleVOS = userService.listRolesWithUserCountOnProjectLevel(projectId, roleAssignmentSearchVO);
            for (RoleVO roleVO : roleVOS) {
                if ("role/project/default/project-owner".equals(roleVO.getCode())) {
                    roleId = roleVO.getId();
                    break;
                }
            }
            if (roleId != null) {
                Page<UserVO> userDTOS = userService.pagingQueryUsersByRoleIdOnProjectLevel(0, 300,roleId, projectId, roleAssignmentSearchVO);
                for (UserVO userVO : userDTOS.getContent()) {
                    if (!result.contains(userVO.getId())) {
                        result.add(userVO.getId());
                    }
                }
            }
        }
    }

    private void addUsersByUsers (List<String> res, List<Long> result, Set<Long> users) {
        if (res.contains(USERS) && users != null && users.size() != 0) {
            for (Long userId : users) {
                if (!result.contains(userId)) {
                    result.add(userId);
                }
            }
        }
    }


    private String[] judgeUserType(MessageDTO changeMessageDTO, List<String> res) {
        String[] users = null;
        if (changeMessageDTO.getEnable()) {
            res.add(changeMessageDTO.getNoticeType());
            users = USERS.equals(changeMessageDTO.getNoticeType()) && changeMessageDTO.getUser() != null && changeMessageDTO.getUser().length() != 0 && !"null".equals(changeMessageDTO.getUser()) ? changeMessageDTO.getUser().split(",") : null;
        }
        return users;
    }

    @Override
    public List<Long> queryUserIdsByProjectId(Long projectId, String code, IssueVO issueVO) {
        ResponseEntity<MessageSettingVO> messageSetting = notifyFeignClient.getMessageSetting(projectId,"agile", code,null,null);
        MessageSettingVO messageVo = messageSetting.getBody();
        if(ObjectUtils.isEmpty(messageVo)){
            throw new CommonException("error.message.setting.is.null");
        }
        Set<String> type = new HashSet<>();
        Set<Long> users = new HashSet<>();
        messageVo.getTargetUserDTOS().forEach(v -> {
            type.add(v.getType());
            if (!ObjectUtils.isEmpty(v.getUserId()) && !v.getUserId().equals(0L)) {
                users.add(v.getUserId());
            }
        });
        List<String> res = type.stream().collect(Collectors.toList());
        List<Long> result = new ArrayList<>();
        addUsersByReporter(res, result, issueVO);
        addUsersByAssigneer(res, result, issueVO);
        addUsersByProjectOwner(projectId, res, result);
        addUsersByUsers(res, result, users);
        return result;
    }

    @Override
    public List<MessageDetailDTO> migrateMessageDetail() {
        List<MessageDetailDTO> messageDetailDTOS = noticeDetailMapper.selectAll();
        if(CollectionUtils.isEmpty(messageDetailDTOS)){
            return new ArrayList<>();
        }
        return messageDetailDTOS;
    }
}
