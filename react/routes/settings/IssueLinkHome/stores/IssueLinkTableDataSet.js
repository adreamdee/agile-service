
import { axios } from '@choerodon/boot';
import { issueLinkTypeApiConfig, issueLinkTypeApi } from '@/api';

export default ({ id, formatMessage }) => {
  async function checkLinkName(value, name, record) {
    if (record && (record.pristineData.linkName === value)) {
      return;
    }
    const res = await axios.get(`agile/v1/projects/${id}/issue_link_types/check_name?issueLinkTypeName=${value}&issueLinkTypeId=`);
    if (!res) {
      // eslint-disable-next-line consistent-return
      return formatMessage({ id: 'issue_link.checkName.repet' });
    }
  }
  return {
    autoQuery: true,
    paging: true,
    selection: false,
    fields: [
      {
        name: 'linkName',
        type: 'string',
        label: formatMessage({ id: 'issue_link.name' }),
        required: true,
        maxLength: 30,
        validator: checkLinkName,
      },
      {
        name: 'outWard',
        type: 'string',
        label: formatMessage({ id: 'issue_link.outWard' }),
        required: true,
        maxLength: 30,
      },
      {
        name: 'inWard',
        type: 'string',
        label: formatMessage({ id: 'issue_link.inWard' }),
        required: true,
        maxLength: 30,
      },
    ],
    queryFields: [
      { name: 'linkName', type: 'string', label: formatMessage({ id: 'issue_link.name' }) },
    ],
    transport: {
      read: ({ params, data }) => issueLinkTypeApiConfig.getAll({
        ...params,
        filter: data,
      }),
      create: ({ data: [data] }) => ({
        url: `/agile/v1/projects/${id}/issue_link_types`,
        method: 'post',
        data,
      }),
      update: ({ data: [data] }) => ({
        url: `/agile/v1/projects/${id}/issue_link_types`,
        method: 'put',
        data,
      }),
    },
  };
};
