import { describe, expect, it } from 'vitest'
import { router } from '../src/router'

describe('admin router', () => {
  it('uses hash history so production deep links do not require server fallback', () => {
    expect(router.options.history.createHref('/users')).toContain('#')
  })
})
