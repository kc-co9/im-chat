<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElButton, ElPagination, ElTable, ElTableColumn } from 'element-plus'
import AppDrawer from '../components/AppDrawer.vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import DataState from '../components/DataState.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { iamApi, type Administrator, type IamRole } from '../api/iam'
import { can } from '../state/auth'
import { notify } from '../state/feedback'
import { safeWireNumber } from '../support/wire'

type AdministratorAction = {
  type: 'disable' | 'enable' | 'delete' | 'revoke'
  administrator: Administrator
}

const records = ref<Administrator[]>([])
const total = ref(0)
const loading = ref(false)
const errorMessage = ref('')
const paging = reactive({ pageNo: 1, pageSize: 20 })
const pendingAdministratorId = ref('')

const passwordAdministrator = ref<Administrator>()
const password = ref('')
const passwordPending = ref(false)
const passwordError = ref('')

const roleAdministrator = ref<Administrator>()
const iamRoles = ref<IamRole[]>([])
const selectedRoleIds = ref<string[]>([])
const initialRoleIds = ref<string[]>([])
const roleLoading = ref(false)
const rolePending = ref(false)
const roleError = ref('')

const confirmAction = ref<AdministratorAction>()

const passwordDirty = computed(() => Boolean(password.value))
const roleDirty = computed(
  () =>
    JSON.stringify([...selectedRoleIds.value].sort()) !==
    JSON.stringify([...initialRoleIds.value].sort()),
)

async function load(pageNo = paging.pageNo) {
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await iamApi.administrators({ pageNo, pageSize: paging.pageSize })
    records.value = response.data.records
    total.value = safeWireNumber(response.data.total)
    paging.pageNo = pageNo
    return true
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '管理账号加载失败'
    return false
  } finally {
    loading.value = false
  }
}

function openPassword(administrator: Administrator) {
  passwordAdministrator.value = administrator
  password.value = ''
  passwordError.value = ''
}

async function resetPassword() {
  const administrator = passwordAdministrator.value
  if (!administrator || passwordPending.value) return
  passwordPending.value = true
  passwordError.value = ''
  try {
    await iamApi.resetAdministratorPassword(administrator.id, password.value)
    passwordAdministrator.value = undefined
    notify('管理员密码重置成功')
  } catch (error) {
    passwordError.value = error instanceof Error ? error.message : '管理员密码重置失败'
  } finally {
    passwordPending.value = false
  }
}

async function openRoles(administrator: Administrator) {
  roleAdministrator.value = administrator
  iamRoles.value = []
  selectedRoleIds.value = []
  initialRoleIds.value = []
  roleError.value = ''
  roleLoading.value = true
  try {
    const [rolesResponse, assignmentResponse] = await Promise.all([
      iamApi.iamRoles(),
      iamApi.administratorIamRoles(administrator.id),
    ])
    if (roleAdministrator.value?.id !== administrator.id) return
    iamRoles.value = rolesResponse.data
    selectedRoleIds.value = [...assignmentResponse.data.roleIds]
    initialRoleIds.value = [...assignmentResponse.data.roleIds]
  } catch (error) {
    roleError.value = error instanceof Error ? error.message : 'IAM 内部角色加载失败'
  } finally {
    roleLoading.value = false
  }
}

async function replaceRoles() {
  const administrator = roleAdministrator.value
  if (!administrator || rolePending.value) return
  rolePending.value = true
  roleError.value = ''
  try {
    await iamApi.changeAdministratorRoles(administrator.id, [...selectedRoleIds.value])
    roleAdministrator.value = undefined
    notify('管理员内部角色已更新')
  } catch (error) {
    roleError.value = error instanceof Error ? error.message : '管理员内部角色更新失败'
  } finally {
    rolePending.value = false
  }
}

function requestAction(type: AdministratorAction['type'], administrator: Administrator) {
  confirmAction.value = { type, administrator }
}

