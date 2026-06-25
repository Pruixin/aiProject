import request from '@/utils/request'
// 后台角色管理接口封装：角色 CRUD + 角色菜单授权。
export function getRoleList(data) {
  // data 通常包含分页参数和角色名称/状态筛选条件。
  return request({
    url: '/role/page',
    method: 'get',
    params: data
  })
}
// 获取单个角色详情，供编辑弹窗回显。
export function getRoleOneById(id){
  return request({
    url:'/role/'+id,
    method:'get'
  })
}
// 新增角色
export function addRole(data){
  // 新增角色（含角色名称、标识、状态等字段）。
  return request({
    url:'/role',
    method:'post',
    data
  })
}
// 修改角色
export function updateRole(data){
  // 修改角色，必须带角色 id。
  return request({
    url:'/role',
    method:'put',
    data
  })
}
// 删除角色
export function deleteRole(id){
  return request({
    url:'/role/'+id,
    method:'delete'
  })
}
// 获取角色当前菜单权限，用于树形回显。
export function getRoleMenuById(id){
  // 获取角色已授权菜单 id 列表，供树形控件回显。
  return request({
    url:'/role/power/'+id,
    method:'get'
  })
}

// 获取全部正常角色列表（不分页），用于角色选择器/分配角色抽屉。
export function fetchAllRoles() {
  return request({
    url: '/role/list',
    method: 'get',
  })
}
export function saveRoleMenu(roleId, menuIds) {
  const params = {
    roleId,
  }
  if (Array.isArray(menuIds) && menuIds.length > 0) {
    // 后端按 query 参数接收，前端把数组序列化成逗号字符串。
    params.menuIds = menuIds.join(',')
  }
  return request({
    url: '/role/power',
    method: 'post',
    params
  })
}
