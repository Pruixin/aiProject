import request from '@/utils/request'

// 后台用户管理接口封装，对应用户列表、详情、增改删、地区字典和头像上传。
export function getUserList(params) {
  // params 一般包含 pageNum/pageSize/phone/nickName/enable。
  return request({
    url: '/user/page',
    method: 'get',
    params,
  })
}

export function getUserById(id) {
  // 编辑用户前先拉详情回显表单。
  return request({
    url: `/user/${id}`,
    method: 'get',
  })
}

export function addUser(data) {
  // data 为后台用户弹窗表单提交的 DTO。
  return request({
    url: '/user',
    method: 'post',
    data,
  })
}

export function updateUser(data) {
  // 修改接口与新增共用同一表单结构，但需额外带 id。
  return request({
    url: '/user',
    method: 'put',
    data,
  })
}

export function deleteUser(id) {
  // 删除单个用户。
  return request({
    url: `/user/${id}`,
    method: 'delete',
  })
}

export function getRegionOptions() {
  // 复用后端地区字典接口，供用户表单的 cascader 选择器使用。
  return request({
    url: '/dict/region/options',
    method: 'get',
  })
}

export function uploadFile(data) {
  // 统一走公共上传接口，返回图片 URL 后再写入用户 avatar 字段。
  return request({
    url: '/common/upload',
    method: 'post',
    data,
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
}

export function getUserRoles(userId) {
  return request({
    url: `/user/${userId}/roles`,
    method: 'get',
  })
}

export function saveUserRoles(userId, roleIds) {
  return request({
    url: `/user/${userId}/roles`,
    method: 'put',
    data: roleIds,
  })
}
