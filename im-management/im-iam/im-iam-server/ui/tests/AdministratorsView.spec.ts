import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import AdministratorsView from '../src/views/AdministratorsView.vue'
import { iamApi } from '../src/api/iam'
import { auth } from '../src/state/auth'
import { notify } from '../src/state/feedback'

vi.mock('../src/state/feedback', async () => {
  const actual =
    await vi.importActual<typeof import('../src/state/feedback')>('../src/state/feedback')
  return { ...actual, notify: vi.fn() }
})

vi.mock('../src/api/iam', () => ({
  iamApi: {
    administrators: vi.fn(),
    disableAdministrator: vi.fn(),
    enableAdministrator: vi.fn(),
    deleteAdministrator: vi.fn(),
    resetAdministratorPassword: vi.fn(),
    iamRoles: vi.fn(),
    administratorIamRoles: vi.fn(),
    changeAdministratorRoles: vi.fn(),
    revokeAdministratorSessions: vi.fn(),
  },
}))

const administrator = {
  id: '1001',
  username: 'admin',
  email: 'admin@imchat.com',
  status: 'ACTIVE' as const,
}

describe('administrator workspace', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    auth.principal = {
      administratorId: '1',
      authorities: [
        'iam:administrator:read',
        'iam:administrator:write',
        'iam:role:write',
        'iam:session:revoke',
      ],
    }
    vi.mocked(iamApi.administrators).mockResolvedValue({
      data: {
        paging: { pageNo: 1, pageSize: 20 },
        records: [administrator],
        total: '1',
      },
    } as never)
    vi.mocked(iamApi.iamRoles).mockResolvedValue({
      data: [
        { id: '11', code: 'IAM_ADMIN', name: 'IAM 管理员', status: 'ACTIVE' },
        { id: '12', code: 'IAM_AUDITOR', name: 'IAM 审计员', status: 'ACTIVE' },
      ],
    } as never)
    vi.mocked(iamApi.administratorIamRoles).mockResolvedValue({
      data: { roleIds: ['12'] },
    } as never)
  })

  it('shows role assignment as the primary action and other actions in a keyboard menu', async () => {
    const wrapper = mount(AdministratorsView)
    await flushPromises()

    expect(wrapper.get('[data-testid="assign-roles-1001"]').text()).toContain('分配角色')
    expect(wrapper.get('summary').text()).toContain('更多')
    expect(wrapper.text()).toContain('正常')
  })

  it('loads IAM roles and the current assignment before changing roles', async () => {
    const wrapper = mount(AdministratorsView)
    await flushPromises()

    await wrapper.get('[data-testid="assign-roles-1001"]').trigger('click')
    await flushPromises()

    expect(iamApi.iamRoles).toHaveBeenCalledTimes(1)
    expect(iamApi.administratorIamRoles).toHaveBeenCalledWith('1001')
    expect(wrapper.text()).toContain('IAM 管理员')
    expect(wrapper.get<HTMLInputElement>('[data-testid="iam-role-12"]').element.checked).toBe(true)

    await wrapper.get('[data-testid="iam-role-11"]').setValue(true)
    await wrapper.get('[data-testid="iam-role-12"]').setValue(false)
    await wrapper.get('[data-testid="administrator-role-form"]').trigger('submit')
    await flushPromises()

    expect(iamApi.changeAdministratorRoles).toHaveBeenCalledWith('1001', ['11'])
    expect(notify).toHaveBeenCalledWith('管理员内部角色已更新')
  })

  it('renders an administrator empty state', async () => {
    vi.mocked(iamApi.administrators).mockResolvedValueOnce({
      data: { paging: { pageNo: 1, pageSize: 20 }, records: [], total: '0' },
    } as never)
    const wrapper = mount(AdministratorsView)
    await flushPromises()

    expect(wrapper.text()).toContain('暂无管理账号')
  })

  it('retains reset input on failure and reports success after retry', async () => {
    vi.mocked(iamApi.resetAdministratorPassword).mockRejectedValueOnce(new Error('密码不符合要求'))
    const wrapper = mount(AdministratorsView)
    await flushPromises()
    await wrapper.get('[data-testid="reset-password-1001"]').trigger('click')
    await wrapper.get('[data-testid="new-password"]').setValue('new-password')
    await wrapper.get('[data-testid="password-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('密码不符合要求')
    expect(wrapper.get<HTMLInputElement>('[data-testid="new-password"]').element.value).toBe(
      'new-password',
    )

    await wrapper.get('[data-testid="password-form"]').trigger('submit')
    await flushPromises()
    expect(iamApi.resetAdministratorPassword).toHaveBeenLastCalledWith('1001', 'new-password')
    expect(notify).toHaveBeenCalledWith('管理员密码重置成功')
  })

  it('requires confirmation before disabling an administrator', async () => {
    const wrapper = mount(AdministratorsView)
    await flushPromises()
    await wrapper.get('[data-testid="disable-1001"]').trigger('click')
    expect(wrapper.text()).toContain('撤销其全部在线授权')
    await wrapper.get('[data-testid="confirm-cancel"]').trigger('click')
    expect(iamApi.disableAdministrator).not.toHaveBeenCalled()

    await wrapper.get('[data-testid="disable-1001"]').trigger('click')
    await wrapper.get('[data-testid="confirm-action"]').trigger('click')
    await flushPromises()
    expect(iamApi.disableAdministrator).toHaveBeenCalledWith('1001')
  })

  it('confirms session revocation and irreversible deletion separately', async () => {
    const wrapper = mount(AdministratorsView)
    await flushPromises()

    await wrapper.get('[data-testid="revoke-sessions-1001"]').trigger('click')
    expect(wrapper.text()).toContain('全部 OAuth 会话')
    await wrapper.get('[data-testid="confirm-action"]').trigger('click')
    await flushPromises()
    expect(iamApi.revokeAdministratorSessions).toHaveBeenCalledWith('1001')

    await wrapper.get('[data-testid="delete-1001"]').trigger('click')
    expect(wrapper.text()).toContain('不可撤销')
    await wrapper.get('[data-testid="confirm-action"]').trigger('click')
    await flushPromises()
    expect(iamApi.deleteAdministrator).toHaveBeenCalledWith('1001')
  })

  it('preserves prior administrators when refresh fails', async () => {
    const wrapper = mount(AdministratorsView)
    await flushPromises()
    vi.mocked(iamApi.administrators).mockRejectedValueOnce(new Error('刷新失败'))
    await wrapper.get('[data-testid="refresh-administrators"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('刷新失败')
    expect(wrapper.text()).toContain('admin@imchat.com')
  })
})
