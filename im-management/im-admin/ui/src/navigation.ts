const permissionRoutes = [{ permission: 'user:read', path: '/users' }] as const

export function firstAccessibleRoute(permissions: string[]) {
  return (
    permissionRoutes.find((route) => permissions.includes(route.permission))?.path ??
    '/no-permission'
  )
}

export function loginUrl(path: string) {
  return `/iam/login?continue=${encodeURIComponent(`/#${path}`)}`
}
