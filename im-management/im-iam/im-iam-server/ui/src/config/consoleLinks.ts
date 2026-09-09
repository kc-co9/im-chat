export type ConsoleKey = 'iam' | 'audit' | 'monitor' | 'admin'

export interface ConsoleLink {
  key: ConsoleKey
  name: string
  location: string
}

const consoles: Record<ConsoleKey, Omit<ConsoleLink, 'key'>> = {
  iam: {
    name: '身份与权限',
    location: import.meta.env.VITE_IAM_CONSOLE_URL || 'http://localhost:18090',
  },
  audit: {
    name: '审计中心',
    location: import.meta.env.VITE_AUDIT_CONSOLE_URL || 'http://localhost:18091',
  },
  monitor: {
    name: '运行监控',
    location: import.meta.env.VITE_MONITOR_CONSOLE_URL || 'http://localhost:18092',
  },
  admin: {
    name: '用户管理',
    location: import.meta.env.VITE_ADMIN_CONSOLE_URL || 'http://localhost:18093',
  },
}

export function consoleLinks(current: ConsoleKey): ConsoleLink[] {
  return (Object.entries(consoles) as Array<[ConsoleKey, Omit<ConsoleLink, 'key'>]>)
    .filter(([key]) => key !== current)
    .map(([key, console]) => ({ key, ...console }))
}
