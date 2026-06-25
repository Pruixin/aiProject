import axios from 'axios';
import { ElMessage } from 'element-plus';
import router from '../router';

// 门户端统一请求实例：所有 Java 后端接口都走 `/api` 代理。
const request = axios.create({
  baseURL: '/api',
  // 10 秒超时适合当前项目常规 CRUD/聚合接口；SSE 流式请求不走这个 axios 实例。
  timeout: 10000
});

// 请求拦截器：从 localStorage 读取 token，并按 Sa-Token 默认 header 名 `satoken` 注入。
request.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token');
    if (token) {
      // 后端使用 Sa-Token，因此 header 名不是 Authorization，而是 satoken。
      config.headers['satoken'] = token;
    }
    return config;
  },
  error => {
    return Promise.reject(error);
  }
);

// 响应拦截器：统一约定后端返回 { code, data, msg }；
// 业务成功返回 res，自定义错误弹消息，401 时清理登录态并跳回登录页。
request.interceptors.response.use(
  response => {
    // axios 的 response.data 才是后端统一返回体。
    const res = response.data;
    if (res.code === 200) {
      // 成功时直接把统一返回体继续交给业务层。
      return res;
    } else {
      // 非 200 业务码先弹出后端 msg。
      ElMessage.error(res.msg || 'Error');
      if (res.code === 401 || res.code === 403) {
        // 401/403 都视为当前门户登录态不可继续使用，清缓存后回登录页。
        localStorage.removeItem('token');
        localStorage.removeItem('roleList');
        localStorage.removeItem('nickName');
        localStorage.removeItem('avatar');
        localStorage.removeItem('avatarUpdatedAt');
        router.push('/login');
      }
      return Promise.reject(new Error(res.msg || 'Error'));
    }
  },
  error => {
    // 这里处理的是网络层异常、超时、跨域失败等，还没进入后端业务返回体。
    ElMessage.error(error.message || 'Network Error');
    return Promise.reject(error);
  }
);

export default request;
