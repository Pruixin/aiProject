export const normalizePermissionInput = (permission) => {
  if (Array.isArray(permission)) {
    return permission.map(item => String(item || '').trim()).filter(Boolean)
  }
  const single = String(permission || '').trim()
  return single ? [single] : []
}

export const collectPermissionCodes = (menus = []) => {
  return [...new Set(
    (menus || []).flatMap(menu => {
      if (!menu) return []
      const currentPerms = normalizePermissionInput(menu.perms)
      return [...currentPerms, ...collectPermissionCodes(menu.children || [])]
    })
  )]
}

export const hasAllPermissions = (ownedPermissions = [], requiredPermissions = []) => {
  const ownedSet = new Set(normalizePermissionInput(ownedPermissions))
  const requiredList = normalizePermissionInput(requiredPermissions)
  if (!requiredList.length) return true
  return requiredList.every(permission => ownedSet.has(permission))
}

export const hasAnyPermission = (ownedPermissions = [], requiredPermissions = []) => {
  const ownedSet = new Set(normalizePermissionInput(ownedPermissions))
  const requiredList = normalizePermissionInput(requiredPermissions)
  if (!requiredList.length) return true
  return requiredList.some(permission => ownedSet.has(permission))
}
