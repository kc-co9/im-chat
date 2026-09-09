import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import App from '../src/App.vue'
import { auth } from '../src/state/auth'

vi.mock('../src/api/audit', () => ({
  api: {
    signOut: vi.fn(),
  },
}))
vi.mock('vue-router', () => ({
  RouterLink: { template: '<a><slot /></a>' },
  RouterView: { template: '<div />' },
}))

describe('audit shell', () => {
  it('keeps local audit navigation and renders configured remote consoles', async () => {
    auth.principal = {
      administratorId: '1',
      username: 'auditor',
      appKey: 'imAudit',
      authorities: ['audit:read'],
    }
    const wrapper = mount(App)
    await flushPromises()

    expect(wrapper.text()).toContain('审计记录')
    expect(wrapper.get('a[href="http://localhost:18093"]').text()).toContain('用户管理')
  })
})
