import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getInfo} from '@/api/auth'
import { ElMessage } from 'element-plus'
import { collectPermissionCodes, normalizePermissionInput } from '@/utils/permission'

export const useUserInfoStore = defineStore('userInfo', () => {
  // 管理端的菜单、角色和头像昵称都依赖 `/auth/getInfo` 一次性返回。
  const menus = ref([])
  const roleList = ref([])
  const permissionList = ref([])
  // 顶栏当前只展示头像和昵称，故这里只保留最小字段集。
  const user = ref({
    avatar: '',
    nickName: ''
  })
  
  // 后端菜单树在不同角色下返回顺序可能不稳定，这里统一按 orderNum 递归排序。
  function sortMenus(menus) {
    if (!menus) return []
    return menus.sort((a, b) => (a.orderNum || 0) - (b.orderNum || 0)).map(item => {
      if (item.children && item.children.length > 0) {
        // 子菜单同样递归排序，保证侧边栏展开顺序稳定。
        item.children = sortMenus(item.children)
      }
      return item
    })
  }

  function setUserInfo(data) {
    // 把后端返回菜单先排序再落库，避免每次刷新菜单顺序跳动。
    menus.value = sortMenus(data.menus)
    // 角色列表后续可用于按钮级权限判断。
    roleList.value = data.roleList
    // 优先使用后端返回的完整权限码列表；老接口没有该字段时再回退到菜单树递归提取。
    if (Array.isArray(data.permissionList)) {
      permissionList.value = [...new Set(data.permissionList.flatMap(item => normalizePermissionInput(item)))]
    } else {
      permissionList.value = collectPermissionCodes(menus.value)
    }
    // 用户信息按字段覆写，保持 user 引用不变，减少依赖该对象的组件抖动。
    user.value.avatar = data.user.avatar
    user.value.nickName = data.user.nickName
  }

  // 路由守卫会调用该方法，拿到菜单后再动态注入可访问路由。
  const getUserInfo = async () => {
    const res = await getInfo()
    // 这里约定 code=200 为成功，其它都按失败处理并中断后续路由流程。
    if(res.code != 200){
      ElMessage.error(res.msg)
      return Promise.reject(res.msg)
    }else{
      // 返回结构拆开是为了让守卫直接拿 menus 做 addRoutes。
      const { menus, roleList,user } = res.data
      setUserInfo(res.data)
      return { menus, roleList,user }
    }
}

  function clearUserInfo() {
    // 退出登录或 token 失效时，把用户可见数据全部清空，避免脏数据残留到下个账号。
    menus.value = []
    roleList.value = []
    permissionList.value = []
    user.value.avatar = ''
    user.value.nickName = ''
  }


  return {
    menus,
    roleList,
    permissionList,
    user,
    setUserInfo,
    clearUserInfo,
    getUserInfo
  }
})