function administratorFrom(row: unknown): Administrator {
  return row as Administrator
}

function actionPresentation(action: AdministratorAction | undefined) {
  if (!action) return { title: '', message: '', label: '' }
  const name = action.administrator.username
  if (action.type === 'disable') {
    return {
      title: '确认停用管理员',
      message: `停用“${name}”并撤销其全部在线授权？`,
      label: '确认停用',
    }
  }
  if (action.type === 'enable') {
    return { title: '确认启用管理员', message: `恢复“${name}”的登录资格？`, label: '确认启用' }
  }
  if (action.type === 'revoke') {
    return { title: '确认下线会话', message: `撤销“${name}”的全部 OAuth 会话？`, label: '确认下线' }
  }
  return {
    title: '确认删除管理员',
    message: `删除“${name}”并撤销其全部授权？该操作不可撤销。`,
    label: '确认删除',
  }
}

async function confirmAdministratorAction() {
  const action = confirmAction.value
  if (!action || pendingAdministratorId.value) return
  pendingAdministratorId.value = action.administrator.id
  try {
    if (action.type === 'disable') await iamApi.disableAdministrator(action.administrator.id)
    if (action.type === 'enable') await iamApi.enableAdministrator(action.administrator.id)
    if (action.type === 'delete') await iamApi.deleteAdministrator(action.administrator.id)
    if (action.type === 'revoke') await iamApi.revokeAdministratorSessions(action.administrator.id)
    confirmAction.value = undefined
    notify(action.type === 'revoke' ? '管理员会话已下线' : '管理员状态已更新')
    await load()
  } finally {
    pendingAdministratorId.value = ''
  }
}

onMounted(() => load())
</script>

