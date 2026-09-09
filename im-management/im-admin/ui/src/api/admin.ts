import { http } from './http'

export type UserStatus = 'NORMAL' | 'BANNED'
export interface Principal {
  administratorId: string
  username: string
  appKey: string
  authorities: string[]
}
export interface User {
  id: string
  username: string
  email: string
  status: UserStatus
  deleted: boolean
  createdAt: string
  updatedAt: string
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
export type UserPage = Page<User>
export const api = {
  session: () => http.get<Principal>('/iam/me', { baseURL: '' }),
  signOut: () => http.post('/iam/logout', undefined, { baseURL: '' }),
  users: (params: Record<string, unknown>) => http.get<UserPage>('/users/page', { params }),
  user: (userId: string) => http.get<User>('/users/detail', { params: { userId } }),
  updateUser: (body: unknown) => http.post<void>('/users/update', body),
  userCommand: (action: string, body: unknown) => http.post(`/users/${action}`, body),
}
