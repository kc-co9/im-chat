import { flushPromises, mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import App from '../src/App.vue'
import { auth } from '../src/state/auth'

vi.mock('../src/api/iam', () => ({
  iamApi: {
    signOut: vi.fn(),
  },
}))

vi.mock('vue-router', () => ({
  RouterLink: { template: '<a><slot /></a>' },
  RouterView: { template: '<div />' },
  useRoute: () => ({ path: '/' }),
}))

describe('IAM navigation', () => {
  it('keeps application roles inside the application workspace', () => {
    auth.principal = {
      administratorId: '1',
      authorities: ['iam:administrator:read', 'iam:application:read', 'iam:session:read'],
    }
    const wrapper = mount(App)

    expect(wrapper.text()).toContain('权限管理')
    expect(wrapper.text()).toContain('管理账号')

    expect(wrapper.text()).toContain('应用管理')
    expect(wrapper.text()).toContain('在线会话')
    expect(wrapper.text()).toContain('管理员 #1')
    expect(wrapper.find('[data-testid="sign-out"]').exists()).toBe(true)

    expect(wrapper.text()).not.toContain('角色配置')
    expect(wrapper.text()).not.toContain('安全审计')
  })

  it('shows configured remote consoles outside local permission navigation', async () => {
    const wrapper = mount(App)
    await flushPromises()

    expect(wrapper.get('a[href="http://localhost:18091"]').text()).toContain('审计中心')
  })
})
