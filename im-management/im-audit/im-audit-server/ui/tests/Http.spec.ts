import { beforeEach, describe, expect, it, vi } from 'vitest'
import { cookie, http, notifyHttpFailure, unwrapHttpResult } from '../src/api/http'

describe('audit HTTP boundary', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=audit%20csrf; path=/'
  })

  it('submits the same-origin CSRF token for logout', async () => {
    let csrfHeader: unknown
    http.defaults.adapter = async (config) => {
      csrfHeader = config.headers['X-XSRF-TOKEN']
      return { data: {}, status: 200, statusText: 'OK', headers: {}, config }
    }

    await http.post('/iam/logout')

    expect(decodeURIComponent(cookie('XSRF-TOKEN') ?? '')).toBe('audit csrf')
    expect(csrfHeader).toBe('audit csrf')
  })

  it('unwraps the shared HttpResult envelope', () => {
    expect(unwrapHttpResult({ code: 0, msg: 'success', data: { total: 0 } })).toEqual({ total: 0 })
    expect(() => unwrapHttpResult({ code: 10006, msg: '参数错误', data: {} })).toThrow('参数错误')
  })

  it('publishes authentication and forbidden states', () => {
    const unauthorized = vi.fn()
    const forbidden = vi.fn()
    window.addEventListener('iam:unauthorized', unauthorized, { once: true })
    window.addEventListener('audit:forbidden', forbidden, { once: true })
    notifyHttpFailure(401)
    notifyHttpFailure(403)
    expect(unauthorized).toHaveBeenCalledOnce()
    expect(forbidden).toHaveBeenCalledOnce()
  })
})
