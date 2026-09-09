import { createRouter, createWebHashHistory } from 'vue-router'
import { auth, loadSession } from './state/auth'
import { firstAccessibleRoute, loginUrl } from './navigation'

export const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', component: () => import('./views/NoPermissionView.vue') },
    { path: '/no-permission', component: () => import('./views/NoPermissionView.vue') },
    {
      path: '/audits',
      component: () => import('./views/AuditsView.vue'),
      meta: { permission: 'audit:read' },
    },
  ],
})

router.beforeEach(async (to) => {
  if (!auth.checked) await loadSession()
  if (!auth.principal) {
    window.location.assign(loginUrl(to.fullPath))
    return false
  }
  if (to.path === '/') return firstAccessibleRoute(auth.principal.authorities)
  if (to.path === '/no-permission') return true
  const permission = to.meta.permission as string | undefined
  return permission && !auth.principal.authorities.includes(permission)
    ? firstAccessibleRoute(auth.principal.authorities)
    : true
})
