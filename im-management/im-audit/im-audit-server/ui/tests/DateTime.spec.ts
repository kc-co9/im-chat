import { describe, expect, it } from 'vitest'
import { formatTimestamp } from '../src/support/dateTime'

describe('audit date-time presentation', () => {
  it('formats an epoch timestamp in the requested user time zone', () => {
    const timestamp = String(Date.parse('2026-08-28T04:00:00Z'))

    expect(formatTimestamp(timestamp, 'Asia/Shanghai')).toBe('2026-08-28 12:00:00')
  })
})
