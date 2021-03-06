/* eslint-disable no-unused-vars */
import { stores, Choerodon } from '@choerodon/boot';
import { DeltaOperation } from 'react-quill';
import _, { find, findIndex, chunk } from 'lodash';
import { uploadImage, uploadFile } from '../api/FileApi';

const { AppState } = stores;
const QuillDeltaToHtmlConverter = require('quill-delta-to-html');

/**
 * 将以base64的图片url数据转换为Blob
 * @param {string} urlData 用url方式表示的base64图片数据
 */
export function convertBase64UrlToBlob(urlData) {
  const bytes = window.atob(urlData.split(',')[1]); // 去掉url的头，并转换为byte

  // 处理异常,将ascii码小于0的转换为大于0
  const buffer = new ArrayBuffer(bytes.length);
  const unit8Array = new Uint8Array(buffer);
  for (let i = 0; i < bytes.length; i += 1) {
    unit8Array[i] = bytes.charCodeAt(i);
  }

  return new Blob([buffer], { type: 'image/png' });
}
/**
 * 从deltaOps中获取图片数据
 * @param {DeltaOperation []} deltaOps
 */
export function getImgInDelta(deltaOps) {
  const imgBase = [];
  const formData = new FormData();
  deltaOps.forEach((item) => {
    if (item.insert && item.insert.image) {
      if (item.insert.image.split(':').length && item.insert.image.split(':')[0] === 'data') {
        imgBase.push(item.insert.image);
        formData.append('file', convertBase64UrlToBlob(item.insert.image), 'blob.png');
      }
    }
  });
  return { imgBase, formData };
}

/**
 * 将富文本中的base64图片替换为对应的url
 * @param {{url:string} []} imgUrlList 图标url对应的
 * @param {any []} imgBase base64图片数组
 * @param {*} text 富文本的文本结构
 */
export function replaceBase64ToUrl(imgUrlList, imgBase, text) {
  const deltaOps = text;
  const imgMap = {};
  imgUrlList.forEach((imgUrl, index) => {
    imgMap[imgBase[index]] = `${imgUrl}`;
  });
  deltaOps.forEach((item, index) => {
    if (item.insert && item.insert.image && imgBase.indexOf(item.insert.image) !== -1) {
      deltaOps[index].insert.image = imgMap[item.insert.image];
    }
  });
}

/**
 * 适用于各个issue的模态框编辑界面富文本上传
 * 富文本内容上传前的图片的检测与上传
 * @param {object} text 富文本的文本结构
 * @param {object} data 要发送的数据
 * @param {function} func 回调
 */
export function beforeTextUpload(text, data, func, pro = 'description') {
  const deltaOps = text;
  const send = data;
  const { imgBase, formData } = getImgInDelta(deltaOps);
  if (imgBase.length) {
    uploadImage(formData).then((imgUrlList) => {
      replaceBase64ToUrl(imgUrlList, imgBase, deltaOps);
      const converter = new QuillDeltaToHtmlConverter(deltaOps, {});
      const html = converter.convert();
      // send.gitlabDescription = html;
      send[pro] = JSON.stringify(deltaOps);
      func(send);
    });
  } else {
    const converter = new QuillDeltaToHtmlConverter(deltaOps, {});
    const html = converter.convert();
    // send.gitlabDescription = html;
    send[pro] = JSON.stringify(deltaOps);
    func(send);
  }
}

export function returnBeforeTextUpload(text, data, func, pro = 'description') {
  const deltaOps = text;
  const send = data;
  const { imgBase, formData } = getImgInDelta(deltaOps);
  if (imgBase.length) {
    return uploadImage(formData).then((imgUrlList) => {
      replaceBase64ToUrl(imgUrlList, imgBase, deltaOps);
      const converter = new QuillDeltaToHtmlConverter(deltaOps, {});
      const html = converter.convert();
      // send.gitlabDescription = html;
      send[pro] = JSON.stringify(deltaOps);
      return func(send);
    });
  } else {
    const converter = new QuillDeltaToHtmlConverter(deltaOps, {});
    const html = converter.convert();
    // send.gitlabDescription = html;
    send[pro] = JSON.stringify(deltaOps);
    return func(send);
  }
}

/**
 * 适用于富文本附件上传以及回调
 * @param {any []} propFileList 文件列表
 * @param {function} func 回调
 * @param {{issueType:string,issueId:number,fileName:string}} config 附件上传的额外信息
 */
export function handleFileUpload(propFileList, func, config) {
  const fileList = propFileList.filter(i => !i.url);
  const formData = new FormData();
  fileList.forEach((file) => {
    // file.name = encodeURI(encodeURI(file.name));
    formData.append('file', file);
  });
  uploadFile(formData, config)
    .then((response) => {
      const newFileList = [
        {
          uid: -1,
          name: fileList[0].name,
          status: 'done',
          url: response,
        },
      ];
      Choerodon.prompt('上传成功');
      func(newFileList);
    })
    .catch((error) => {
      if (error.response) {
        Choerodon.prompt(error.response.data.message);
      } else {
        Choerodon.prompt(error.message);
      }
      const temp = propFileList.slice();
      temp.forEach((one) => {
        if (!one.url) {
          const tmp = one;
          tmp.status = 'error';
        }
      });
      func(temp);
    });
}
export function text2Delta(description) {
  if (!description) {
    return undefined;
  }
  // eslint-disable-next-line no-restricted-globals
  if (!isNaN(description)) {
    return String(description);
  }
  let temp = description;
  try {
    temp = JSON.parse(description.replace(/\\n/g, '\\n')
      .replace(/\\'/g, "\\'")
      .replace(/\\"/g, '\\"')
      .replace(/\\&/g, '\\&')
      .replace(/\\r/g, '\\r')
      .replace(/\\t/g, '\\t')
      .replace(/\\b/g, '\\b')
      .replace(/\\f/g, '\\f'));
  } catch (error) {
    temp = description;
  }
  // return temp;
  return temp || '';
}

/**
 * 将quill特有的文本结构转为html
 * @param {*} delta
 */
export function delta2Html(description) {
  const delta = text2Delta(description);
  const converter = new QuillDeltaToHtmlConverter(delta, {});
  const text = converter.convert();
  if (text.substring(0, 3) === '<p>') {
    return text.substring(3);
  } else {
    return text;
  }
}

export function validateFile(rule, fileList, callback) {
  if (fileList) {
    fileList.forEach((file) => {
      if (file.size > 1024 * 1024 * 30) {
        callback('文件不能超过30M');
      } else if (file.name && encodeURI(file.name).length > 210) {
        callback('文件名过长');
      }
    });
    callback();
  } else {
    callback();
  }
}

export function normFile(e) {
  if (Array.isArray(e)) {
    return e;
  }
  return e && e.fileList;
}
