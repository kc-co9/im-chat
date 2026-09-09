import { describe, expect, it } from 'vitest'
import { formatTimestamp, safeWireNumber } from '../src/support/wire'

describe('admin wire values', () => {
  it('formats string epoch milliseconds in the requested time zone', () => {
    expect(formatTimestamp('1787889600000', 'Asia/Shanghai')).toBe('2026-08-28 12:00:00')
  })

  it('rejects unsafe numeric conversions', () => {
    expect(safeWireNumber('20')).toBe(20)
    expect(() => safeWireNumber('90071992547409930')).toThrow('safe integer')
  })
})
