<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { MoreFilled, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, type User } from '../api/admin'
import { can } from '../state/auth'
import { formatTimestamp, safeWireNumber } from '../support/wire'

const loading = ref(false)
const errorMessage = ref('')
const users = ref<User[]>([])
const total = ref(0)
const editVisible = ref(false)
const detailVisible = ref(false)
const detail = ref<User>()
const edit = reactive({ userId: '', username: '', email: '' })
const editInitial = ref('')
const editError = ref('')
const editPending = ref(false)
const query = reactive({
  pageNo: 1,
  pageSize: 20,
  userId: undefined as string | undefined,
  username: '',
  email: '',
  status: '',
})

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await api.users(query)
    const { data } = response
    users.value = data.records
    total.value = safeWireNumber(data.total)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '用户列表加载失败'
  } finally {
    loading.value = false
  }
}

async function search() {
  query.pageNo = 1
  await load()
}

async function command(action: string, user: User, label: string) {
  let body: Record<string, unknown> = { userId: user.id }
  if (action === 'reset-password') {
    const { value } = await ElMessageBox.prompt(
      `为用户“${user.username}”设置新密码，保存后其现有会话将失效。`,
      '重置密码',
      { inputType: 'password', inputPattern: /.{8,}/, inputErrorMessage: '密码至少 8 位' },
    )
    body = { ...body, password: value }
  } else {
    await ElMessageBox.confirm(
      `确认${label}用户“${user.username}”？该操作将影响其登录状态。`,
      label,
      { type: 'warning' },
    )
  }
  await api.userCommand(action, body)
  ElMessage.success(`${label}成功`)
  await load()
}

function openEdit(user: User) {
  Object.assign(edit, { userId: user.id, username: user.username, email: user.email })
  editInitial.value = JSON.stringify(edit)
  editError.value = ''
  editVisible.value = true
}

function closeEdit(done: () => void) {
  if (JSON.stringify(edit) === editInitial.value) {
    done()
    return
  }
  ElMessageBox.confirm('关闭后，本次修改将不会保存。', '放弃未保存的修改？', {
    confirmButtonText: '放弃修改',
    cancelButtonText: '继续编辑',
    type: 'warning',
  })
    .then(done)
    .catch(() => undefined)
}

async function saveEdit() {
  if (editPending.value) return
  editPending.value = true
  editError.value = ''
  try {
    await api.updateUser(edit)
    editVisible.value = false
    ElMessage.success('用户资料已更新')
    await load()
  } catch (error) {
    editError.value = error instanceof Error ? error.message : '用户资料更新失败'
  } finally {
    editPending.value = false
  }
}

async function openDetail(user: User) {
  detail.value = (await api.user(user.id)).data
  detailVisible.value = true
}

function tag(user: User) {
  if (user.deleted) return 'info'
  return user.status === 'NORMAL' ? 'success' : 'warning'
}

function handleRowCommand(action: string, user: User) {
  if (action === 'edit') {
    openEdit(user)
    return
  }
  if (action === 'reset-password') return command(action, user, '重置密码')
  if (action === 'ban') return command(action, user, '封禁')
  if (action === 'unban') return command(action, user, '解封')
  if (action === 'delete') return command(action, user, '删除')
}

onMounted(load)
</script>

<template>
  <section class="console-page">
    <header class="console-page-header">
      <div>
        <h1>用户管理</h1>
        <p>查询与维护普通 IM 用户账号</p>
      </div>
      <div>
        <el-button :icon="Refresh" circle title="刷新" @click="load" />
      </div>
    </header>

    <div class="toolbar">
      <el-input v-model="query.userId" placeholder="用户 ID" clearable />
      <el-input v-model="query.username" placeholder="用户名" clearable />
      <el-input v-model="query.email" placeholder="邮箱" clearable />
      <el-select v-model="query.status" placeholder="全部状态" clearable>
        <el-option label="正常" value="NORMAL" />
        <el-option label="已封禁" value="BANNED" />
      </el-select>
      <el-button type="primary" :icon="Search" @click="search"> 查询 </el-button>
    </div>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false">
      <template #default><el-button link @click="load">重试</el-button></template>
    </el-alert>

    <el-table :data="users" v-loading="loading" empty-text="暂无用户">
      <el-table-column prop="id" label="用户 ID" width="130" />
      <el-table-column prop="username" label="用户名" />
      <el-table-column prop="email" label="邮箱" min-width="210" />
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="tag(row)">{{ row.deleted ? '已删除' : row.status }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="注册时间" min-width="180">
        <template #default="{ row }">{{ formatTimestamp(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }">
          <el-button link @click="openDetail(row)">详情</el-button>
          <el-dropdown v-if="!row.deleted" trigger="click" @command="handleRowCommand($event, row)">
            <el-button :icon="MoreFilled" link aria-label="更多操作" />
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-if="can('user:update')" data-testid="edit-user" command="edit"
                  >编辑</el-dropdown-item
                >
                <el-dropdown-item v-if="can('user:password:reset')" command="reset-password"
                  >重置密码</el-dropdown-item
                >
                <el-dropdown-item v-if="can('user:ban') && row.status === 'NORMAL'" command="ban"
                  >封禁</el-dropdown-item
                >
                <el-dropdown-item v-if="can('user:ban') && row.status === 'BANNED'" command="unban"
                  >解封</el-dropdown-item
                >
                <el-dropdown-item v-if="can('user:delete')" command="delete" divided
                  >删除</el-dropdown-item
                >
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="query.pageNo"
      v-model:page-size="query.pageSize"
      :total="total"
      layout="total, sizes, prev, pager, next"
      @change="load"
    />

    <el-drawer
      v-model="editVisible"
      title="编辑用户资料"
      size="min(480px, 100vw)"
      :before-close="closeEdit"
    >
      <el-form label-position="top">
        <el-alert v-if="editError" :title="editError" type="error" show-icon :closable="false" />
        <el-form-item label="用户名"><el-input v-model="edit.username" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="edit.email" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button data-testid="save-user" type="primary" :loading="editPending" @click="saveEdit"
          >保存</el-button
        >
      </template>
    </el-drawer>

    <el-drawer v-model="detailVisible" title="用户详情" size="420">
      <el-descriptions v-if="detail" :column="1" border>
        <el-descriptions-item label="用户 ID">{{ detail.id }}</el-descriptions-item>
        <el-descriptions-item label="用户名">{{ detail.username }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ detail.email }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{
          detail.deleted ? '已删除' : detail.status
        }}</el-descriptions-item>
        <el-descriptions-item label="注册时间">{{
          formatTimestamp(detail.createdAt)
        }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{
          formatTimestamp(detail.updatedAt)
        }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </section>
</template>
