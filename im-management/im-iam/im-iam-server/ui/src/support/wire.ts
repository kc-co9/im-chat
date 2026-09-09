export const userTimeZone = Intl.DateTimeFormat().resolvedOptions().timeZone

export function safeWireNumber(value: string | number): number {
  const number = typeof value === 'number' ? value : Number(value)
  if (!Number.isSafeInteger(number)) {
    throw new Error('wire value must be a safe integer')
  }
  return number
}

export function formatTimestamp(
  timestamp: string | number | null | undefined,
  timeZone: string = userTimeZone,
): string {
  if (timestamp == null) {
    return '-'
  }
  const value = safeWireNumber(timestamp)
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(new Date(value))
  const values = Object.fromEntries(parts.map((part) => [part.type, part.value]))
  return `${values.year}-${values.month}-${values.day} ${values.hour}:${values.minute}:${values.second}`
}
