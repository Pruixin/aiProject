import { h } from 'vue'
import { createRouter, createWebHistory, RouterView } from 'vue-router'
import { ElMessage } from 'element-plus'
import Layout from '@/pages/layout/layout.vue'
import User from '@/pages/system/user/index.vue'
import FitnessPlan from '@/pages/system/fitnessPlan/index.vue'
import Role from '@/pages/system/role/index.vue'
import Menu from '@/pages/system/menu/index.vue'

const ParentView = {
  name: 'ParentView',
  // 目录型节点本身没有实际页面时，用 RouterView 作为中间承载层。
  render: () => h(RouterView),
}

/* 
  1. 静态路由配置 
*/
export const constantRoutes = [
  {
    path: '/login',
    name: 'Login',
    // 登录页始终静态可访问，不参与权限动态裁剪。
    component: () => import('@/pages/system/auth/login.vue'),
    meta: { title: '登录', hidden: true }
  },
  {
    path: '/404',
    name: 'StaticNotFound',
    component: () => import('@/pages/404.vue'),
    meta: { title: '404', hidden: true }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/pages/system/auth/register.vue'),
    meta: { title: '注册', hidden: true }
  },
  {
    path: '/',
    name: 'Layout',
    // Layout 是所有后台动态业务路由的挂载父节点。
    component: Layout,
    meta: { title: '首页' }
  }
]

// 动态路由模板：需要权限校验
export const asyncRoutes=[
  {
        path: 'index',
        name: 'index',
        // 首页模板是否真正可见，最终取决于后端是否把该菜单返回给当前用户。
        component: () => import('@/pages/system/index.vue'),
        meta: { title: '首页', 
                icon: 'el-icon-s-home',
                perms:['system:index:select'],
                menu_type: 'C',
                visible:1
              }
      },
  {
    path: 'user',
    name: 'user',
    component:User,
    // 这里的 meta.perms 主要用于和后端菜单 perms 做语义对齐。
    meta: { title: '用户管理',
            icon:'el-icon-user',
            perms:['system:user:list'],
            menu_type: 'C',
            visible:0
          },
  },
  {
    path: 'fitness-plan',
    name: 'FitnessPlan',
    component: FitnessPlan,
    // 健身方案管理是后台新增的业务页，这里预先放入模板池，等待菜单树命中。
    meta: {
      title: '健身方案管理',
      icon: 'el-icon-document',
      perms: ['system:fitness-plan:list'],
      menu_type: 'C',
      visible: 0
    }
  },
  {
    path: 'power',
    name: 'Power',
    component: ParentView,
    // 权限管理自身更像“目录”，默认跳到其第一个子页菜单。
    redirect: '/power/menu',
    meta: { 
      title: '权限管理', 
      icon: 'el-icon-lock', 
      perms: ['system:power:list'],
      menu_type: 'C',
      visible: 0
    },
    children: [
      // 2.1 角色管理（二级菜单）
      {
        path: 'role',
        name: 'Role',
        component: Role,
        // 角色管理是真正渲染页面的二级菜单。
        meta: { 
          title: '角色管理', 
          perms: ['system:role:list'],
          menu_type: 'C',
          visible: 0
        },
        // children: [
        //   // 角色列表（菜单）
        //   {
        //     path: 'index',
        //     name: 'RoleIndex',
        //     component: Role,
        //     meta: { 
        //       title: '角色列表', 
        //       perms: ['system:role:list'],
        //       menu_type: 'C',
        //       visible: 0
        //     }
        //   },
        //   // 新增角色（按钮级）
        //   // {
        //   //   path: 'add',
        //   //   name: 'RoleAdd',
        //   //   component: Role,
        //   //   meta: { 
        //   //     title: '新增角色', 
        //   //     perms: ['system:role:add'],
        //   //     menu_type: 'F',
        //   //     hidden: true,
        //   //     visible: 0
        //   //   }
        //   // },
        //   // // 修改角色（按钮级）
        //   // {
        //   //   path: 'update/:id',
        //   //   name: 'RoleUpdate',
        //   //   component: Role,
        //   //   meta: { 
        //   //     title: '修改角色', 
        //   //     perms: ['system:role:update'],
        //   //     menu_type: 'F',
        //   //     hidden: true,
        //   //     visible: 0
        //   //   }
        //   // },
        //   // // 删除角色（按钮级）
        //   // {
        //   //   path: 'delete/:id',
        //   //   name: 'RoleDelete',
        //   //   component: Role,
        //   //   meta: { 
        //   //     title: '删除角色', 
        //   //     perms: ['system:role:delete'],
        //   //     menu_type: 'F',
        //   //     hidden: true,
        //   //     visible: 0
        //   //   }
        //   // },
        //   // // 回显角色（按钮级）
        //   // {
        //   //   path: 'select/:id',
        //   //   name: 'RoleSelect',
        //   //   component: Role,
        //   //   meta: { 
        //   //     title: '回显角色', 
        //   //     perms: ['system:role:select'],
        //   //     menu_type: 'F',
        //   //     hidden: true,
        //   //     visible: 0
        //   //   }
        //   // }
        // ]
      },

      // 2.2 菜单管理（二级菜单）
      {
        path: 'menu',
        name: 'Menu',
        component: Menu,
        // 菜单管理与角色管理同属权限目录下的子页面。
        meta: { 
          title: '菜单管理', 
          perms: ['system:menu:list'],
          menu_type: 'C',
          visible: 0
        },
        // children: [
        //   // 菜单列表（菜单）
        //   {
        //     path: 'index',
        //     name: 'MenuIndex',
        //     component: Menu,
        //     meta: { 
        //       title: '菜单列表', 
        //       perms: ['system:menu:list'],
        //       menu_type: 'C',
        //       visible: 0
        //     }
        //   },
        //   // 新增菜单（按钮级）
        //   // {
        //   //   path: 'add',
        //   //   name: 'MenuAdd',
        //   //   component: Menu,
        //   //   meta: { 
        //   //     title: '新增菜单', 
        //   //     perms: ['system:menu:add'],
        //   //     menu_type: 'F',
        //   //     hidden: true,
        //   //     visible: 0
        //   //   }
        //   // },
        //   // // 修改菜单（按钮级）
        //   // {
        //   //   path: 'update/:id',
        //   //   name: 'MenuUpdate',
        //   //   component: Menu,
        //   //   meta: { 
        //   //     title: '修改菜单', 
        //   //     perms: ['system:menu:update'],
        //   //     menu_type: 'F',
        //   //     hidden: true,
        //   //     visible: 0
        //   //   }
        //   // },
        //   // // 删除菜单（按钮级）
        //   // {
        //   //   path: 'delete/:id',
        //   //   name: 'MenuDelete',
        //   //   component:Menu,
        //   //   meta: { 
        //   //     title: '删除菜单', 
        //   //     perms: ['system:menu:delete'],
        //   //     menu_type: 'F',
        //   //     hidden: true,
        //   //     visible: 0
        //   //   }
        //   // },
        //   // // 回显菜单（按钮级）
        //   // {
        //   //   path: 'select/:id',
        //   //   name: 'MenuSelect',
        //   //   component: Menu,
        //   //   meta: { 
        //   //     title: '回显菜单', 
        //   //     perms: ['system:menu:select'],
        //   //     menu_type: 'F',
        //   //     hidden: true,
        //   //     visible: 0
        //   //   }
        //   // }
        // ]
      }
    ]
  }

]

