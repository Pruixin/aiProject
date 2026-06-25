## 1. 架构设计

```mermaid
graph TD
    A[用户浏览器] --> B[Vue3前端应用]
    B --> C[Element Plus UI组件]
    B --> D[Vue Router动态路由]
    B --> E[Pinia状态管理]
    B --> F[Axios HTTP客户端]
    F --> G[后端API服务]
    G --> H[权限验证中间件]
    G --> I[菜单管理服务]
    G --> J[用户管理服务]
    G --> K[角色权限服务]
    
    subgraph "前端层"
        B
        C
        D
        E
        F
    end
    
    subgraph "服务层"
        G
        H
        I
        J
        K
    end
```

## 2. 技术描述

* **前端框架**：Vue3\@3.3 + JavaScript + Composition API

* **UI组件库**：Element Plus\@2.4

* **路由管理**：Vue Router\@4.2（支持动态路由生成）

* **状态管理**：Pinia\@2.1（核心状态管理）

* **HTTP客户端**：Axios\@1.5

* **构建工具**：Vite\@4.4

* **初始化工具**：vite-init

* **CSS预处理器**：Sass\@1.66

* **图标库**：Element Plus Icons

* **后端服务**：RESTful API（提供动态菜单和权限数据）

## 3. 路由定义

| 路由路径         | 页面组件           | 权限要求             | 描述    |
| ------------ | -------------- | ---------------- | ----- |
| /login       | Login.vue      | 无                | 登录页面  |
| /            | Layout.vue     | 需要登录             | 主布局页面 |
| /dashboard   | Dashboard.vue  | dashboard:read   | 系统首页  |
| /system/user | SystemUser.vue | system:user:read | 用户管理  |
| /system/role | SystemRole.vue | system:role:read | 角色管理  |
| /system/menu | SystemMenu.vue | system:menu:read | 菜单管理  |
| /profile     | Profile.vue    | profile:read     | 个人中心  |
| /403         | Error403.vue   | 无                | 无权限页面 |
| /404         | Error404.vue   | 无                | 页面不存在 |

## 4. API接口定义

### 4.1 认证相关API

**用户登录**

```
POST /auth/login
```

请求参数：

| 参数名      | 类型     | 必填 | 描述     |
| -------- | ------ | -- | ------ |
| username | string | 是  | 用户名    |
| password | string | 是  | 密码（明文） |
| captcha  | string | 是  | 验证码    |

响应数据：

```json
{
    "code": 200,
    "msg": "操作成功",
    "data": {
        "tokenName": "satoken",
        "tokenValue": "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJsb2dpblR5cGUiOiJsb2dpbiIsImxvZ2luSWQiOjEsInJuU3RyIjoic2pGT2FDV2ZpeHZpS2NMbW44RjNoWkczeVhDSVVxVU8ifQ.mIligP4eV4YIE8qpRFCEUwRCMlAedwVPYvA3-jhNywM",
        "isLogin": true,
        "loginId": "1",
        "loginType": "login",
        "tokenTimeout": 2592000,
        "sessionTimeout": 2592000,
        "tokenSessionTimeout": -2,
        "tokenActiveTimeout": -1,
        "loginDeviceType": "DEF",
        "tag": null
    },
    "map": {}
}
```

**获取用户信息**

```
GET /auth/getInfo
```

<br />

<br />

响应数据：

