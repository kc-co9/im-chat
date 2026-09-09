import { describe, expect, it } from 'vitest'
import { legacyApplicationAccessRedirect, router } from '../src/router'

describe('IAM routes', () => {
  it('uses hash history and scopes roles to an application', () => {
    expect(router.options.history.createHref('/')).toContain('#')
    expect(router.hasRoute('application-detail')).toBe(true)
    expect(router.getRoutes().some((route) => route.path === '/permissions/roles')).toBe(false)
    expect(router.getRoutes().some((route) => route.path === '/login')).toBe(false)
  })

  it('preserves the old application access intent on the roles tab', () => {
    expect(
      legacyApplicationAccessRedirect({ appId: '90071992547409930', appKey: 'stale-key' }),
    ).toEqual({
      name: 'application-detail',
      params: { appId: '90071992547409930' },
      query: { tab: 'roles' },
    })
    expect(legacyApplicationAccessRedirect({ appKey: 'stale-key' })).toEqual({
      path: '/permissions/applications',
    })
  })
})
