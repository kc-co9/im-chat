import { flushPromises, mount } from '@vue/test-utils'
import { reactive } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import ApplicationDetailView from '../src/views/ApplicationDetailView.vue'
import { iamApi } from '../src/api/iam'
import { auth } from '../src/state/auth'
import { notify } from '../src/state/feedback'

const route = reactive({
  params: { appId: '10001' },
  query: { tab: 'clients' as string },
})
const replace = vi.fn()

vi.mock('vue-router', () => ({
  RouterLink: { template: '<a><slot /></a>' },
  useRoute: () => route,
  useRouter: () => ({ replace }),
}))

vi.mock('../src/state/feedback', async () => {
  const actual =
    await vi.importActual<typeof import('../src/state/feedback')>('../src/state/feedback')
  return { ...actual, notify: vi.fn() }
})

vi.mock('../src/api/iam', () => ({
  iamApi: {
    application: vi.fn(),
    applications: vi.fn(),
    oauthClients: vi.fn(),
    registerOAuthClient: vi.fn(),
    rotateOAuthClientSecret: vi.fn(),
    disableOAuthClient: vi.fn(),
    roles: vi.fn(),
    permissions: vi.fn(),
    createRole: vi.fn(),
    updateRole: vi.fn(),
    updateOAuthClientAccess: vi.fn(),
    administrators: vi.fn(),
    applicationRoleAssignments: vi.fn(),
    changeApplicationRoles: vi.fn(),
  },
}))

const page = <T>(records: T[], total = records.length) => ({
  data: { paging: { pageNo: 1, pageSize: 20 }, records, total: String(total) },
})

