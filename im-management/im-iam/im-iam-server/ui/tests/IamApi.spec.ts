import type { AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { beforeEach, describe, expect, it } from 'vitest'
import { http } from '../src/api/http'
import { iamApi } from '../src/api/iam'

describe('IAM management API contract', () => {
  const requests: InternalAxiosRequestConfig[] = []

  beforeEach(() => {
    requests.length = 0
    http.defaults.adapter = async (config) => {
      requests.push(config)
      return {
        data: { code: 0, msg: 'success', data: {} },
        status: 200,
        statusText: 'OK',
        headers: {},
        config,
      } as AxiosResponse
    }
  })

  it('queries roles inside an application', async () => {
    await iamApi.roles('10001', { pageNo: 2, pageSize: 30 })

    expect(requests).toHaveLength(1)
    expect(requests[0].url).toBe('/roles/page')
    expect(requests[0].params).toEqual({ appId: '10001', pageNo: 2, pageSize: 30 })
  })

  it('loads and changes IAM administrator role assignments', async () => {
    await iamApi.iamRoles()
    await iamApi.administratorIamRoles('90071992547409930')
    await iamApi.changeAdministratorRoles('90071992547409930', ['11', '12'])

    expect(requests[0].url).toBe('/internal-roles/list')
    expect(requests[1].url).toBe('/administrators/internal-roles')
    expect(requests[1].params).toEqual({ administratorId: '90071992547409930' })
    expect(requests[2].url).toBe('/administrators/roles')
    expect(JSON.parse(requests[2].data as string)).toEqual({
      administratorId: '90071992547409930',
      roleIds: ['11', '12'],
    })
  })

  it('updates role permissions, client access, and application role assignments', async () => {
    await iamApi.updateRole({ roleId: '11', name: '审计员', permissionIds: ['21'] })
    await iamApi.updateOAuthClientAccess({
      clientId: 'im-audit-client',
      scopes: ['openid'],
      redirectUris: ['http://localhost/callback'],
      postLogoutRedirectUris: [],
    })
    await iamApi.applicationRoleAssignments('10001', '1001')
    await iamApi.changeApplicationRoles('10001', '1001', ['11'])

    expect(requests.map((request) => request.url)).toEqual([
      '/roles/update',
      '/oauth-clients/update',
      '/roles/assignments',
      '/roles/assignments',
    ])
    expect(requests[2].params).toEqual({ appId: '10001', administratorId: '1001' })
    expect(JSON.parse(requests[3].data as string)).toEqual({
      appId: '10001',
      administratorId: '1001',
      roleIds: ['11'],
    })
  })

  it('loads authoritative application detail with a WireLong app id', async () => {
    await iamApi.application('90071992547409930')

    expect(requests[0].url).toBe('/applications/detail')
    expect(requests[0].params).toEqual({ appId: '90071992547409930' })
  })

  it('pages OAuth clients inside an application', async () => {
    await iamApi.oauthClients('90071992547409930', { pageNo: 2, pageSize: 10 })

    expect(requests[0].url).toBe('/oauth-clients/page')
    expect(requests[0].params).toEqual({
      appId: '90071992547409930',
      pageNo: 2,
      pageSize: 10,
    })
  })

  it('passes an optional permission keyword without changing omitted requests', async () => {
    await iamApi.permissions('10001', { pageNo: 2, pageSize: 30 }, '  audit  ')
    await iamApi.permissions('10001')

    expect(requests[0].url).toBe('/permissions/page')
    expect(requests[0].params).toEqual({
      appId: '10001',
      pageNo: 2,
      pageSize: 30,
      keyword: 'audit',
    })
    expect(requests[1].params).toEqual({ appId: '10001', pageNo: 1, pageSize: 20 })
  })

  it('loads the current IAM administrator through the management boundary', async () => {
    await iamApi.principal()

    expect(requests[0].url).toBe('/me')
    expect(requests[0].method).toBe('get')
  })

  it('submits administrator actions through request bodies', async () => {
    await iamApi.disableAdministrator('90071992547409930')
    await iamApi.changeAdministratorRoles('90071992547409930', ['11', '12'])

    expect(requests[0].url).toBe('/administrators/disable')
    expect(requests[0].data).toBe(JSON.stringify({ administratorId: '90071992547409930' }))
    expect(requests[1].url).toBe('/administrators/roles')
    expect(requests[1].data).toBe(
      JSON.stringify({
        administratorId: '90071992547409930',
        roleIds: ['11', '12'],
      }),
    )
  })

  it('revokes an OAuth session through its request body', async () => {
    await iamApi.revokeSession('authorization-1')

    expect(requests[0].url).toBe('/sessions/revoke')
    expect(requests[0].data).toBe(JSON.stringify({ sessionId: 'authorization-1' }))
  })

  it('revokes all OAuth sessions owned by an administrator', async () => {
    await iamApi.revokeAdministratorSessions('90071992547409930')

    expect(requests[0].url).toBe('/administrators/revoke-sessions')
    expect(requests[0].data).toBe(
      JSON.stringify({
        administratorId: '90071992547409930',
      }),
    )
  })

  it('rotates and disables OAuth clients through exact request bodies', async () => {
    await iamApi.rotateOAuthClientSecret('im-audit-client', 'new-secret')
    await iamApi.disableOAuthClient('im-audit-client')

    expect(requests[0].url).toBe('/oauth-clients/rotate-secret')
    expect(requests[0].data).toBe(
      JSON.stringify({ clientId: 'im-audit-client', clientSecret: 'new-secret' }),
    )
    expect(requests[1].url).toBe('/oauth-clients/disable')
    expect(requests[1].data).toBe(JSON.stringify({ clientId: 'im-audit-client' }))
  })
})
