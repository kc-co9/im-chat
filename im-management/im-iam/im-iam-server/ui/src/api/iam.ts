import { http } from './http'

export type WireLong = string
export type AdministratorStatus = 'ACTIVE' | 'DISABLED'
export type ApplicationStatus = 'ACTIVE' | 'DISABLED'
export type ApplicationRoleStatus = 'ACTIVE' | 'DISABLED'

export interface Paging {
  pageNo: number
  pageSize: number
}

export interface Page<T> {
  paging: Paging
  records: T[]
  total: WireLong
}

export interface Administrator extends Record<string, unknown> {
  id: WireLong
  username: string
  email: string
  status: AdministratorStatus
}

export interface ApplicationRole extends Record<string, unknown> {
  id: WireLong
  code: string
  name: string
  permissionIds: WireLong[]
  status: ApplicationRoleStatus
}

export interface IamRole extends Record<string, unknown> {
  id: WireLong
  code: string
  name: string
  status: ApplicationRoleStatus
}

export interface RoleAssignment {
  roleIds: WireLong[]
}

export interface Application extends Record<string, unknown> {
  appId: WireLong
  appKey: string
  name: string
  status: ApplicationStatus
}

export interface OAuthClient extends Record<string, unknown> {
  clientId: string
  appId: WireLong
  audienceAppId: WireLong
  audienceAppKey: string
  name: string
  grantTypes: Array<'AUTHORIZATION_CODE' | 'REFRESH_TOKEN' | 'CLIENT_CREDENTIALS'>
  scopes: string[]
  redirectUris: string[]
  postLogoutRedirectUris: string[]
  status: 'ACTIVE' | 'DISABLED'
}

export interface ApplicationPermission extends Record<string, unknown> {
  id: WireLong
  code: string
  name: string
  description: string
}

export interface OAuthSession extends Record<string, unknown> {
  id: string
  administratorId: WireLong
  username: string
  createdAt: WireLong
  lastAccessAt: WireLong
  expiresAt: WireLong
}

export interface IamPrincipal {
  administratorId: WireLong
  authorities: string[]
}

export interface ApplicationRegisterRequest {
  appKey: string
  name: string
}

export interface OAuthClientRegisterRequest {
  appKey: string
  audienceAppId: WireLong
  name: string
  clientId: string
  clientSecret: string
  grantTypes: string[]
  scopes: string[]
  redirectUris: string[]
  postLogoutRedirectUris: string[]
}

export interface ApplicationRoleCreateRequest {
  appId: WireLong
  code: string
  name: string
  permissionIds: WireLong[]
}

export interface ApplicationRoleUpdateRequest {
  roleId: WireLong
  name: string
  permissionIds: WireLong[]
}

export interface OAuthClientAccessUpdateRequest {
  clientId: string
  scopes: string[]
  redirectUris: string[]
  postLogoutRedirectUris: string[]
}

export const iamApi = {
  principal: () => http.get<IamPrincipal>('/me'),
  signOut: () => http.post('/logout', undefined, { baseURL: '' }),
  administrators: (paging: Paging = { pageNo: 1, pageSize: 20 }) =>
    http.get<Page<Administrator>>('/administrators/page', { params: paging }),
  applications: (paging: Paging = { pageNo: 1, pageSize: 20 }) =>
    http.get<Page<Application>>('/applications/page', { params: paging }),
  application: (appId: WireLong) =>
    http.get<Application>('/applications/detail', { params: { appId } }),
  oauthClients: (appId: WireLong, paging: Paging = { pageNo: 1, pageSize: 20 }) =>
    http.get<Page<OAuthClient>>('/oauth-clients/page', { params: { appId, ...paging } }),
  roles: (appId: WireLong, paging: Paging = { pageNo: 1, pageSize: 20 }) =>
    http.get<Page<ApplicationRole>>('/roles/page', { params: { appId, ...paging } }),
  iamRoles: () => http.get<IamRole[]>('/internal-roles/list'),
  administratorIamRoles: (administratorId: WireLong) =>
    http.get<RoleAssignment>('/administrators/internal-roles', {
      params: { administratorId },
    }),
  applicationRoleAssignments: (appId: WireLong, administratorId: WireLong) =>
    http.get<RoleAssignment>('/roles/assignments', { params: { appId, administratorId } }),
  permissions: (appId: WireLong, paging: Paging = { pageNo: 1, pageSize: 20 }, keyword?: string) =>
    http.get<Page<ApplicationPermission>>('/permissions/page', {
      params: {
        appId,
        ...paging,
        ...(keyword?.trim() ? { keyword: keyword.trim() } : {}),
      },
    }),
  sessions: (paging: Paging = { pageNo: 1, pageSize: 20 }) =>
    http.get<Page<OAuthSession>>('/sessions/page', { params: paging }),
  registerApplication: (request: ApplicationRegisterRequest) =>
    http.post<Application>('/applications/create', request),
  registerOAuthClient: (request: OAuthClientRegisterRequest) =>
    http.post('/oauth-clients/create', request),
  rotateOAuthClientSecret: (clientId: string, clientSecret: string) =>
    http.post('/oauth-clients/rotate-secret', { clientId, clientSecret }),
  disableOAuthClient: (clientId: string) => http.post('/oauth-clients/disable', { clientId }),
  createRole: (request: ApplicationRoleCreateRequest) => http.post('/roles/create', request),
  updateRole: (request: ApplicationRoleUpdateRequest) => http.post('/roles/update', request),
  updateOAuthClientAccess: (request: OAuthClientAccessUpdateRequest) =>
    http.post('/oauth-clients/update', request),
  changeApplicationRoles: (appId: WireLong, administratorId: WireLong, roleIds: WireLong[]) =>
    http.post('/roles/assignments', { appId, administratorId, roleIds }),
  disableAdministrator: (administratorId: WireLong) =>
    http.post('/administrators/disable', { administratorId }),
  enableAdministrator: (administratorId: WireLong) =>
    http.post('/administrators/enable', { administratorId }),
  deleteAdministrator: (administratorId: WireLong) =>
    http.post('/administrators/delete', { administratorId }),
  resetAdministratorPassword: (administratorId: WireLong, password: string) =>
    http.post('/administrators/reset-password', { administratorId, password }),
  changeAdministratorRoles: (administratorId: WireLong, roleIds: WireLong[]) =>
    http.post('/administrators/roles', { administratorId, roleIds }),
  revokeAdministratorSessions: (administratorId: WireLong) =>
    http.post('/administrators/revoke-sessions', { administratorId }),
  revokeSession: (sessionId: string) => http.post('/sessions/revoke', { sessionId }),
}
