import { beforeEach, describe, expect, it, vi } from 'vitest'
import { cookie, notifyHttpFailure, unwrapHttpResult } from '../src/api/http'

describe('admin HTTP security boundary', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=csrf%20value; path=/'
  })

  it('reads the non-sensitive CSRF delivery cookie', () => {
    expect(decodeURIComponent(cookie('XSRF-TOKEN') ?? '')).toBe('csrf value')
  })

  it('publishes authentication and authorization failures', () => {
    const unauthorized = vi.fn()
    const forbidden = vi.fn()
    window.addEventListener('iam:unauthorized', unauthorized, { once: true })
    window.addEventListener('admin:forbidden', forbidden, { once: true })

    notifyHttpFailure(10001)
    notifyHttpFailure(10002)

    expect(unauthorized).toHaveBeenCalledOnce()
    expect(forbidden).toHaveBeenCalledOnce()
  })

  it('unwraps successful results and rejects business failures', () => {
    expect(unwrapHttpResult({ code: 0, msg: 'success', data: { id: 1 } })).toEqual({ id: 1 })
    expect(() => unwrapHttpResult({ code: 10006, msg: '参数错误', data: {} })).toThrow('参数错误')
  })
})
