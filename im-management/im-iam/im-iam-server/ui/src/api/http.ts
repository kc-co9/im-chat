import axios from 'axios'

export interface HttpResult<T> {
  code: number
  msg: string
  data: T
}

export function cookie(name: string): string | undefined {
  return document.cookie
    .split('; ')
    .find((value) => value.startsWith(`${name}=`))
    ?.split('=')[1]
}

export function notifyHttpFailure(code?: number) {
  if (code === 401 || code === 10001) {
    window.dispatchEvent(new Event('iam:unauthorized'))
  }
  if (code === 403 || code === 10002) {
    window.dispatchEvent(new Event('iam:forbidden'))
  }
}

export function unwrapHttpResult<T>(result: HttpResult<T>): T {
  if (result.code !== 0) {
    notifyHttpFailure(result.code)
    throw new Error(result.msg)
  }
  return result.data
}

export const http = axios.create({
  baseURL: '/api/iam',
  withCredentials: true,
  timeout: 5000,
})

http.interceptors.request.use((config) => {
  const method = config.method?.toUpperCase()
  if (method && !['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const csrfToken = cookie('XSRF-TOKEN')
    if (csrfToken) {
      config.headers['X-XSRF-TOKEN'] = decodeURIComponent(csrfToken)
    }
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const result = response.data as Partial<HttpResult<unknown>>
    if (typeof result?.code === 'number' && 'data' in result) {
      response.data = unwrapHttpResult(result as HttpResult<unknown>)
    }
    return response
  },
  (error) => {
    notifyHttpFailure(error.response?.status)
    return Promise.reject(error)
  },
)
