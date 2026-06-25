import { storeToRefs } from 'pinia'
import { useUserInfoStore } from '@/stores/userInfo'
import { hasAllPermissions, hasAnyPermission } from '@/utils/permission'

export const usePermission = () => {
  const userInfoStore = useUserInfoStore()
  const { permissionList } = storeToRefs(userInfoStore)

  const hasPerm = (permission) => hasAllPermissions(permissionList.value, permission)
  const hasAnyPerm = (permissions) => hasAnyPermission(permissionList.value, permissions)

  return {
    hasPerm,
    hasAnyPerm,
    permissionList,
  }
}
