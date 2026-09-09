import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ApplicationsView from '../src/views/ApplicationsView.vue'
import { iamApi } from '../src/api/iam'
import { auth } from '../src/state/auth'
import { notify } from '../src/state/feedback'

vi.mock('vue-router', () => ({
  RouterLink: { props: ['to'], template: '<a><slot /></a>' },
}))

vi.mock('../src/state/feedback', async () => {
  const actual =
    await vi.importActual<typeof import('../src/state/feedback')>('../src/state/feedback')
  return { ...actual, notify: vi.fn() }
})

vi.mock('../src/api/iam', () => ({
  iamApi: {
    applications: vi.fn(),
    registerApplication: vi.fn(),
  },
}))

function page(records: Array<Record<string, unknown>>, total = records.length) {
  return { data: { paging: { pageNo: 1, pageSize: 20 }, records, total: String(total) } }
}

describe('applications workspace', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    auth.principal = {
      administratorId: '1',
      authorities: ['iam:application:read', 'iam:application:write', 'iam:client:write'],
    }
    vi.mocked(iamApi.applications).mockResolvedValue(
      page([{ appId: '10001', appKey: 'imAudit', name: '集中审计', status: 'ACTIVE' }]) as never,
    )
  })

  it('renders the scan-first application table and detail entry', async () => {
    const wrapper = mount(ApplicationsView)
    await flushPromises()

    expect(wrapper.text()).toContain('集中审计')
    expect(wrapper.text()).toContain('imAudit')
    expect(wrapper.text()).toContain('管理访问')
    expect(wrapper.text()).not.toContain('注册 OAuth 客户端')
  })

  it('renders a contextual empty action', async () => {
    vi.mocked(iamApi.applications).mockResolvedValueOnce(page([]) as never)
    const wrapper = mount(ApplicationsView)
    await flushPromises()

    expect(wrapper.text()).toContain('暂无接入应用')
    expect(wrapper.text()).toContain('注册第一个应用')
  })

  it('registers an application in a drawer and reports success', async () => {
    const wrapper = mount(ApplicationsView)
    await flushPromises()
    await wrapper.get('[data-testid="register-application"]').trigger('click')
    await wrapper.get('[data-testid="application-key"]').setValue('imAdmin')
    await wrapper.get('[data-testid="application-name"]').setValue('管理后台')
    await wrapper.get('[data-testid="application-form"]').trigger('submit')
    await flushPromises()

    expect(iamApi.registerApplication).toHaveBeenCalledWith({
      appKey: 'imAdmin',
      name: '管理后台',
    })
    expect(notify).toHaveBeenCalledWith('IAM 应用注册成功')
  })

  it('retains drawer input on failure and keeps prior data when refresh fails', async () => {
    vi.mocked(iamApi.registerApplication).mockRejectedValueOnce(new Error('appKey 已存在'))
    const wrapper = mount(ApplicationsView)
    await flushPromises()
    await wrapper.get('[data-testid="register-application"]').trigger('click')
    await wrapper.get('[data-testid="application-key"]').setValue('imAudit')
    await wrapper.get('[data-testid="application-name"]').setValue('重复应用')
    await wrapper.get('[data-testid="application-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('appKey 已存在')
    expect(wrapper.get<HTMLInputElement>('[data-testid="application-key"]').element.value).toBe(
      'imAudit',
    )

    vi.mocked(iamApi.applications).mockRejectedValueOnce(new Error('刷新失败'))
    await wrapper.get('[data-testid="refresh-applications"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('刷新失败')
    expect(wrapper.text()).toContain('集中审计')
  })
})