```json
{
    "code": 200,
    "msg": "操作成功",
    "data": {
        "menus": [
            {
                "id": "2",
                "parentId": 0,
                "menuType": "C",
                "orderNum": 0,
                "path": "user",
                "name": "用户管理",
                "component": "pages/system/user/index",
                "visible": 0,
                "status": 0,
                "perms": "system:user:list",
                "icon": "",
                "children": [],
                "createBy": null,
                "createTime": null,
                "updateBy": null,
                "updateTime": null,
                "deleted": 0,
                "remark": null
            },
            {
                "id": "3",
                "parentId": 0,
                "menuType": "M",
                "orderNum": 0,
                "path": "power",
                "name": "权限管理",
                "component": "",
                "visible": 0,
                "status": 0,
                "perms": "",
                "icon": "",
                "children": [
                    {
                        "id": "5",
                        "parentId": 3,
                        "menuType": "C",
                        "orderNum": 0,
                        "path": "role",
                        "name": "角色管理",
                        "component": "pages/system/role/index",
                        "visible": 0,
                        "status": 0,
                        "perms": "system:role:list",
                        "icon": "",
                        "children": [],
                        "createBy": null,
                        "createTime": null,
                        "updateBy": null,
                        "updateTime": null,
                        "deleted": 0,
                        "remark": null
                    },
                    {
                        "id": "4",
                        "parentId": 3,
                        "menuType": "C",
                        "orderNum": 0,
                        "path": "menu",
                        "name": "菜单管理",
                        "component": "pages/system/menu/index",
                        "visible": 0,
                        "status": 0,
                        "perms": "system:menu:list",
                        "icon": "",
                        "children": [],
                        "createBy": null,
                        "createTime": null,
                        "updateBy": null,
                        "updateTime": null,
                        "deleted": 0,
                        "remark": null
                    }
                ],
                "createBy": null,
                "createTime": null,
                "updateBy": null,
                "updateTime": null,
                "deleted": 0,
                "remark": null
            },
            {
                "id": "18",
                "parentId": 0,
                "menuType": "C",
                "orderNum": 0,
                "path": "index",
                "name": "首页",
                "component": "pages/system/index",
                "visible": 0,
                "status": 0,
                "perms": "system:index:select",
                "icon": "",
                "children": [],
                "createBy": null,
                "createTime": null,
                "updateBy": null,
                "updateTime": null,
                "deleted": 0,
                "remark": null
            }
        ],
        "roleList": [
            "superadmin"
        ],
        "user": {
            "id": null,
            "phone": "15033065212",
            "password": null,
            "avatar": null,
            "enable": 0,
            "sex": 0,
            "nickName": "庞一",
            "createBy": null,
            "updateBy": null,
            "createTime": null,
            "updateTime": null
        }
    },
    "map": {}
}
```

### 4.2 菜单管理API

**获取用户菜单**

```
GET /api/menu/user-menus
```

响应数据：

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "name": "系统管理",
      "path": "/system",
      "component": "Layout",
      "icon": "Setting",
      "sort": 1,
      "children": [
        {
          "id": 2,
          "name": "用户管理",
          "path": "user",
          "component": "system/user/index",
          "icon": "User",
          "permission": "system:user:read"
        }
      ]
    }
  ]
}
```

### 4.3 用户管理API

**获取用户列表**

```
GET /api/system/user/list
```

请求参数：

| 参数名      | 类型     | 必填 | 描述        |
| -------- | ------ | -- | --------- |
| page     | number | 否  | 页码，默认1    |
| pageSize | number | 否  | 每页条数，默认10 |
| keyword  | string | 否  | 搜索关键词     |
| status   | string | 否  | 用户状态      |

响应数据：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "total": 100,
    "list": [
      {
        "id": 1,
        "username": "admin",
        "nickname": "管理员",
        "email": "admin@example.com",
        "phone": "13800138000",
        "status": 1,
        "createTime": "2024-01-01 00:00:00"
      }
    ]
  }
}
```

### 4.4 角色管理API

**获取角色列表**

```
GET /api/system/role/list
```

**获取角色权限**

```
GET /api/system/role/{id}/permissions
```

**更新角色权限**

```
PUT /api/system/role/{id}/permissions
```

请求数据：

```json
{
  "permissions": ["system:user:read", "system:role:read"]
}
```

## 5. 前端架构设计

