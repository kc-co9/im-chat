import { reactive } from 'vue'

export type ToastTone = 'success' | 'error' | 'info'

export interface ToastMessage {
  id: number
  message: string
  tone: ToastTone
}

export const toasts = reactive<ToastMessage[]>([])

let nextToastId = 1

export function notify(message: string, tone: ToastTone = 'success') {
  const toast = { id: nextToastId++, message, tone }
  toasts.push(toast)
  window.setTimeout(() => dismissToast(toast.id), 4000)
}

export function dismissToast(id: number) {
  const index = toasts.findIndex((toast) => toast.id === id)
  if (index >= 0) toasts.splice(index, 1)
}

export function clearToasts() {
  toasts.splice(0)
}
