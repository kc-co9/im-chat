import { createRouter, createWebHashHistory } from 'vue-router'
import { auth, loadPrincipal } from './state/auth'
import { firstAccessibleRoute, loginUrl } from './navigation'

export function legacyApplicationAccessRedirect(query: Record<string, unknown>) {
  const rawAppId = query.appId
  const appId = Array.isArray(rawAppId) ? rawAppId[0] : rawAppId
  if (typeof appId !== 'string' || !appId) return { path: '/permissions/applications' }
  return {
    name: 'application-detail',
    params: { appId },
    query: { tab: 'roles' },
  }
}

export const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', component: () => import('./views/NoPermissionView.vue') },
    { path: '/no-permission', component: () => import('./views/NoPermissionView.vue') },
    {
      path: '/permissions/administrators',
      component: () => import('./views/AdministratorsView.vue'),
      meta: { permission: 'iam:administrator:read' },
    },
    {
      path: '/permissions/applications',
      component: () => import('./views/ApplicationsView.vue'),
      meta: { permission: 'iam:application:read' },
    },
    {
      path: '/permissions/applications/access',
      redirect: (to) => legacyApplicationAccessRedirect(to.query),
    },
    {
      path: '/permissions/applications/:appId',
      name: 'application-detail',
      component: () => import('./views/ApplicationDetailView.vue'),
      meta: { permission: 'iam:application:read' },
    },
    {
      path: '/permissions/sessions',
      component: () => import('./views/SessionsView.vue'),
      meta: { permission: 'iam:session:read' },
    },
  ],
})

router.beforeEach(async (to) => {
  if (!auth.checked) await loadPrincipal()
  if (!auth.principal) {
    window.location.assign(loginUrl(to.fullPath))
    return false
  }
  if (to.path === '/') {
    return firstAccessibleRoute(auth.principal.authorities)
  }
  if (to.path === '/no-permission') return true
  const permission = to.meta.permission as string | undefined
  return permission && !auth.principal.authorities.includes(permission)
    ? firstAccessibleRoute(auth.principal.authorities)
    : true
})
