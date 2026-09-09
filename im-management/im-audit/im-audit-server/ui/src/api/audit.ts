import { http } from './http'

export interface Principal {
  administratorId: string
  username: string
  appKey: string
  authorities: string[]
}

export interface Paging {
  pageNo: number
  pageSize: number
}

export interface Page<T> {
  paging: Paging
  records: T[]
  total: string
}

export interface AuditListItem {
  auditId: string
  sourceApp: string
  type: 'BUSINESS' | 'SECURITY'
  action: string
  actorId: string | null
  actorName: string | null
  targetType: string
  targetId: string | null
  outcome: 'SUCCESS' | 'FAILURE'
  description: string
  occurredAt: string
}

export interface AuditDetail extends AuditListItem {
  actorType: string
  clientAddress: string | null
  userAgent: string | null
  traceId: string | null
  errorCode: string | null
  attributes: Record<string, string>
}

export const api = {
  session: () => http.get<Principal>('/iam/me', { baseURL: '' }),
  signOut: () => http.post('/iam/logout', undefined, { baseURL: '' }),
  audits: (params: Record<string, unknown>) => http.get<Page<AuditListItem>>('/audits', { params }),
  audit: (auditId: string) => http.get<AuditDetail>(`/audits/${auditId}`),
  exportAudits: (params: Record<string, unknown>) =>
    http.get<Blob>('/audits/export', { params, responseType: 'blob' }),
}
