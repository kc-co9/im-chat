import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import LoginView from '../src/views/LoginView.vue'

describe('IAM login page', () => {
  beforeEach(() => {
    document.cookie = 'XSRF-TOKEN=iam%20csrf; path=/'
  })

  it('submits the standard Spring Security form and restores the inherited hash route', () => {
    window.history.replaceState({}, '', '/login#/permissions/administrators')

    const wrapper = mount(LoginView)
    const form = wrapper.get('form')

    expect(form.attributes('method')).toBe('post')
    expect(form.attributes('action')).toBe('/login?continue=%2F%23%2Fpermissions%2Fadministrators')
    expect(wrapper.get('input[name="username"]').attributes('autocomplete')).toBe('username')
    expect(wrapper.get('input[name="password"]').attributes('autocomplete')).toBe(
      'current-password',
    )
    expect(wrapper.get('input[name="_csrf"]').attributes('value')).toBe('iam csrf')
  })

  it('shows authentication failure and preserves an explicit continuation', () => {
    window.history.replaceState(
      {},
      '',
      '/login?error=true&continue=%2F%23%2Fpermissions%2Fapplications',
    )

    const wrapper = mount(LoginView)

    expect(wrapper.text()).toContain('邮箱或密码错误')
    expect(wrapper.get('form').attributes('action')).toBe(
      '/login?continue=%2F%23%2Fpermissions%2Fapplications',
    )
  })

  it('leaves OAuth saved-request restoration to Spring when no route is supplied', () => {
    window.history.replaceState({}, '', '/login')

    const wrapper = mount(LoginView)

    expect(wrapper.get('form').attributes('action')).toBe('/login')
  })

  it('ignores an inherited root hash so OAuth saved-request restoration can continue', () => {
    window.history.replaceState({}, '', '/login#/')

    const wrapper = mount(LoginView)

    expect(wrapper.get('form').attributes('action')).toBe('/login')
  })
})
