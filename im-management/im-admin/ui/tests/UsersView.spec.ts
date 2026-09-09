import ElementPlus from 'element-plus'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { api } from '../src/api/admin'
import { auth } from '../src/state/auth'
import UsersView from '../src/views/UsersView.vue'

vi.mock('../src/api/admin', () => ({
  api: {
    users: vi.fn(),
    user: vi.fn(),
    updateUser: vi.fn(),
    userCommand: vi.fn(),
  },
}))

describe('ordinary-user management', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    auth.principal = {
      administratorId: '1',
      username: 'admin',
      appKey: 'imAdmin',
      authorities: ['user:read', 'user:update', 'user:password:reset', 'user:ban', 'user:delete'],
    }
    vi.mocked(api.users).mockResolvedValueOnce({
      data: {
        paging: {
          pageNo: 1,
          pageSize: 20,
        },
        records: [
          {
            id: '6',
            username: 'active-user',
            email: 'active@example.com',
            status: 'NORMAL',
            deleted: false,
            createdAt: '1787529600000',
            updatedAt: '1787533200000',
          },
          {
            id: '7',
            username: 'deleted-user',
            email: 'deleted@example.com',
            status: 'NORMAL',
            deleted: true,
            createdAt: '1787529600000',
            updatedAt: '1787533200000',
          },
        ],
        total: '2',
      },
    } as never)
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('shows deleted users in the unified list without deleted-list controls', async () => {
    const wrapper = mount(UsersView, { global: { plugins: [ElementPlus] } })
    await flushPromises()

    expect(wrapper.text()).toContain('active-user')
    expect(wrapper.text()).toContain('deleted-user')
    expect(wrapper.text()).toContain('已删除')
    expect(api.users).toHaveBeenNthCalledWith(1, expect.objectContaining({ status: '' }))
    expect(wrapper.text()).not.toContain('查看已删除用户')
    expect(wrapper.text()).not.toContain('返回用户列表')

    const deletedRow = wrapper
      .findAll('.el-table__row')
      .find((row) => row.text().includes('deleted-user'))
    expect(deletedRow).toBeDefined()
    for (const label of ['编辑', '重置密码', '封禁', '解封', '删除']) {
      expect(deletedRow!.findAll('button').some((button) => button.text() === label)).toBe(false)
    }
    const activeRow = wrapper
      .findAll('.el-table__row')
      .find((row) => row.text().includes('active-user'))!
    expect(
      activeRow
        .findAll('button')
        .filter((button) => button.text().trim())
        .map((button) => button.text().trim()),
    ).toEqual(['详情'])
    expect(activeRow.find('[aria-label="更多操作"]').exists()).toBe(true)
  })

  it('retains current records and exposes retry when refresh fails', async () => {
    const wrapper = mount(UsersView, { global: { plugins: [ElementPlus] } })
    await flushPromises()
    vi.mocked(api.users).mockRejectedValueOnce(new Error('刷新失败'))

    await wrapper.get('button[title="刷新"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('刷新失败')
    expect(wrapper.text()).toContain('active-user')
  })

  it('retains edited values in the drawer when saving fails', async () => {
    vi.mocked(api.updateUser).mockRejectedValueOnce(new Error('邮箱已被占用'))
    const wrapper = mount(UsersView, {
      attachTo: document.body,
      global: { plugins: [ElementPlus] },
    })
    await flushPromises()

    await wrapper.get('[aria-label="更多操作"]').trigger('click')
    await flushPromises()
    const editItem = document.querySelector<HTMLElement>('[data-testid="edit-user"]')!
    editItem.click()
    await flushPromises()
    const username = wrapper.get<HTMLInputElement>('.el-drawer input')
    await username.setValue('active-user-updated')
    await wrapper.get('[data-testid="save-user"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('邮箱已被占用')
    expect(username.element.value).toBe('active-user-updated')
    expect(wrapper.find('.el-drawer').exists()).toBe(true)
  })
})
