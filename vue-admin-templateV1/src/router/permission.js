import { addRoutes, router } from ".";
import { useUserTokenStore } from "@/stores/user";
import { useUserInfoStore } from "@/stores/userInfo";
import { ElMessage } from 'element-plus'
import NProgress from 'nprogress'
import "nprogress/nprogress.css"

const whiteList = ['/login','/404','/401']

// 统一去掉路径首尾 `/`，避免后续拼接和比较时出现 `//` 或空段差异。
const normalizePath = (path = '') => path.replace(/^\/+|\/+$/g, '')

const joinPath = (basePath = '', currentPath = '') => {
  const base = normalizePath(basePath)
  const current = normalizePath(currentPath)
  if (!base) return `/${current}`
  if (!current) return `/${base}`
  return `/${base}/${current}`
}

const isAccessibleMenu = (menu) => {
  if (!menu) return false
  // F 表示按钮权限，不应该被当成页面路由；
  // visible=0/status=0 代表前端允许显示且启用。
  return menu.menuType !== 'F' && menu.visible === 0 && menu.status === 0
}

const getFirstAccessiblePath = (menus = [], parentPath = '') => {
  // 根路径 `/` 不直接对应具体业务页，这里递归找出当前用户可访问的第一个菜单路径。
  for (const menu of menus) {
    if (!isAccessibleMenu(menu)) continue
    const currentPath = joinPath(parentPath, menu.path)
    // 优先往子节点继续找，保证最终落到真正可打开的页面，而不是只有目录的父节点。
    const childPath = getFirstAccessiblePath(menu.children || [], currentPath)
    if (childPath) {
      return childPath
    }
    if (menu.path) {
      return currentPath
    }
  }
  return ''
}

const flattenMenuPaths = (menus = [], parentPath = '') => {
  // 把后端返回的菜单树展开成完整路径集合，用于判断动态路由是否已经注册过。
  return menus.flatMap(menu => {
    if (!menu?.path) {
      // 某些目录节点可能没有 path，继续向下展开其子节点。
      return flattenMenuPaths(menu?.children || [], parentPath)
    }
    const currentPath = normalizePath(parentPath)
      ? `${normalizePath(parentPath)}/${normalizePath(menu.path)}`
      : normalizePath(menu.path)
    return [currentPath, ...flattenMenuPaths(menu.children || [], currentPath)]
  })
}

const hasRegisteredRoutes = (menus = []) => {
  const registeredPaths = new Set(
    router.getRoutes()
      // Vue Router 返回的 route.path 带不带 `/` 都可能存在，这里统一规整后再比较。
      .map(route => normalizePath(route.path))
      .filter(Boolean)
  )
  return flattenMenuPaths(menus).every(path => registeredPaths.has(path))
}

const hasAdminRole = (roleList = []) => roleList.includes('superadmin')

const hasSupportedAdminMenu = (menus = []) => {
  const supportedPaths = new Set(['index', 'user', 'power', 'fitness-plan'])
  return menus.some(menu => {
    if (!menu) return false
    if (supportedPaths.has(menu.path)) return true
    return hasSupportedAdminMenu(menu.children || [])
  })
}

const shouldRefreshUserInfo = (userInfoStore) => {
  // 兼容热更新、旧会话或后端权限结构升级后的场景：
  // 只要角色、菜单或权限码任一为空，就重新拉一次 getInfo，避免沿用脏缓存。
  return userInfoStore.menus.length === 0
    || userInfoStore.roleList.length === 0
    || userInfoStore.permissionList.length === 0
}

const denyAdminAccess = (message) => {
  const tokenStore = useUserTokenStore()
  const userInfoStore = useUserInfoStore()
  tokenStore.removeToken()
  userInfoStore.clearUserInfo()
  ElMessage.error(message)
  return '/login'
}

router.beforeEach( async(to,from,next)=>{
  const tokenStore = useUserTokenStore()
  const tokenValue = tokenStore.tokenValue
  const userInfoStore = useUserInfoStore()

  // 每次路由切换都显示进度条，避免首次拉权限菜单时页面“卡住无反馈”。
  NProgress.start()
  // 未登录访问非登录页时直接拦截。
  // 未登录只允许进入白名单页，其余全部重定向到登录页。
  if(!tokenValue && to.path != "/login"){
    ElMessage.error("请先登录")
    return next("/login")
  }
  // 已登录用户不再允许回到登录页。
  if(tokenValue && to.path == "/login"){
    ElMessage.error("请勿重复登录")
    return next({path:from.path ? from.path:"/"})
  }
  // 首次进入系统时 Pinia 中还没有菜单，先请求 `/auth/getInfo`，
  // 再根据后端菜单树动态注册当前用户真正可见的路由。
  if(tokenValue && shouldRefreshUserInfo(userInfoStore)){
    try {
      let {menus, roleList} = await userInfoStore.getUserInfo()
      if (!hasAdminRole(roleList)) {
        return next(denyAdminAccess('当前账号没有后台管理权限，请使用超级管理员账号登录'))
      }
      if (!hasSupportedAdminMenu(menus)) {
        return next(denyAdminAccess('当前账号未分配可用的后台菜单，请联系管理员'))
      }
      console.log("守卫路由中的mens",menus);
      // addRoutes 会把后端菜单树映射到前端预定义组件上。
      addRoutes(menus)
      if (to.path === '/') {
        // 登录后的默认落点不是 `/`，而是第一个可访问页面。
        const firstPath = getFirstAccessiblePath(menus)
        return next(firstPath || '/404')
      }
      // replace:true 防止本次“补注册路由”造成额外历史记录。
      return next({ ...to, replace: true })
    } catch (error) {
      console.error(error)
      // 获取用户信息失败，可能是token过期或无效，跳转回登录页
      useUserTokenStore().removeToken()
      userInfoStore.clearUserInfo()
      return next("/login")
    }
  }
  if (tokenValue && !hasAdminRole(userInfoStore.roleList)) {
    return next(denyAdminAccess('当前账号没有后台管理权限，请重新登录'))
  }
  if (tokenValue && userInfoStore.menus.length > 0 && !hasSupportedAdminMenu(userInfoStore.menus)) {
    return next(denyAdminAccess('当前账号未分配可用的后台菜单，请联系管理员'))
  }
  // 刷新页面后可能出现“Pinia 里有菜单，但 Router 里还没重新注册”的情况，这里做一次补注册。
  if (tokenValue && userInfoStore.menus.length > 0 && !hasRegisteredRoutes(userInfoStore.menus)) {
    // 典型场景是浏览器刷新：内存里的 router 丢了，但持久化的 Pinia 还在。
    addRoutes(userInfoStore.menus)
    if (to.path === '/') {
      const firstPath = getFirstAccessiblePath(userInfoStore.menus)
      return next(firstPath || '/404')
    }
    return next({ ...to, replace: true })
  }
  if (tokenValue && to.path === '/') {
    // 已登录访问根路径时，统一导向首个有权限的页面。
    const firstPath = getFirstAccessiblePath(userInfoStore.menus)
    return next(firstPath || '/404')
  }
  next()

})

router.afterEach((to,from) =>{
  // 隐藏loading
  NProgress.done()
})
