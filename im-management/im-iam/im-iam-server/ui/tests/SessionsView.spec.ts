import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SessionsView from '../src/views/SessionsView.vue'
import { iamApi } from '../src/api/iam'
import { auth } from '../src/state/auth'
import { notify } from '../src/state/feedback'

vi.mock('../src/state/feedback', async () => {
  const actual =
    await vi.importActual<typeof import('../src/state/feedback')>('../src/state/feedback')
  return { ...actual, notify: vi.fn() }
})

vi.mock('../src/api/iam', () => ({
  iamApi: { sessions: vi.fn(), revokeSession: vi.fn() },
}))

describe('OAuth session workspace', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    auth.principal = {
      administratorId: '1',
      authorities: ['iam:session:read', 'iam:session:revoke'],
    }
    vi.mocked(iamApi.sessions).mockResolvedValue({
      data: {
        paging: { pageNo: 1, pageSize: 20 },
        records: [
          {
            id: 'authorization-1',
            administratorId: '1001',
            username: 'admin',
            createdAt: '1788832800000',
            lastAccessAt: '1788832860000',
            expiresAt: '1788861600000',
          },
        ],
        total: '1',
      },
    } as never)
  })

  it('preserves timestamp formatting and paging request semantics', async () => {
    const wrapper = mount(SessionsView)
    await flushPromises()

    expect(iamApi.sessions).toHaveBeenCalledWith({ pageNo: 1, pageSize: 20 })
    expect(wrapper.text()).toContain('admin')
    expect(wrapper.text()).not.toContain('1788832800000')
  })

  it('renders a session empty state', async () => {
    vi.mocked(iamApi.sessions).mockResolvedValueOnce({
      data: { paging: { pageNo: 1, pageSize: 20 }, records: [], total: '0' },
    } as never)
    const wrapper = mount(SessionsView)
    await flushPromises()

    expect(wrapper.text()).toContain('暂无在线会话')
  })

  it('confirms revocation and reports success', async () => {
    const wrapper = mount(SessionsView)
    await flushPromises()
    await wrapper.get('[data-testid="revoke-authorization-1"]').trigger('click')
    expect(wrapper.text()).toContain('admin')
    await wrapper.get('[data-testid="confirm-action"]').trigger('click')
    await flushPromises()

    expect(iamApi.revokeSession).toHaveBeenCalledWith('authorization-1')
    expect(notify).toHaveBeenCalledWith('OAuth 会话已撤销')
  })

  it('preserves prior sessions when refresh fails', async () => {
    const wrapper = mount(SessionsView)
    await flushPromises()
    vi.mocked(iamApi.sessions).mockRejectedValueOnce(new Error('刷新失败'))
    await wrapper.get('[data-testid="refresh-sessions"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('刷新失败')
    expect(wrapper.text()).toContain('authorization-1')
  })
})
