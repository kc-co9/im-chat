export function firstAccessibleRoute(permissions: string[]) {
  return permissions.includes('audit:read') ? '/audits' : '/no-permission'
}

export function loginUrl(path: string) {
  return `/iam/login?continue=${encodeURIComponent(`/#${path}`)}`
}

export function loginUrlFromHash(hash: string) {
  const path = hash.startsWith('#/') ? hash.substring(1) : '/'
  return loginUrl(path)
}
