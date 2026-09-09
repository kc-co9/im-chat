import { reactive } from 'vue'
import { iamApi, type IamPrincipal } from '../api/iam'

export const auth = reactive<{ principal?: IamPrincipal; checked: boolean }>({ checked: false })

export async function loadPrincipal() {
  try {
    const response = await iamApi.principal()
    const principal = response.data
    auth.principal = principal && Array.isArray(principal.authorities) ? principal : undefined
  } catch {
    auth.principal = undefined
  }
  auth.checked = true
}

export function can(permission: string) {
  return auth.principal?.authorities.includes(permission) ?? false
}