```mermaid
graph TD
    A[main.js] --> B[App.vue]
    B --> C[Router]
    B --> D[Pinia Store]
    
    C --> E[路由守卫]
    C --> F[动态路由]
    
    D --> G[用户模块]
    D --> H[权限模块]
    D --> I[菜单模块]
    D --> J[应用配置]
    
    E --> K[登录验证]
    E --> L[权限验证]
    
    F --> M[菜单转换]
    F --> N[路由注册]
    
    G --> O[用户信息]
    G --> P[登录状态]
    
    H --> Q[权限列表]
    H --> R[角色信息]
    
    I --> S[菜单树]
    I --> T[路由映射]
```

## 6. 数据模型

### 6.1 用户模型（JavaScript对象定义）

```javascript
// 用户对象结构
const userSchema = {
  id: Number,        // 用户ID
  username: String,   // 用户名
  nickname: String,   // 昵称
  email: String,      // 邮箱
  phone: String,      // 手机号
  avatar: String,     // 头像URL
  status: Number,     // 0:禁用, 1:启用
  createTime: String, // 创建时间
  updateTime: String  // 更新时间
}
```

### 6.2 角色模型（JavaScript对象定义）

```javascript
// 角色对象结构
const roleSchema = {
  id: Number,         // 角色ID
  name: String,       // 角色名称
  code: String,       // 角色编码
  description: String,// 角色描述
  status: Number,    // 0:禁用, 1:启用
  createTime: String, // 创建时间
  updateTime: String  // 更新时间
}
```

### 6.3 菜单模型（JavaScript对象定义）

```javascript
// 菜单对象结构（支持动态路由生成）
const menuSchema = {
  id: Number,         // 菜单ID
  name: String,       // 菜单名称
  path: String,       // 路由路径
  component: String,    // 组件路径（用于动态导入）
  icon: String,       // 图标名称
  sort: Number,       // 排序号
  parentId: Number,   // 父菜单ID（null为顶级菜单）
  permission: String, // 权限标识（如：system:user:read）
  type: String,       // 'MENU' | 'BUTTON'
  children: Array,    // 子菜单数组
  visible: Number,    // 是否显示（0:显示, 1:隐藏）
  status: Number      // 0:禁用, 1:启用
}
```

### 6.4 权限模型（JavaScript对象定义）

```javascript
// 权限对象结构
const permissionSchema = {
  id: Number,         // 权限ID
  menuId: Number,     // 关联菜单ID
  operation: String,  // 操作类型（read, write, delete, export）
  permission: String  // 权限标识（如：system:user:read）
}
```

## 7. 核心功能实现

### 7.1 动态路由实现（JavaScript版）

```javascript
// 核心：后端菜单数据转换为前端路由配置
function transformMenuToRoute(menu) {
  const route = {
    path: menu.path,
    name: menu.name,
    // 动态组件导入（关键特性）
    component: () => import(`@/pages/${menu.component}.vue`),
    meta: {
      title: menu.name,
      icon: menu.icon,
      permission: menu.permission,
      id: menu.id
    }
  }
  
  // 递归处理子菜单
  if (menu.children && menu.children.length > 0) {
    route.children = menu.children.map(child => transformMenuToRoute(child))
  }
  
  return route
}

// Pinia状态管理中的动态路由处理
const usePermissionStore = defineStore('permission', {
  state: () => ({
    routes: [],
    addRoutes: []
  }),
  
  actions: {
    // 生成动态路由（核心方法）
    async generateRoutes(menus) {
      const accessedRoutes = menus.map(menu => transformMenuToRoute(menu))
      this.addRoutes = accessedRoutes
      return accessedRoutes
    },
    
    // 添加路由到路由器
    async addRoutesToRouter(routes) {
      routes.forEach(route => {
        router.addRoute('layout', route)
      })
    }
  }
})
```

### 7.2 权限验证实现（JavaScript + Pinia版）

