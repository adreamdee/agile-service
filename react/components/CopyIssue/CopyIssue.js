import React, { Component } from 'react';
import { stores, axios, Content } from '@choerodon/boot';
import _ from 'lodash';
import {
  Modal, Form, Input, Checkbox,
} from 'choerodon-ui';

import './CopyIssue.less';

const { AppState } = stores;
const FormItem = Form.Item;

class CopyIssue extends Component {
  constructor(props) {
    super(props);
    this.state = {
      loading: false,
    };
  }

  componentDidMount() {
    setTimeout(() => {
      this.textInput.focus();
    });
  }

  handleCopyIssue = () => {
    const { applyType = 'agile' } = this.props;
    this.props.form.validateFields((err, values) => {
      if (!err) {
        const projectId = AppState.currentMenuType.id;
        const orgId = AppState.currentMenuType.organizationId;
        const {
          issueId, issue,
        } = this.props;
        const {
          issueSummary, issueName, copySubIssue, copyLinkIssue, sprint,
        } = values;
        const copyConditionVO = {
          issueLink: copyLinkIssue || false,
          sprintValues: sprint || false,
          subTask: copySubIssue || false,
          summary: issueSummary || false,
          epicName: issueName || false,
        };
        this.setState({
          loading: true,
        });
        axios.post(`/agile/v1/projects/${projectId}/issues/${issueId}/clone_issue?organizationId=${orgId}&applyType=${applyType}&orgId=${orgId}`, copyConditionVO)
          .then((res) => {
            this.setState({
              loading: false,
            });
            this.props.onOk(res);
          });
      }
    });
  };

  checkEpicNameRepeat = (rule, value, callback) => {
    if (value && value.trim()) {
      axios.get(`/agile/v1/projects/${AppState.currentMenuType.id}/issues/check_epic_name?epicName=${value.trim()}`)
        .then((res) => {
          if (res) {
            callback('史诗名称重复');
          } else {
            callback();
          }
        });
    } else {
      callback();
    }
  };

  render() {
    const {
      visible, onCancel, issueNum, issueSummary, issue,
    } = this.props;
    const { getFieldDecorator } = this.props.form;
    console.log('this.props.issue：');
    console.log(this.props.issue);
    return (
      <Modal
        className="c7n-copyIssue"
        title={`复制问题${issueNum}`}
        visible={visible || false}
        onOk={this.handleCopyIssue}
        onCancel={onCancel}
        okText="复制"
        cancelText="取消"
        confirmLoading={this.state.loading}
      >
        <Form layout="vertical" style={{ width: 472 }}>
          <FormItem style={{ marginTop: 20 }}>
            {getFieldDecorator('issueSummary', {
              rules: [{ required: true, message: '请输入概要' }],
              initialValue: issueSummary,
            })(
              <Input
                ref={(input) => { this.textInput = input; }}
                label="概要"
                maxLength={44}
              />,
            )}
          </FormItem>
          {
            issue.typeCode === 'issue_epic' && (
            <FormItem style={{ marginTop: 20 }}>
              {getFieldDecorator('issueName', {
                rules: [{ required: true, message: '请输入史诗名称' },
                  { validator: this.checkEpicNameRepeat }],
                initialValue: issue.epicName,
              })(
                <Input
                  ref={(input) => { this.textInput = input; }}
                  label="名称"
                  maxLength={20}
                />,
              )}
            </FormItem>
            )
          }
          {
            this.props.issue.closeSprint.length || this.props.issue.activeSprint ? (
              <FormItem>
                {getFieldDecorator('sprint', {})(
                  <Checkbox>
                    是否复制冲刺
                  </Checkbox>,
                )}
              </FormItem>
            ) : null
          }
          {
            this.props.issue.subIssueVOList.length ? (
              <FormItem>
                {getFieldDecorator('copySubIssue', {})(
                  <Checkbox>
                    是否复制子任务
                  </Checkbox>,
                )}
              </FormItem>
            ) : null
          }
          {
            this.props.issueLink.length ? (
              <FormItem>
                {getFieldDecorator('copyLinkIssue', {})(
                  <Checkbox>
                    是否复制关联任务
                  </Checkbox>,
                )}
              </FormItem>
            ) : null
          }
        </Form>
      </Modal>
    );
  }
}
export default Form.create({})(CopyIssue);