<template>
  <section class="console-page">
    <header class="console-page-header">
      <div>
        <p class="eyebrow">权限管理</p>
        <h2>管理账号</h2>
        <p>管理 IAM 登录身份、内部角色和在线授权</p>
      </div>
      <ElButton
        data-testid="refresh-administrators"
        :icon="Refresh"
        :disabled="loading"
        @click="load()"
        >刷新</ElButton
      >
    </header>

    <DataState
      :loading="loading"
      :error="errorMessage"
      :empty="!loading && !records.length"
      :has-data="Boolean(records.length)"
      empty-text="暂无管理账号"
      @retry="load()"
    >
      <ElTable :data="records" border>
        <ElTableColumn label="管理员" min-width="160"
          ><template #default="{ row }"
            ><strong>{{ row.username }}</strong
            ><small>#{{ row.id }}</small></template
          ></ElTableColumn
        >
        <ElTableColumn prop="email" label="邮箱" min-width="220" />
        <ElTableColumn label="状态" width="110"
          ><template #default="{ row }"><StatusBadge :status="row.status" /></template
        ></ElTableColumn>
        <ElTableColumn label="操作" min-width="290" fixed="right"
          ><template #default="{ row: administrator }">
            <div class="row-actions">
              <ElButton
                v-if="can('iam:role:write')"
                :data-testid="`assign-roles-${administrator.id}`"
                link
                type="primary"
                @click="openRoles(administratorFrom(administrator))"
                >分配角色</ElButton
              >
              <details class="action-menu">
                <summary>更多</summary>
                <div class="action-menu-popover">
                  <ElButton
                    v-if="can('iam:administrator:write')"
                    :data-testid="`reset-password-${administrator.id}`"
                    link
                    @click="openPassword(administratorFrom(administrator))"
                    >重置密码</ElButton
                  >
                  <ElButton
                    v-if="can('iam:administrator:write') && administrator.status === 'ACTIVE'"
                    :data-testid="`disable-${administrator.id}`"
                    link
                    @click="requestAction('disable', administratorFrom(administrator))"
                    >停用</ElButton
                  >
                  <ElButton
                    v-if="can('iam:administrator:write') && administrator.status === 'DISABLED'"
                    link
                    @click="requestAction('enable', administratorFrom(administrator))"
                    >启用</ElButton
                  >
                  <ElButton
                    v-if="can('iam:session:revoke')"
                    :data-testid="`revoke-sessions-${administrator.id}`"
                    link
                    @click="requestAction('revoke', administratorFrom(administrator))"
                    >下线会话</ElButton
                  >
                  <ElButton
                    v-if="can('iam:administrator:write')"
                    :data-testid="`delete-${administrator.id}`"
                    link
                    type="danger"
                    @click="requestAction('delete', administratorFrom(administrator))"
                    >删除</ElButton
                  >
                </div>
              </details>
            </div>
          </template></ElTableColumn
        >
      </ElTable>
    </DataState>

    <ElPagination
      v-if="records.length"
      v-model:current-page="paging.pageNo"
      v-model:page-size="paging.pageSize"
      :total="total"
      layout="total, sizes, prev, pager, next"
      @change="load()"
    />
  </section>

  <AppDrawer
    :open="Boolean(passwordAdministrator)"
    title="重置管理员密码"
    :description="passwordAdministrator?.username"
    :dirty="passwordDirty"
    :pending="passwordPending"
    @close="passwordAdministrator = undefined"
  >
    <form
      id="password-form"
      data-testid="password-form"
      class="drawer-form"
      @submit.prevent="resetPassword"
    >
      <p class="field-help warning-help">保存后将撤销该管理员的现有授权。</p>
      <p v-if="passwordError" class="form-error">{{ passwordError }}</p>
      <label
        >新密码<input
          v-model="password"
          data-testid="new-password"
          type="password"
          minlength="8"
          required
      /></label>
    </form>
    <template #footer="{ close }"
      ><ElButton @click="close">取消</ElButton
      ><ElButton type="primary" :loading="passwordPending" @click="resetPassword"
        >确认重置</ElButton
      ></template
    >
  </AppDrawer>

  <AppDrawer
    :open="Boolean(roleAdministrator)"
    title="分配 IAM 内部角色"
    :description="roleAdministrator?.username"
    :dirty="roleDirty"
    :pending="rolePending"
    @close="roleAdministrator = undefined"
  >
    <form
      id="administrator-role-form"
      data-testid="administrator-role-form"
      class="drawer-form"
      @submit.prevent="replaceRoles"
    >
      <p class="field-help">保存后会以当前选择替换该管理员的 IAM 内部角色。</p>
      <p v-if="roleError" class="form-error">{{ roleError }}</p>
      <div v-if="roleLoading" class="inline-loading">正在加载角色…</div>
      <fieldset v-else>
        <legend>可分配角色</legend>
        <label v-for="role in iamRoles" :key="role.id" class="check-row"
          ><input
            v-model="selectedRoleIds"
            :data-testid="`iam-role-${role.id}`"
            type="checkbox"
            :value="role.id"
            :disabled="role.status !== 'ACTIVE'"
          /><span
            >{{ role.name }}<small>{{ role.code }}</small></span
          ></label
        >
        <p v-if="!iamRoles.length" class="field-help">暂无可分配的 IAM 内部角色</p>
      </fieldset>
    </form>
    <template #footer="{ close }"
      ><ElButton @click="close">取消</ElButton
      ><ElButton
        type="primary"
        :disabled="rolePending || roleLoading || Boolean(roleError)"
        :loading="rolePending"
        @click="replaceRoles"
      >
        保存角色
      </ElButton></template
    >
  </AppDrawer>

  <ConfirmDialog
    :open="Boolean(confirmAction)"
    :title="actionPresentation(confirmAction).title"
    :message="actionPresentation(confirmAction).message"
    :confirm-label="actionPresentation(confirmAction).label"
    :danger="confirmAction?.type !== 'enable'"
    :pending="Boolean(pendingAdministratorId)"
    @cancel="confirmAction = undefined"
    @confirm="confirmAdministratorAction"
  />
</template>
