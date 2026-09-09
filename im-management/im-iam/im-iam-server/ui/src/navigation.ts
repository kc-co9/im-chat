const permissionRoutes = [
  { permission: 'iam:administrator:read', path: '/permissions/administrators' },
  { permission: 'iam:application:read', path: '/permissions/applications' },
  { permission: 'iam:session:read', path: '/permissions/sessions' },
] as const

export function firstAccessibleRoute(permissions: string[]) {
  return (
    permissionRoutes.find((route) => permissions.includes(route.permission))?.path ??
    '/no-permission'
  )
}

export function loginUrl(path: string) {
  return `/login?continue=${encodeURIComponent(`/#${path}`)}`
}
