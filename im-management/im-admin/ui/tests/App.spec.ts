import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import App from '../src/App.vue'
import { auth } from '../src/state/auth'

vi.mock('../src/api/admin', () => ({
  api: {
    signOut: vi.fn(),
  },
}))
vi.mock('vue-router', () => ({
  RouterLink: { template: '<a><slot /></a>' },
  RouterView: { template: '<div />' },
  useRoute: () => ({ path: '/users' }),
}))

describe('administration shell', () => {
  it('shows only Admin-owned navigation from IAM authorities', () => {
    auth.principal = {
      administratorId: '1',
      username: 'admin',
      appKey: 'imAdmin',
      authorities: ['user:read'],
    }
    const wrapper = mount(App, { global: { stubs: { ElButton: true } } })
    expect(wrapper.text()).toContain('用户管理')
    expect(wrapper.text()).not.toContain('审计日志')
    expect(wrapper.text()).not.toContain('权限管理')
  })

  it('renders locally configured remote consoles as full-page links', async () => {
    const wrapper = mount(App, { global: { stubs: { ElButton: true } } })
    await flushPromises()

    const link = wrapper.get('a[href="http://localhost:18090"]')
    expect(link.text()).toContain('身份与权限')
  })
})
