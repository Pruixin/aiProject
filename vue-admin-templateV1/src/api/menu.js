import request from "@/utils/request";

// 后台菜单管理接口封装：菜单树、父级选项、增改删。
export function getMentTreeData(){
    // 菜单管理页主表格直接消费树形菜单数据。
    return request({
        url:'/menu/list',
        method:'get'
    })
}

// 获取所有目录节点，供 C 类型菜单选择父级目录。
export function getMLOptions(){
    // 新增/编辑目录或页面菜单时，用于父级目录下拉。
    return request({
        url:'/menu/getMLOptions',
        method:'get'
    })
}

// 获取所有菜单节点，供 F 类型按钮选择所属页面菜单。
export function getCDOptions(){
    // 新增按钮权限时，用于选择挂在哪个页面菜单下面。
    return request({
        url:'/menu/getCDOptions',
        method:'get'
    })
}

// 新增菜单
export function addMenu(data){
    // data 为菜单编辑弹窗表单对象。
    return request({
        url:'/menu',
        method:'post',
        data
    })
}
// 获取单个菜单详情，供编辑回显。
export function getMenuOneById(id){
    return request({
        url:`/menu/${id}`,
        method:'get'
    })
}
// 修改菜单
export function updateMenu(data){
    // 与新增共用表单结构，但必须携带 id。
    return request({
        url:'/menu',
        method:'put',
        data
    })
}

// 删除菜单
export function deleteMenu(id){
    return request({
        url:`/menu/${id}`,
        method:'delete'
    })
}
