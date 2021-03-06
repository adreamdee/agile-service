package io.choerodon.agile.app.service.impl;

import io.choerodon.core.domain.Page;
import io.choerodon.agile.api.vo.*;
import io.choerodon.agile.app.service.UserService;
import io.choerodon.agile.infra.utils.ConvertUtil;
import io.choerodon.agile.infra.dto.UserDTO;
import io.choerodon.agile.infra.dto.UserMessageDTO;
import io.choerodon.agile.infra.feign.BaseFeignClient;
import io.choerodon.core.oauth.CustomUserDetails;
import io.choerodon.core.oauth.DetailsHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import java.util.*;


/**
 * @author dinghuang123@gmail.com
 * @since 2018/5/28
 */
@Component
public class UserServiceImpl implements UserService {

    private static final String PROJECT_OWNER = "role/project/default/project-owner";

    private final BaseFeignClient baseFeignClient;

    @Autowired
    public UserServiceImpl(BaseFeignClient baseFeignClient) {
        this.baseFeignClient = baseFeignClient;
    }

    @Override
    public UserDTO queryUserNameByOption(Long userId, Boolean withId) {
        CustomUserDetails customUserDetails = DetailsHelper.getUserDetails();
        if (userId == null || userId == 0) {
            return new UserDTO();
        } else {
            UserDTO userDTO = baseFeignClient.query(customUserDetails.getOrganizationId(), userId).getBody();
            if (withId) {
                userDTO.setRealName(userDTO.getLoginName() + userDTO.getRealName());
                return userDTO;
            } else {
                return userDTO;
            }
        }
    }

    @Override
    public Map<Long, UserMessageDTO> queryUsersMap(List<Long> assigneeIdList, Boolean withLoginName) {
        if (assigneeIdList == null) {
            return new HashMap<>();
        }
        Map<Long, UserMessageDTO> userMessageMap = new HashMap<>(assigneeIdList.size());
        if (!assigneeIdList.isEmpty()) {
            Long[] assigneeIds = new Long[assigneeIdList.size()];
            assigneeIdList.toArray(assigneeIds);
            List<UserDTO> userDTOS = baseFeignClient.listUsersByIds(assigneeIds, false).getBody();
            if (withLoginName) {
                userDTOS.forEach(userDO -> {
                    String ldapName = userDO.getRealName() + "（" + userDO.getLoginName() + "）";
                    String noLdapName = userDO.getRealName() + "（" + userDO.getEmail() + "）";
                    userMessageMap.put(userDO.getId(),
                            new UserMessageDTO(userDO.getLdap() ? ldapName : noLdapName,
                                    userDO.getLoginName(),
                                    userDO.getRealName(),
                                    userDO.getImageUrl(),
                                    userDO.getEmail(),
                                    userDO.getLdap(),
                                    userDO.getId()));
                });
            } else {
                userDTOS.forEach(userDO -> userMessageMap.put(userDO.getId(), new UserMessageDTO(userDO.getRealName(), userDO.getLoginName(), userDO.getRealName(), userDO.getImageUrl(), userDO.getEmail(), userDO.getLdap())));
            }
        }
        return userMessageMap;
    }

    @Override
    public List<UserVO> queryUsersByNameAndProjectId(Long projectId, String name) {
        ResponseEntity<Page<UserVO>> userList = baseFeignClient.list(projectId, name);
        if (userList != null) {
            return userList.getBody().getContent();
        } else {
            return new ArrayList<>();
        }
    }

    @Override
    public List<UserVO> listUsersByRealNames(List<String> realNames, boolean onlyEnabled) {
        if (ObjectUtils.isEmpty(realNames)) {
            return new ArrayList<>();
        } else {
            return baseFeignClient.listUsersByRealNames(onlyEnabled, new HashSet<>(realNames)).getBody();
        }
    }

    @Override
    public ProjectVO queryProject(Long projectId) {
        return baseFeignClient.queryProject(projectId).getBody();
    }

    @Override
    public List<RoleVO> listRolesWithUserCountOnProjectLevel(Long sourceId, RoleAssignmentSearchVO roleAssignmentSearchVO) {
        ResponseEntity<List<RoleVO>> roles = baseFeignClient.listRolesWithUserCountOnProjectLevel(sourceId, roleAssignmentSearchVO);
        return roles != null ? roles.getBody() : new ArrayList<>();
    }

    @Override
    public Page<UserVO> pagingQueryUsersByRoleIdOnProjectLevel(int page, int size, Long roleId, Long sourceId, RoleAssignmentSearchVO roleAssignmentSearchVO) {
        ResponseEntity<Page<UserVO>> users = baseFeignClient.pagingQueryUsersByRoleIdOnProjectLevel(page, size, roleId, sourceId, roleAssignmentSearchVO);
        return users != null ? users.getBody() : new Page<>();
    }

    @Override
    public List<UserDTO> listUsersByIds(Long[] ids) {
        ResponseEntity<List<UserDTO>> users = baseFeignClient.listUsersByIds(ids, false);
        return users != null ? users.getBody() : new ArrayList<>();
    }

    @Override
    public ProjectVO getGroupInfoByEnableProject(Long organizationId, Long projectId) {
        ResponseEntity<ProjectVO> projectDTOResponseEntity = baseFeignClient.getGroupInfoByEnableProject(ConvertUtil.getOrganizationId(projectId), projectId);
        return projectDTOResponseEntity != null ? projectDTOResponseEntity.getBody() : null;
    }

//    @Override
//    public WebHookJsonSendDTO.User getWebHookUserById(Long userId) {
//        Long[] ids = new Long[2];
//        ids[0] = userId;
//        ResponseEntity<List<UserDTO>> users = baseFeignClient.listUsersByIds(ids, false);
//        if (users != null) {
//            UserDTO userDTO = users.getBody().get(0);
//            return new WebHookJsonSendDTO.User(userDTO.getLoginName(), userDTO.getRealName());
//        } else {
//            return new WebHookJsonSendDTO.User("0", "unknown");
//        }
//    }

    @Override
    public boolean isProjectOwner(Long projectId, Long userId) {
        if (ObjectUtils.isEmpty(projectId)
                || ObjectUtils.isEmpty(userId)) {
            return false;
        }
        ResponseEntity<List<RoleVO>> response =
                baseFeignClient.getUserWithProjLevelRolesByUserId(projectId, userId);
        List<RoleVO> roles = response.getBody();
        if (ObjectUtils.isEmpty(roles)) {
            return false;
        } else {
            boolean isProjectOwner = false;
            for (RoleVO role : roles) {
                if (PROJECT_OWNER.equals(role.getCode())
                        && role.getEnabled()) {
                    isProjectOwner = true;
                    break;
                }
            }
            return isProjectOwner;
        }
    }
}