```javascript
// 路由守卫（权限控制核心）
router.beforeEach(async (to, from, next) => {
  const userStore = useUserStore()
  const permissionStore = usePermissionStore()
  
  // 白名单路由（无需登录）
  const whiteList = ['/login', '/404', '/403']
  
  // 验证登录状态
  if (!userStore.token) {
    if (whiteList.includes(to.path)) {
      return next()
    }
    return next('/login')
  }
  
  // 已登录但尚未获取用户信息
  if (!userStore.userInfo.id) {
    try {
      // 获取用户信息和菜单数据（关键步骤）
      const { menus, user, permissions } = await userStore.getUserInfo()
      
      // 生成动态路由
      const accessRoutes = await permissionStore.generateRoutes(menus)
      await permissionStore.addRoutesToRouter(accessRoutes)
      
      // 保存权限数据到Pinia
      userStore.setPermissions(permissions)
      userStore.setUserInfo(user)
      
      // 重新跳转（确保动态路由已添加）
      return next({ ...to, replace: true })
    } catch (error) {
      // 获取失败则重置登录状态
      await userStore.resetToken()
      return next('/login')
    }
  }
  
  // 验证页面权限
  if (to.meta.permission) {
    const hasPermission = userStore.permissions.includes(to.meta.permission)
    if (!hasPermission) {
      return next('/403')
    }
  }
  
  next()
})

// Pinia用户状态管理
const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    userInfo: {},
    permissions: [],
    menus: []
  }),
  
  actions: {
    // 获取用户信息（包含菜单和权限）
    async getUserInfo() {
      const response = await request.get('/auth/getInfo')
      const { menus, user, roleList } = response.data
      
      // 从角色列表生成权限列表
      const permissions = this.extractPermissionsFromMenus(menus)
      
      return { menus, user, permissions }
    },
    
    // 从菜单数据提取权限
    extractPermissionsFromMenus(menus) {
      const permissions = []
      const traverse = (menuList) => {
        menuList.forEach(menu => {
          if (menu.perms) {
            permissions.push(menu.perms)
          }
          if (menu.children && menu.children.length > 0) {
            traverse(menu.children)
          }
        })
      }
      traverse(menus)
      return permissions
    }
  }
})
```

### 7.3 菜单树形结构渲染（JavaScript + Pinia响应式）

```javascript
// 侧边栏菜单组件（响应式数据）
import { computed } from 'vue'
import { usePermissionStore } from '@/stores/permission'

export default {
  setup() {
    const permissionStore = usePermissionStore()
    
    // 计算属性：从Pinia获取菜单数据（响应式）
    const menuList = computed(() => permissionStore.menus)
    
    // 递归渲染菜单函数
    function renderMenu(menus) {
      return menus.map(menu => {
        // 处理有子菜单的情况
        if (menu.children && menu.children.length > 0) {
          return (
            <el-sub-menu index={menu.path} key={menu.id}>
              <template #title>
                {menu.icon && <el-icon><{menu.icon} /></el-icon>}
                <span>{menu.name}</span>
              </template>
              {renderMenu(menu.children)}
            </el-sub-menu>
          )
        }
        
        // 处理无子菜单的情况
        return (
          <el-menu-item index={menu.path} key={menu.id}>
            {menu.icon && <el-icon><{menu.icon} /></el-icon>}
            <span>{menu.name}</span>
          </el-menu-item>
        )
      })
    }
    
    return {
      menuList,
      renderMenu
    }
  }
}

// Pinia权限状态管理
const usePermissionStore = defineStore('permission', {
  state: () => ({
    menus: [],        // 菜单数据（驱动侧边栏）
    routes: [],       // 路由配置
    addRoutes: []     // 动态添加的路由
  }),
  
  getters: {
    // 获取扁平化菜单（用于权限判断）
    flatMenus: (state) => {
      const flatten = (menus, result = []) => {
        menus.forEach(menu => {
          result.push(menu)
          if (menu.children && menu.children.length > 0) {
            flatten(menu.children, result)
          }
        })
        return result
      }
      return flatten(state.menus)
    }
  },
  
  actions: {
    // 设置菜单数据（触发界面更新）
    setMenus(menus) {
      this.menus = menus
    }
  }
})
```

