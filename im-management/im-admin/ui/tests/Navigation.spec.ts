import { describe, expect, it } from 'vitest'
import { firstAccessibleRoute, loginUrl } from '../src/navigation'

describe('permission navigation', () => {
  it('selects the first route granted by current permissions', () => {
    expect(firstAccessibleRoute(['audit:read'])).toBe('/no-permission')
    expect(firstAccessibleRoute(['user:read'])).toBe('/users')
  })

  it('uses an explicit no-permission route when no menu is accessible', () => {
    expect(firstAccessibleRoute([])).toBe('/no-permission')
  })

  it('restores the requested hash route after IAM login', () => {
    expect(loginUrl('/users?pageNo=2')).toBe('/iam/login?continue=%2F%23%2Fusers%3FpageNo%3D2')
  })
})