/* 
   创建路由实例 
*/
export const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: constantRoutes,
})

const cloneRouteRecord = (route) => {
  // 动态注册时不直接复用模板对象，避免多次 addRoutes 时相互污染 children/meta。
  const record = {
    ...route,
    meta: route.meta ? { ...route.meta } : undefined,
  }
  if (route.children?.length) {
    // 子路由也递归克隆，保证整棵路由树都是独立副本。
    record.children = route.children.map(cloneRouteRecord)
  } else {
    delete record.children
  }
  return record
}

const buildRouteByMenu = (menu, routeTemplates = asyncRoutes) => {
  for (const template of routeTemplates) {
    if (template.path === menu.path) {
      // 当前后端菜单 path 与某个模板 path 命中时，就把该模板实例化成真实路由。
      const route = cloneRouteRecord(template)
      if (template.children?.length) {
        // 目录类菜单需要继续根据后端 children 裁剪其可访问子路由。
        const allowedChildren = (menu.children || [])
          .map(child => buildRouteByMenu(child, template.children))
          .filter(Boolean)
        if (allowedChildren.length) {
          // 只有命中的子菜单才会保留下来。
          route.children = allowedChildren
        } else {
          // 如果目录下没有任何可访问子页，就删除 children，避免生成空目录路由。
          delete route.children
        }
      }
      return route
    }

    if (template.children?.length) {
      // 如果当前层没命中，就继续去模板子树里深度查找。
      const nestedRoute = buildRouteByMenu(menu, template.children)
      if (nestedRoute) {
        return nestedRoute
      }
    }
  }
  return null
}

// 动态添加路由方法
export function addRoutes(menus){
  // 后端返回的是“当前用户拥有的菜单树”，这里把它映射成真正的前端 RouteRecord。
  const routesToAdd = (menus || [])
    .map(menu => buildRouteByMenu(menu))
    .filter(Boolean)

  routesToAdd.forEach(route => {
    if (router.hasRoute(route.name)) {
      // 重复注册前先移除旧同名路由，适配刷新和重新登录不同角色场景。
      router.removeRoute(route.name)
    }
    // 所有业务动态路由统一挂在 Layout 下面。
    router.addRoute('Layout', route)
  })

  if (!router.hasRoute('NotFound')) {
    // 最后再补一个兜底 404，避免未命中的动态路径直接白屏。
    router.addRoute({
      path: '/:pathMatch(.*)*',
      name: 'NotFound',
      component: () => import('@/pages/404.vue'),
      meta: { title: '404', hidden: true }
    })
  }

  console.log("动态路由添加的路由",router.getRoutes());
  
}
