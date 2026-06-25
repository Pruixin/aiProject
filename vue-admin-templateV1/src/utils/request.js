import axios from "axios";
import { ElMessage } from "element-plus";
import { useUserTokenStore } from "@/stores/user";
import { useUserInfoStore } from "@/stores/userInfo";
import { router } from "@/router";

// 后台管理端统一请求实例：通过 Pinia 读取 Sa-Token 的 header 名和值并注入请求头。
const request = axios.create({
    baseURL:"/api",
    timeout:5000
})
// 请求拦截器
request.interceptors.request.use(config =>{
    // 从pinia获取token
    const userTokenStore = useUserTokenStore()
    let tokenName = userTokenStore.tokenName
    let tokenValue = userTokenStore.tokenValue

    // let header = {
    //     "content-type":"application/x-www-form-urlencoded"
    // }
    // if(tokenName != undefined && tokenName != ""){
    //     header[tokenName] = tokenValue
    // }
    
    // 这里打印 token 仅用于开发调试，若需要更安全可移除或改为 debug 等级日志。
    console.log("Getting token:", tokenName, tokenValue);  
    
    if(tokenName != undefined && tokenName != ""){
        // config.headers.Authorization = `Bearer ${token}`
        config.headers[tokenName] = tokenValue
    }
    return config
},e=>Promise.reject(e))

// 响应拦截器
request.interceptors.response.use((res) => {
    const data = res.data
    if (data?.code === 200) {
        return data
    }
    const message = data?.msg || data?.message || '请求失败'
    const statusCode = data?.code
    if (statusCode === 401 || statusCode === 403) {
        const tokenStore = useUserTokenStore()
        const userInfoStore = useUserInfoStore()
        tokenStore.removeToken()
        userInfoStore.clearUserInfo()
        if (router.currentRoute.value.path !== '/login') {
            ElMessage({
                type:'error',
                message
            })
            router.push('/login')
        }
        return Promise.reject(new Error(message))
    }
    ElMessage({
        type:'error',
        message
    })
    return Promise.reject(new Error(message))
},(e)=>{
    // 统一错误处理：401 清理登录态并跳登录；其它错误直接弹 msg。
    const status = e?.response?.status
    const message = e?.response?.data?.msg || e?.response?.data?.message || e?.message || '请求失败'
    if (status === 401) {
        const tokenStore = useUserTokenStore()
        const userInfoStore = useUserInfoStore()
        tokenStore.removeToken()
        userInfoStore.clearUserInfo()
        if (router.currentRoute.value.path !== '/login') {
            ElMessage({
                type:'error',
                message: message || '登录状态已失效，请重新登录'
            })
            router.push('/login')
        }
        return Promise.reject(new Error(message || '登录状态已失效，请重新登录'))
    }
    ElMessage({
        type:'error',
        message
    })
    return Promise.reject(new Error(message))
})


export default request
