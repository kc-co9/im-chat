import { describe, expect, it } from 'vitest'
import { firstAccessibleRoute, loginUrl, loginUrlFromHash } from '../src/navigation'

describe('audit navigation', () => {
  it('restores the requested route through IAM login', () => {
    expect(loginUrl('/audits?type=BUSINESS')).toBe(
      '/iam/login?continue=%2F%23%2Faudits%3Ftype%3DBUSINESS',
    )
  })

  it('gates navigation with audit read permission', () => {
    expect(firstAccessibleRoute(['audit:read'])).toBe('/audits')
    expect(firstAccessibleRoute([])).toBe('/no-permission')
  })

  it('preserves the current hash route after an expired application session', () => {
    expect(loginUrlFromHash('#/audits?type=SECURITY')).toBe(
      '/iam/login?continue=%2F%23%2Faudits%3Ftype%3DSECURITY',
    )
    expect(loginUrlFromHash('')).toBe('/iam/login?continue=%2F%23%2F')
  })
})
