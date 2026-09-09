import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '../src/api/audit'
import { auth } from '../src/state/auth'
import AuditsView from '../src/views/AuditsView.vue'
import { formatTimestamp, userTimeZone } from '../src/support/dateTime'

vi.mock('../src/api/audit', () => ({
  api: {
    audits: vi.fn(),
    audit: vi.fn(),
    exportAudits: vi.fn(),
  },
}))

describe('central audit workspace', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    URL.createObjectURL = vi.fn(() => 'blob:audit-export')
    URL.revokeObjectURL = vi.fn()
    HTMLAnchorElement.prototype.click = vi.fn()
    auth.principal = {
      administratorId: '1',
      username: 'auditor',
      appKey: 'imAudit',
      authorities: ['audit:read'],
    }
    vi.mocked(api.audits).mockResolvedValue({
      data: {
        paging: { pageNo: 1, pageSize: 20 },
        records: [
          {
            auditId: 'audit-1',
            sourceApp: 'imAdmin',
            type: 'BUSINESS',
            action: 'USER_UPDATE',
            actorId: '1001',
            actorName: 'admin',
            targetType: 'USER',
            targetId: '2001',
            outcome: 'SUCCESS',
            errorCode: null,
            description: '修改用户',
            occurredAt: String(Date.parse('2026-08-28T04:00:00Z')),
          },
        ],
        total: '1',
      },
    } as never)
  })

  it('shows business/security views and read-only states', async () => {
    const wrapper = mount(AuditsView, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(wrapper.text()).toContain('业务审计')
    expect(wrapper.text()).toContain('安全审计')
    expect(wrapper.text()).toContain('USER_UPDATE')
    expect(wrapper.text()).toContain(
      formatTimestamp(String(Date.parse('2026-08-28T04:00:00Z')), userTimeZone),
    )
    const request = vi.mocked(api.audits).mock.calls[0][0]
    expect(typeof request.occurredFrom).toBe('number')
    expect(typeof request.occurredTo).toBe('number')
    expect(request).not.toHaveProperty('sourceApp')
    expect(request).not.toHaveProperty('action')
    expect(request).not.toHaveProperty('actorId')
    expect(wrapper.text()).not.toContain('删除')
    expect(wrapper.text()).not.toContain('重放')
    expect(wrapper.text()).not.toContain('导出')
  })

  it('toggles advanced filters and retains stale rows after refresh failure', async () => {
    const wrapper = mount(AuditsView, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    expect(wrapper.find('.advanced-filters').attributes('style')).toContain('display: none')

    const filterButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('高级筛选'))!
    await filterButton.trigger('click')
    expect(wrapper.find('.advanced-filters').attributes('style')).not.toContain('display: none')

    vi.mocked(api.audits).mockRejectedValueOnce(new Error('刷新失败'))
    const refreshButton = wrapper
      .findAll('button')
      .find((button) => button.attributes('title') === '刷新')!
    await refreshButton.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('刷新失败')
    expect(wrapper.text()).toContain('USER_UPDATE')
  })

  it('shows export only with the dedicated permission', async () => {
    auth.principal!.authorities.push('audit:export')
    vi.mocked(api.exportAudits).mockResolvedValue({ data: new Blob() } as never)
    const wrapper = mount(AuditsView, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    const exportButton = wrapper.findAll('button').find((button) => button.text().includes('导出'))
    expect(exportButton).toBeDefined()
    await exportButton!.trigger('click')
    await flushPromises()
    expect(api.exportAudits).toHaveBeenCalledWith(
      expect.objectContaining({ timeZone: userTimeZone }),
    )
  })
})
