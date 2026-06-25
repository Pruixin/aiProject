import { createRouter, createWebHistory } from 'vue-router'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import FitnessPlan from '../views/FitnessPlan.vue'
import Login from '../views/auth/Login.vue'
import Register from '../views/auth/Register.vue'
import AppShell from '../views/AppShell.vue'
import DashboardShell from '../views/dashboard/DashboardShell.vue'
import DashboardFitness from '../views/dashboard/Fitness.vue'
import DashboardDiet from '../views/dashboard/Diet.vue'
import DashboardProfile from '../views/dashboard/Profile.vue'
import SocialShell from '../views/social/Shell.vue'
import SocialChat from '../views/social/Chat.vue'
import SocialMatch from '../views/social/Match.vue'
import SocialPlaza from '../views/social/Plaza.vue'

const PORTAL_ROLE = 'users'

const readRoleList = () => {
  try {
    const parsed = JSON.parse(localStorage.getItem('roleList') || '[]')
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

const clearPortalSession = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('roleList')
  localStorage.removeItem('nickName')
  localStorage.removeItem('avatar')
  localStorage.removeItem('avatarUpdatedAt')
}

const fetchPortalRoleList = async (token) => {
  const response = await axios.get('/api/auth/getInfo', {
    headers: { satoken: token }
  })
  const res = response.data
  if (res?.code !== 200) {
    throw new Error(res?.msg || '获取用户信息失败')
  }
  const roleList = Array.isArray(res.data?.roleList) ? res.data.roleList : []
  localStorage.setItem('roleList', JSON.stringify(roleList))
  return roleList
}

// 门户端路由分三层：
// 1. `/` 下挂主业务壳层（计划 + 社交），需要登录；
// 2. `/dashboard` 下挂数据大屏，允许未登录直接查看；
// 3. `/login`、`/register` 为独立认证页面。
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      component: AppShell,
      meta: { requiresAuth: true },
      redirect: '/dashboard/profile',
      children: [
        {
          path: 'plan',
          name: 'plan',
          meta: { requiresPortalRole: true },
          component: FitnessPlan,
        },
        {
          path: 'social',
          component: SocialShell,
          meta: { requiresPortalRole: true },
          redirect: '/social/chat',
          children: [
            { path: 'chat', name: 'social-chat', meta: { requiresPortalRole: true }, component: SocialChat },
            { path: 'match', name: 'social-match', meta: { requiresPortalRole: true }, component: SocialMatch },
            { path: 'plaza', name: 'social-plaza', meta: { requiresPortalRole: true }, component: SocialPlaza },
          ],
        },
      ],
    },
    {
      path: '/dashboard',
      component: DashboardShell,
      meta: { requiresAuth: false },
      redirect: '/dashboard/fitness',
      children: [
        { path: 'fitness', name: 'dashboard-fitness', component: DashboardFitness },
        { path: 'diet', name: 'dashboard-diet', component: DashboardDiet },
        { path: 'profile', name: 'dashboard-profile', component: DashboardProfile },
      ],
    },
    {
      path: '/login',
      name: 'login',
      component: Login
    },
    {
      path: '/register',
      name: 'register',
      component: Register
    }
  ],
})

router.beforeEach(async (to, from, next) => {
  // 门户鉴权比较轻量，只校验本地 token 是否存在；
  // 真正的 token 失效处理交给请求拦截器和后端 401 响应兜底。
  const token = localStorage.getItem('token');
  if (to.meta.requiresAuth && !token) {
    ElMessage.error('请先登录')
    return next('/login');
  }
  if (to.meta.requiresPortalRole) {
    try {
      let roleList = readRoleList()
      if (!roleList.length && token) {
        roleList = await fetchPortalRoleList(token)
      }
      if (!roleList.includes(PORTAL_ROLE)) {
        clearPortalSession()
        ElMessage.error('当前账号没有门户访问权限，请使用普通用户账号登录')
        return next('/login')
      }
    } catch (error) {
      clearPortalSession()
      ElMessage.error(error?.message || '权限校验失败，请重新登录')
      return next('/login')
    }
  }
  next();
});

export default router
