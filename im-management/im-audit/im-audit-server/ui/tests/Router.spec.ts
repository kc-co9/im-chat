import { describe, expect, it } from 'vitest'
import { router } from '../src/router'

describe('audit router', () => {
  it('uses hash history so production deep links do not require server fallback', () => {
    expect(router.options.history.createHref('/audits')).toContain('#')
  })
})
