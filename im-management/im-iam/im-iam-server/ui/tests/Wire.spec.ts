import { describe, expect, it } from 'vitest'
import { formatTimestamp, safeWireNumber } from '../src/support/wire'

describe('IAM wire values', () => {
  it('converts bounded counts without converting business identifiers', () => {
    expect(safeWireNumber('20')).toBe(20)
    expect(() => safeWireNumber('90071992547409930')).toThrow('safe integer')
  })

  it('formats epoch milliseconds in the requested IANA time zone', () => {
    expect(formatTimestamp('1787889600000', 'Asia/Shanghai')).toBe('2026-08-28 12:00:00')
  })
})
