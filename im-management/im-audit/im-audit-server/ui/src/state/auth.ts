import { reactive } from 'vue'
import { api, type Principal } from '../api/audit'

export const auth = reactive<{ principal?: Principal; checked: boolean }>({ checked: false })

export async function loadSession() {
  try {
    auth.principal = (await api.session()).data
  } catch {
    auth.principal = undefined
  }
  auth.checked = true
}

export function can(permission: string) {
  return auth.principal?.authorities.includes(permission) ?? false
}
