import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import AppDrawer from '../src/components/AppDrawer.vue'
import ConfirmDialog from '../src/components/ConfirmDialog.vue'
import DataState from '../src/components/DataState.vue'
import StatusBadge from '../src/components/StatusBadge.vue'
import ToastRegion from '../src/components/ToastRegion.vue'
import { clearToasts, notify, toasts } from '../src/state/feedback'

describe('IAM interaction components', () => {
  beforeEach(() => {
    clearToasts()
  })

  afterEach(() => {
    document.body.innerHTML = ''
    vi.restoreAllMocks()
  })

  it('moves focus into an open drawer and restores it after close', async () => {
    const trigger = document.createElement('button')
    document.body.appendChild(trigger)
    trigger.focus()
    const wrapper = mount(AppDrawer, {
      attachTo: document.body,
      props: { open: false, title: '注册客户端' },
      slots: { default: '<input data-testid="first-field" />' },
    })

    await wrapper.setProps({ open: true })
    await flushPromises()

    expect(wrapper.get('.el-drawer').element.contains(document.activeElement)).toBe(true)
    await wrapper.setProps({ open: false })
    await flushPromises()
    await new Promise((resolve) => window.setTimeout(resolve))
    expect(document.activeElement).toBe(trigger)
  })

  it('guards dirty drawer close and locks close while pending', async () => {
    const wrapper = mount(AppDrawer, {
      props: { open: true, title: '编辑', dirty: true, pending: false },
    })

    await wrapper.get('[data-testid="drawer-close"]').trigger('click')
    expect(wrapper.get('[role="alertdialog"]').text()).toContain('放弃未保存的修改')
    expect(wrapper.emitted('close')).toBeUndefined()

    await wrapper.get('[data-testid="confirm-cancel"]').trigger('click')
    expect(wrapper.emitted('close')).toBeUndefined()

    await wrapper.get('[data-testid="drawer-close"]').trigger('click')
    await wrapper.get('[data-testid="confirm-action"]').trigger('click')
    expect(wrapper.emitted('close')).toHaveLength(1)

    await wrapper.setProps({ pending: true })
    await wrapper.get('[data-testid="drawer-close"]').trigger('click')
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('requires explicit confirmation for destructive actions', async () => {
    const wrapper = mount(ConfirmDialog, {
      attachTo: document.body,
      props: {
        open: true,
        title: '停用客户端',
        message: '停用 im-audit-client 后将无法继续访问。',
        confirmLabel: '确认停用',
        danger: true,
      },
    })

    await flushPromises()

    expect(document.body.textContent).toContain('im-audit-client')
    expect(document.body.querySelector('[data-testid="confirm-action"]')?.classList).toContain(
      'danger',
    )
    await wrapper.get('[data-testid="confirm-cancel"]').trigger('click')
    expect(wrapper.emitted('cancel')).toHaveLength(1)
    await wrapper.get('[data-testid="confirm-action"]').trigger('click')
    expect(wrapper.emitted('confirm')).toHaveLength(1)
  })

  it('moves focus into a confirmation and restores the trigger', async () => {
    const trigger = document.createElement('button')
    document.body.appendChild(trigger)
    trigger.focus()
    const wrapper = mount(ConfirmDialog, {
      attachTo: document.body,
      props: { open: false, title: '确认操作', message: '确认继续吗？' },
    })

    await wrapper.setProps({ open: true })
    await flushPromises()
    expect(wrapper.get('.el-dialog').element.contains(document.activeElement)).toBe(true)

    await wrapper.setProps({ open: false })
    await flushPromises()
    await new Promise((resolve) => window.setTimeout(resolve))
    expect(document.activeElement).toBe(trigger)
  })

  it('preserves prior records when refresh fails and exposes retry', async () => {
    const wrapper = mount(DataState, {
      props: {
        loading: false,
        error: '刷新失败',
        empty: false,
        hasData: true,
        emptyText: '暂无记录',
      },
      slots: { default: '<div data-testid="records">existing rows</div>' },
    })

    expect(wrapper.find('[data-testid="records"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('刷新失败')
    await wrapper.get('[data-testid="retry"]').trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)
  })

  it('renders loading and contextual empty states', async () => {
    const wrapper = mount(DataState, {
      props: {
        loading: true,
        error: '',
        empty: false,
        hasData: false,
        emptyText: '暂无客户端',
      },
    })
    expect(wrapper.find('[aria-busy="true"]').exists()).toBe(true)

    await wrapper.setProps({ loading: false, empty: true })
    expect(wrapper.text()).toContain('暂无客户端')
  })

  it('always renders status text in addition to color', () => {
    const active = mount(StatusBadge, { props: { status: 'ACTIVE' } })
    const disabled = mount(StatusBadge, { props: { status: 'DISABLED' } })

    expect(active.text()).toContain('正常')
    expect(disabled.text()).toContain('已停用')
  })

  it('announces success feedback through a polite live region', async () => {
    const wrapper = mount(ToastRegion)

    notify('客户端注册成功')
    await flushPromises()

    expect(toasts).toHaveLength(1)
    expect(wrapper.get('[aria-live="polite"]').text()).toContain('客户端注册成功')
  })
})