describe('application detail workspace', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    route.params.appId = '10001'
    route.query.tab = 'clients'
    auth.principal = {
      administratorId: '1',
      authorities: [
        'iam:application:read',
        'iam:client:write',
        'iam:role:read',
        'iam:role:write',
        'iam:permission:read',
      ],
    }
    vi.mocked(iamApi.application).mockResolvedValue({
      data: { appId: '10001', appKey: 'imAudit', name: '集中审计', status: 'ACTIVE' },
    } as never)
    vi.mocked(iamApi.applications).mockResolvedValue(
      page([
        { appId: '10001', appKey: 'imAudit', name: '集中审计', status: 'ACTIVE' },
        { appId: '10002', appKey: 'imIam', name: 'IAM', status: 'ACTIVE' },
      ]) as never,
    )
    vi.mocked(iamApi.oauthClients).mockResolvedValue(
      page([
        {
          clientId: 'im-audit-client',
          appId: '10001',
          audienceAppId: '10001',
          audienceAppKey: 'imAudit',
          name: '审计 Web',
          grantTypes: ['AUTHORIZATION_CODE', 'REFRESH_TOKEN'],
          scopes: ['openid', 'profile'],
          redirectUris: ['http://localhost:18091/iam/callback'],
          postLogoutRedirectUris: ['http://localhost:18091/'],
          status: 'ACTIVE',
        },
      ]) as never,
    )
    vi.mocked(iamApi.roles).mockResolvedValue(
      page([
        {
          id: '11',
          code: 'AUDITOR',
          name: '审计员',
          permissionIds: ['21'],
          status: 'ACTIVE',
        },
      ]) as never,
    )
    vi.mocked(iamApi.permissions).mockResolvedValue(
      page([
        { id: '21', code: 'audit:read', name: '查询审计', description: '查询审计记录' },
        { id: '22', code: 'audit:export', name: '导出审计', description: '导出审计记录' },
      ]) as never,
    )
    vi.mocked(iamApi.administrators).mockResolvedValue(
      page([
        {
          id: '1001',
          username: 'admin',
          email: 'admin@imchat.com',
          status: 'ACTIVE',
        },
      ]) as never,
    )
    vi.mocked(iamApi.applicationRoleAssignments).mockResolvedValue({
      data: { roleIds: ['11'] },
    } as never)
  })

  it('loads authoritative application and client data', async () => {
    const wrapper = mount(ApplicationDetailView)
    await flushPromises()

    expect(iamApi.application).toHaveBeenCalledWith('10001')
    expect(iamApi.oauthClients).toHaveBeenCalledWith('10001', { pageNo: 1, pageSize: 20 })
    expect(wrapper.text()).toContain('集中审计')
    expect(wrapper.text()).toContain('im-audit-client')
    expect(wrapper.text()).toContain('浏览器')
    expect(wrapper.text()).not.toContain('clientSecret')
  })

  it('shows recoverable client loading failures and retry', async () => {
    vi.mocked(iamApi.oauthClients).mockRejectedValueOnce(new Error('客户端列表暂不可用'))
    const wrapper = mount(ApplicationDetailView)
    await flushPromises()

    expect(wrapper.text()).toContain('客户端列表暂不可用')
    vi.mocked(iamApi.oauthClients).mockResolvedValueOnce(page([]) as never)
    await wrapper.get('[data-testid="clients-retry"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('暂无 OAuth 客户端')
  })

  it('keeps the current client page when the next page fails', async () => {
    vi.mocked(iamApi.oauthClients).mockResolvedValueOnce(
      page(
        [
          {
            clientId: 'im-audit-client',
            appId: '10001',
            audienceAppId: '10001',
            audienceAppKey: 'imAudit',
            name: '审计 Web',
            grantTypes: ['AUTHORIZATION_CODE'],
            scopes: ['openid'],
            redirectUris: [],
            postLogoutRedirectUris: [],
            status: 'ACTIVE',
          },
        ],
        21,
      ) as never,
    )
    const wrapper = mount(ApplicationDetailView)
    await flushPromises()
    vi.mocked(iamApi.oauthClients).mockRejectedValueOnce(new Error('下一页加载失败'))

    await wrapper.get('[data-testid="clients-next"]').trigger('click')
    await flushPromises()

    expect(iamApi.oauthClients).toHaveBeenLastCalledWith('10001', { pageNo: 2, pageSize: 20 })
    expect(wrapper.text()).toContain('第 1 页')
    expect(wrapper.text()).toContain('审计 Web')
  })

  it('switches client type fields and submits exact machine payload once', async () => {
    let resolveRegistration: (() => void) | undefined
    vi.mocked(iamApi.registerOAuthClient).mockImplementationOnce(
      () =>
        new Promise<void>((resolve) => {
          resolveRegistration = resolve
        }) as never,
    )
    const wrapper = mount(ApplicationDetailView)
    await flushPromises()
    await wrapper.get('[data-testid="register-client"]').trigger('click')

    expect(wrapper.text()).toContain('PKCE')
    expect(wrapper.find('[data-testid="client-redirect-uri"]').exists()).toBe(true)
    await wrapper.get('[data-testid="client-type-machine"]').trigger('click')
    expect(wrapper.find('[data-testid="client-redirect-uri"]').exists()).toBe(false)

    await wrapper.get('[data-testid="client-name"]').setValue('目录同步')
    await wrapper.get('[data-testid="client-id"]').setValue('im-audit-catalog')
    await wrapper.get('[data-testid="client-secret"]').setValue('catalog-secret')
    await wrapper.get('[data-testid="client-audience"]').setValue('10002')
    await wrapper.get('[data-testid="client-scopes"]').setValue('iam.catalog.write')
    const firstSubmission = wrapper.get('[data-testid="client-form"]').trigger('submit')
    await wrapper.get('[data-testid="client-form"]').trigger('submit')
    resolveRegistration?.()
    await firstSubmission
    await flushPromises()

    expect(iamApi.registerOAuthClient).toHaveBeenCalledTimes(1)
    expect(iamApi.registerOAuthClient).toHaveBeenCalledWith({
      appKey: 'imAudit',
      audienceAppId: '10002',
      name: '目录同步',
      clientId: 'im-audit-catalog',
      clientSecret: 'catalog-secret',
      grantTypes: ['CLIENT_CREDENTIALS'],
      scopes: ['iam.catalog.write'],
      redirectUris: [],
      postLogoutRedirectUris: [],
    })
    expect(notify).toHaveBeenCalledWith('OAuth 客户端注册成功')
  })

  it('retains client form input when registration fails', async () => {
    vi.mocked(iamApi.registerOAuthClient).mockRejectedValueOnce(new Error('Client ID 已存在'))
    const wrapper = mount(ApplicationDetailView)
    await flushPromises()
    await wrapper.get('[data-testid="register-client"]').trigger('click')
    await wrapper.get('[data-testid="client-name"]').setValue('重复客户端')
    await wrapper.get('[data-testid="client-id"]').setValue('duplicate-client')
    await wrapper.get('[data-testid="client-secret"]').setValue('secret')
    await wrapper.get('[data-testid="client-scopes"]').setValue('openid')
    await wrapper.get('[data-testid="client-redirect-uri"]').setValue('http://localhost/callback')
    await wrapper.get('[data-testid="client-logout-uri"]').setValue('http://localhost/')
    await wrapper.get('[data-testid="client-form"]').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('Client ID 已存在')
    expect(wrapper.get<HTMLInputElement>('[data-testid="client-id"]').element.value).toBe(
      'duplicate-client',
    )
  })

  it('submits browser grant defaults and redirect fields', async () => {
    const wrapper = mount(ApplicationDetailView)
    await flushPromises()
    await wrapper.get('[data-testid="register-client"]').trigger('click')
    await wrapper.get('[data-testid="client-name"]').setValue('审计 Web')
    await wrapper.get('[data-testid="client-id"]').setValue('im-audit-web')
    await wrapper.get('[data-testid="client-secret"]').setValue('web-secret')
    await wrapper.get('[data-testid="client-audience"]').setValue('10001')
    await wrapper.get('[data-testid="client-scopes"]').setValue('openid, profile')
    await wrapper
      .get('[data-testid="client-redirect-uri"]')
      .setValue('http://localhost:18091/iam/callback')
    await wrapper.get('[data-testid="client-logout-uri"]').setValue('http://localhost:18091/')
    await wrapper.get('[data-testid="client-form"]').trigger('submit')
    await flushPromises()

    expect(iamApi.registerOAuthClient).toHaveBeenCalledWith(
      expect.objectContaining({
        grantTypes: ['AUTHORIZATION_CODE', 'REFRESH_TOKEN'],
        scopes: ['openid', 'profile'],
        redirectUris: ['http://localhost:18091/iam/callback'],
        postLogoutRedirectUris: ['http://localhost:18091/'],
      }),
    )
  })

  it('requires consequence confirmation before rotating a secret', async () => {
    const wrapper = mount(ApplicationDetailView)
    await flushPromises()
    await wrapper.get('[data-testid="rotate-im-audit-client"]').trigger('click')
    await wrapper.get('[data-testid="rotation-secret"]').setValue('new-secret')
    await wrapper.get('[data-testid="rotation-form"]').trigger('submit')

    expect(wrapper.text()).toContain('旧密钥将立即失效')
    await wrapper.get('[data-testid="confirm-cancel"]').trigger('click')
    expect(iamApi.rotateOAuthClientSecret).not.toHaveBeenCalled()

    await wrapper.get('[data-testid="rotation-form"]').trigger('submit')
    await wrapper.get('[data-testid="confirm-action"]').trigger('click')
    await flushPromises()
    expect(iamApi.rotateOAuthClientSecret).toHaveBeenCalledWith('im-audit-client', 'new-secret')
  })

  it('requires confirmation before disabling a client', async () => {
    const wrapper = mount(ApplicationDetailView)
    await flushPromises()
    await wrapper.get('[data-testid="disable-im-audit-client"]').trigger('click')
    expect(wrapper.text()).toContain('该客户端将无法继续访问')
    await wrapper.get('[data-testid="confirm-action"]').trigger('click')
    await flushPromises()

    expect(iamApi.disableOAuthClient).toHaveBeenCalledWith('im-audit-client')
    expect(notify).toHaveBeenCalledWith('OAuth 客户端已停用')
  })

  it('keeps permission selections while searching and creates a role', async () => {
    const wrapper = mount(ApplicationDetailView)
    await flushPromises()
    await wrapper.get('[data-testid="tab-roles"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="create-role"]').trigger('click')
    await wrapper.get('[data-testid="permission-21"]').setValue(true)
    await wrapper.get('[data-testid="permission-keyword"]').setValue('export')
    await wrapper.get('[data-testid="permission-search"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="permission-22"]').setValue(true)
    await wrapper.get('[data-testid="role-code"]').setValue('AUDIT_MANAGER')
    await wrapper.get('[data-testid="role-name"]').setValue('审计管理员')
    await wrapper.get('[data-testid="role-form"]').trigger('submit')
    await flushPromises()

    expect(iamApi.permissions).toHaveBeenLastCalledWith(
      '10001',
      { pageNo: 1, pageSize: 20 },
      'export',
    )
    expect(iamApi.createRole).toHaveBeenCalledWith({
      appId: '10001',
      code: 'AUDIT_MANAGER',
      name: '审计管理员',
      permissionIds: ['21', '22'],
    })
  })

  it('keeps permission data available when the role request fails', async () => {
    vi.mocked(iamApi.roles).mockRejectedValueOnce(new Error('角色加载失败'))
    const wrapper = mount(ApplicationDetailView)
    await flushPromises()

    await wrapper.get('[data-testid="tab-roles"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('角色加载失败')

    await wrapper.get('[data-testid="tab-permissions"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('audit:read')
  })

  it('loads existing permissions and updates an application role', async () => {
    route.query.tab = 'roles'
    const wrapper = mount(ApplicationDetailView)
    await flushPromises()

    await wrapper.get('[data-testid="edit-role-11"]').trigger('click')
    expect(wrapper.get<HTMLInputElement>('[data-testid="permission-21"]').element.checked).toBe(
      true,
    )
    await wrapper.get('[data-testid="permission-22"]').setValue(true)
    await wrapper.get('[data-testid="role-name"]').setValue('高级审计员')
    await wrapper.get('[data-testid="role-form"]').trigger('submit')
    await flushPromises()

    expect(iamApi.updateRole).toHaveBeenCalledWith({
      roleId: '11',
      name: '高级审计员',
      permissionIds: ['21', '22'],
    })
    expect(notify).toHaveBeenCalledWith('应用角色已更新')
  })

  it('edits OAuth client scopes and redirects without changing its grant family', async () => {
    const wrapper = mount(ApplicationDetailView)
    await flushPromises()

    await wrapper.get('[data-testid="edit-client-im-audit-client"]').trigger('click')
    expect(wrapper.text()).toContain('浏览器客户端')
    await wrapper.get('[data-testid="edit-client-scopes"]').setValue('openid, profile, audit:read')
    await wrapper
      .get('[data-testid="edit-client-redirect-uri"]')
      .setValue('http://localhost:18091/iam/updated-callback')
    await wrapper.get('[data-testid="client-access-form"]').trigger('submit')
    await flushPromises()

    expect(iamApi.updateOAuthClientAccess).toHaveBeenCalledWith({
      clientId: 'im-audit-client',
      scopes: ['openid', 'profile', 'audit:read'],
      redirectUris: ['http://localhost:18091/iam/updated-callback'],
      postLogoutRedirectUris: ['http://localhost:18091/'],
    })
  })

  it('loads an administrator application roles and changes only that assignment', async () => {
    route.query.tab = 'members'
    const wrapper = mount(ApplicationDetailView)
    await flushPromises()

    await wrapper.get('[data-testid="assign-application-roles-1001"]').trigger('click')
    await flushPromises()
    expect(iamApi.applicationRoleAssignments).toHaveBeenCalledWith('10001', '1001')
    expect(
      wrapper.get<HTMLInputElement>('[data-testid="application-role-11"]').element.checked,
    ).toBe(true)

    await wrapper.get('[data-testid="application-role-11"]').setValue(false)
    await wrapper.get('[data-testid="application-role-form"]').trigger('submit')
    await flushPromises()

    expect(iamApi.changeApplicationRoles).toHaveBeenCalledWith('10001', '1001', [])
  })
})
