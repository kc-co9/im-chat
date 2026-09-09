import { describe, expect, it } from 'vitest'
import { firstAccessibleRoute, loginUrl } from '../src/navigation'

describe('IAM permission navigation', () => {
  it('chooses the first route visible to the current administrator', () => {
    expect(firstAccessibleRoute(['iam:application:read'])).toBe('/permissions/applications')
    expect(firstAccessibleRoute([])).toBe('/no-permission')
  })

  it('preserves the requested hash route across administrator login', () => {
    expect(loginUrl('/permissions/administrators')).toBe(
      '/login?continue=%2F%23%2Fpermissions%2Fadministrators',
    )
  })
})
