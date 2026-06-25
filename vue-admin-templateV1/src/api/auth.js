import request from "@/utils/request";

// 后台认证接口封装：登录获取 token，getInfo 获取角色与菜单树。
export function login(data){
    return request({
        url:'/auth/login',
        method:'post',
        data:data
    })
}

export function getInfo(){
    return request({
        url:'/auth/getInfo',
        method:'get'
    })
}
