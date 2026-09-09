<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import { ElButton, ElInput, ElTable, ElTableColumn } from 'element-plus'
import AppDrawer from '../components/AppDrawer.vue'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import DataState from '../components/DataState.vue'
import StatusBadge from '../components/StatusBadge.vue'
import {
  iamApi,
  type Application,
  type ApplicationPermission,
  type ApplicationRole,
  type Administrator,
  type OAuthClient,
} from '../api/iam'
import { can } from '../state/auth'
import { notify } from '../state/feedback'
import { safeWireNumber } from '../support/wire'

type DetailTab = 'clients' | 'roles' | 'members' | 'permissions'
type ConfirmAction =
  | { type: 'rotate'; client: OAuthClient }
  | { type: 'disable'; client: OAuthClient }

function clientFrom(row: unknown): OAuthClient {
  return row as OAuthClient
}

function roleFrom(row: unknown): ApplicationRole {
  return row as ApplicationRole
}

function administratorFrom(row: unknown): Administrator {
  return row as Administrator
}

const route = useRoute()
const router = useRouter()
const appId = computed(() => String(route.params.appId ?? ''))
const application = ref<Application>()
const applications = ref<Application[]>([])
const pageError = ref('')
const pageLoading = ref(false)
const activeTab = ref<DetailTab>(tabFrom(route.query.tab))

const clients = ref<OAuthClient[]>([])
const clientTotal = ref(0)
const clientPaging = reactive({ pageNo: 1, pageSize: 20 })
const clientsLoading = ref(false)
const clientsError = ref('')

const roles = ref<ApplicationRole[]>([])
const roleTotal = ref(0)
const rolePaging = reactive({ pageNo: 1, pageSize: 20 })
const rolesLoading = ref(false)
const rolesError = ref('')

const members = ref<Administrator[]>([])
const memberTotal = ref(0)
const memberPaging = reactive({ pageNo: 1, pageSize: 20 })
const membersLoading = ref(false)
const membersError = ref('')

const permissions = ref<ApplicationPermission[]>([])
const permissionTotal = ref(0)
const permissionPaging = reactive({ pageNo: 1, pageSize: 20 })
const permissionKeyword = ref('')
const permissionsLoading = ref(false)
const permissionsError = ref('')

const clientDrawerOpen = ref(false)
const clientPending = ref(false)
const clientFormError = ref('')
const clientForm = reactive({
  type: 'browser' as 'browser' | 'machine',
  name: '',
  clientId: '',
  clientSecret: '',
  audienceAppId: '',
  scopes: 'openid, profile',
  redirectUri: '',
  logoutUri: '',
})

const rotationClient = ref<OAuthClient>()
const rotationSecret = ref('')
const rotationPending = ref(false)
const rotationError = ref('')
const confirmAction = ref<ConfirmAction>()

const accessClient = ref<OAuthClient>()
const accessPending = ref(false)
const accessError = ref('')
const accessForm = reactive({ scopes: '', redirectUris: '', postLogoutRedirectUris: '' })

const roleDrawerOpen = ref(false)
const editingRole = ref<ApplicationRole>()
const rolePending = ref(false)
const roleFormError = ref('')
const selectedPermissionIds = ref<string[]>([])
const roleForm = reactive({ code: '', name: '' })

const assignmentAdministrator = ref<Administrator>()
const assignedApplicationRoleIds = ref<string[]>([])
const assignmentLoading = ref(false)
const assignmentPending = ref(false)
const assignmentError = ref('')

const clientFormDirty = computed(() =>
  Boolean(
    clientForm.name ||
      clientForm.clientId ||
      clientForm.clientSecret ||
      clientForm.redirectUri ||
      clientForm.logoutUri,
  ),
)
const roleFormDirty = computed(() =>
  Boolean(roleForm.code || roleForm.name || selectedPermissionIds.value.length),
)

function tabFrom(value: unknown): DetailTab {
  return value === 'roles' || value === 'members' || value === 'permissions' ? value : 'clients'
}

function message(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback
}

function grantType(client: OAuthClient) {
  return client.grantTypes.includes('CLIENT_CREDENTIALS') ? '机器' : '浏览器'
}

async function loadApplication() {
  pageLoading.value = true
  pageError.value = ''
  try {
    const response = await iamApi.application(appId.value)
    application.value = response.data
  } catch (error) {
    pageError.value = message(error, '应用信息加载失败')
  } finally {
    pageLoading.value = false
  }
}

async function loadApplications() {
  try {
    const response = await iamApi.applications({ pageNo: 1, pageSize: 100 })
    applications.value = response.data.records
  } catch {
    applications.value = application.value ? [application.value] : []
  }
}

async function loadClients(pageNo = clientPaging.pageNo) {
  clientsLoading.value = true
  clientsError.value = ''
  try {
    const response = await iamApi.oauthClients(appId.value, {
      pageNo,
      pageSize: clientPaging.pageSize,
    })
    clients.value = response.data.records
    clientTotal.value = safeWireNumber(response.data.total)
    clientPaging.pageNo = pageNo
  } catch (error) {
    clientsError.value = message(error, 'OAuth 客户端列表加载失败')
  } finally {
    clientsLoading.value = false
  }
}

async function loadRoles(pageNo = rolePaging.pageNo) {
  if (!can('iam:role:read')) return
  rolesLoading.value = true
  rolesError.value = ''
  try {
    const response = await iamApi.roles(appId.value, { pageNo, pageSize: rolePaging.pageSize })
    roles.value = response.data.records
    roleTotal.value = safeWireNumber(response.data.total)
    rolePaging.pageNo = pageNo
  } catch (error) {
    rolesError.value = message(error, '角色列表加载失败')
  } finally {
    rolesLoading.value = false
  }
}

async function loadMembers(pageNo = memberPaging.pageNo) {
  if (!can('iam:role:read')) return
  membersLoading.value = true
  membersError.value = ''
  try {
    const response = await iamApi.administrators({ pageNo, pageSize: memberPaging.pageSize })
    members.value = response.data.records
    memberTotal.value = safeWireNumber(response.data.total)
    memberPaging.pageNo = pageNo
  } catch (error) {
    membersError.value = message(error, '应用成员加载失败')
  } finally {
    membersLoading.value = false
  }
}

async function loadPermissions(pageNo = permissionPaging.pageNo) {
  if (!can('iam:permission:read')) return
  permissionsLoading.value = true
  permissionsError.value = ''
  try {
    const response = await iamApi.permissions(
      appId.value,
      { pageNo, pageSize: permissionPaging.pageSize },
      permissionKeyword.value,
    )
    permissions.value = response.data.records
    permissionTotal.value = safeWireNumber(response.data.total)
    permissionPaging.pageNo = pageNo
  } catch (error) {
    permissionsError.value = message(error, '权限目录加载失败')
  } finally {
    permissionsLoading.value = false
  }
}

async function selectTab(tab: DetailTab) {
  activeTab.value = tab
  await router.replace({ query: { ...route.query, tab } })
  if (tab === 'clients') await loadClients()
  if (tab === 'roles') await Promise.all([loadRoles(), loadPermissions()])
  if (tab === 'members') await Promise.all([loadRoles(), loadMembers()])
  if (tab === 'permissions') await loadPermissions()
}

function lines(value: string) {
  return value
    .split(/[\n,]/)
    .map((item) => item.trim())
    .filter(Boolean)
}

function openClientAccess(client: OAuthClient) {
  accessClient.value = client
  accessError.value = ''
  Object.assign(accessForm, {
    scopes: client.scopes.join(', '),
    redirectUris: client.redirectUris.join('\n'),
    postLogoutRedirectUris: client.postLogoutRedirectUris.join('\n'),
  })
}

async function saveClientAccess() {
  const client = accessClient.value
  if (!client || accessPending.value) return
  accessPending.value = true
  accessError.value = ''
  try {
    await iamApi.updateOAuthClientAccess({
      clientId: client.clientId,
      scopes: lines(accessForm.scopes),
      redirectUris: lines(accessForm.redirectUris),
      postLogoutRedirectUris: lines(accessForm.postLogoutRedirectUris),
    })
    accessClient.value = undefined
    notify('OAuth 客户端访问配置已更新')
    await loadClients()
  } catch (error) {
    accessError.value = message(error, 'OAuth 客户端访问配置更新失败')
  } finally {
    accessPending.value = false
  }
}

function openClientDrawer() {
  Object.assign(clientForm, {
    type: 'browser',
    name: '',
    clientId: '',
    clientSecret: '',
    audienceAppId: appId.value,
    scopes: 'openid, profile',
    redirectUri: '',
    logoutUri: '',
  })
  clientFormError.value = ''
  clientDrawerOpen.value = true
}

function selectClientType(type: 'browser' | 'machine') {
  clientForm.type = type
  if (type === 'machine') {
    clientForm.scopes = ''
    clientForm.redirectUri = ''
    clientForm.logoutUri = ''
  } else {
    clientForm.scopes = 'openid, profile'
  }
}

async function registerClient() {
  if (clientPending.value || !application.value) return
  clientPending.value = true
  clientFormError.value = ''
  const browser = clientForm.type === 'browser'
  try {
    await iamApi.registerOAuthClient({
      appKey: application.value.appKey,
      audienceAppId: clientForm.audienceAppId,
      name: clientForm.name.trim(),
      clientId: clientForm.clientId.trim(),
      clientSecret: clientForm.clientSecret,
      grantTypes: browser ? ['AUTHORIZATION_CODE', 'REFRESH_TOKEN'] : ['CLIENT_CREDENTIALS'],
      scopes: clientForm.scopes
        .split(',')
        .map((scope) => scope.trim())
        .filter(Boolean),
      redirectUris: browser ? [clientForm.redirectUri.trim()] : [],
      postLogoutRedirectUris: browser ? [clientForm.logoutUri.trim()] : [],
    })
    clientDrawerOpen.value = false
    notify('OAuth 客户端注册成功')
    await loadClients()
  } catch (error) {
    clientFormError.value = message(error, 'OAuth 客户端注册失败')
  } finally {
    clientPending.value = false
  }
}

function openRotation(client: OAuthClient) {
  rotationClient.value = client
  rotationSecret.value = ''
  rotationError.value = ''
}

function requestRotation() {
  if (rotationClient.value && rotationSecret.value) {
    confirmAction.value = { type: 'rotate', client: rotationClient.value }
  }
}

function requestDisable(client: OAuthClient) {
  confirmAction.value = { type: 'disable', client }
}

async function confirmClientAction() {
  const action = confirmAction.value
  if (!action) return
  if (action.type === 'rotate') {
    rotationPending.value = true
    rotationError.value = ''
    try {
      await iamApi.rotateOAuthClientSecret(action.client.clientId, rotationSecret.value)
      confirmAction.value = undefined
      rotationClient.value = undefined
      notify('客户端密钥轮换成功')
    } catch (error) {
      confirmAction.value = undefined
      rotationError.value = message(error, '客户端密钥轮换失败')
    } finally {
      rotationPending.value = false
    }
    return
  }
  rotationPending.value = true
  try {
    await iamApi.disableOAuthClient(action.client.clientId)
    confirmAction.value = undefined
    notify('OAuth 客户端已停用')
    await loadClients()
  } finally {
    rotationPending.value = false
  }
}

function openRoleDrawer(role?: ApplicationRole) {
  editingRole.value = role
  Object.assign(roleForm, { code: role?.code ?? '', name: role?.name ?? '' })
  selectedPermissionIds.value = [...(role?.permissionIds ?? [])]
  permissionKeyword.value = ''
  permissionPaging.pageNo = 1
  roleFormError.value = ''
  roleDrawerOpen.value = true
  void loadPermissions()
}

async function searchPermissions() {
  await loadPermissions(1)
}

async function saveRole() {
  if (rolePending.value) return
  if (!roleForm.code.trim() || !roleForm.name.trim()) {
    roleFormError.value = '角色编码和名称不能为空'
    return
  }
  rolePending.value = true
  roleFormError.value = ''
  try {
    if (editingRole.value) {
      await iamApi.updateRole({
        roleId: editingRole.value.id,
        name: roleForm.name.trim(),
        permissionIds: [...selectedPermissionIds.value],
      })
    } else {
      await iamApi.createRole({
        appId: appId.value,
        code: roleForm.code.trim(),
        name: roleForm.name.trim(),
        permissionIds: [...selectedPermissionIds.value],
      })
    }
    roleDrawerOpen.value = false
    notify(editingRole.value ? '应用角色已更新' : '应用角色创建成功')
    editingRole.value = undefined
    await loadRoles()
  } catch (error) {
    roleFormError.value = message(error, '应用角色创建失败')
  } finally {
    rolePending.value = false
  }
}

async function openApplicationRoles(administrator: Administrator) {
  assignmentAdministrator.value = administrator
  assignedApplicationRoleIds.value = []
  assignmentError.value = ''
  assignmentLoading.value = true
  try {
    const response = await iamApi.applicationRoleAssignments(appId.value, administrator.id)
    if (assignmentAdministrator.value?.id !== administrator.id) return
    assignedApplicationRoleIds.value = [...response.data.roleIds]
  } catch (error) {
    assignmentError.value = message(error, '应用角色分配加载失败')
  } finally {
    assignmentLoading.value = false
  }
}

async function saveApplicationRoles() {
  const administrator = assignmentAdministrator.value
  if (!administrator || assignmentPending.value) return
  assignmentPending.value = true
  assignmentError.value = ''
  try {
    await iamApi.changeApplicationRoles(appId.value, administrator.id, [
      ...assignedApplicationRoleIds.value,
    ])
    assignmentAdministrator.value = undefined
    notify('应用角色分配已更新')
  } catch (error) {
    assignmentError.value = message(error, '应用角色分配失败')
  } finally {
    assignmentPending.value = false
  }
}

onMounted(async () => {
  await loadApplication()
  await loadApplications()
  if (activeTab.value === 'clients') await loadClients()
  if (activeTab.value === 'roles') await Promise.all([loadRoles(), loadPermissions()])
  if (activeTab.value === 'members') await Promise.all([loadRoles(), loadMembers()])
  if (activeTab.value === 'permissions') await loadPermissions()
})

watch(
  () => route.query.tab,
  async (value) => {
    const tab = tabFrom(value)
    if (tab === activeTab.value) return
    activeTab.value = tab
    if (tab === 'clients') await loadClients()
    if (tab === 'roles') await Promise.all([loadRoles(), loadPermissions()])
    if (tab === 'members') await Promise.all([loadRoles(), loadMembers()])
    if (tab === 'permissions') await loadPermissions()
  },
)
</script>

<template>
  <section class="console-page application-detail">
    <header class="console-page-header detail-header">
      <div>
        <RouterLink class="breadcrumb" to="/permissions/applications">← 返回应用列表</RouterLink>
        <h2>{{ application?.name || '应用详情' }}</h2>
        <p v-if="application" class="detail-meta">
          {{ application.appKey }} · {{ application.appId }}
          <StatusBadge :status="application.status" />
        </p>
      </div>
      <ElButton
        v-if="activeTab === 'clients' && can('iam:client:write')"
        data-testid="register-client"
        native-type="button"
        @click="openClientDrawer"
      >
        ＋ 注册客户端
      </ElButton>
      <ElButton
        v-else-if="activeTab === 'roles' && can('iam:role:write')"
        data-testid="create-role"
        native-type="button"
        @click="openRoleDrawer()"
      >
        ＋ 创建角色
      </ElButton>
    </header>

    <div v-if="pageLoading" class="page-loading">正在加载应用信息…</div>
    <div v-else-if="pageError" class="inline-error" role="alert">
      {{ pageError }}
      <ElButton native-type="button" class="text-button" @click="loadApplication">重试</ElButton>
    </div>

    <nav class="detail-tabs" aria-label="应用配置">
      <ElButton
        data-testid="tab-clients"
        native-type="button"
        :class="{ active: activeTab === 'clients' }"
        @click="selectTab('clients')"
      >
        OAuth 客户端 <small>{{ clientTotal }}</small>
      </ElButton>
      <ElButton
        v-if="can('iam:role:read')"
        data-testid="tab-roles"
        native-type="button"
        :class="{ active: activeTab === 'roles' }"
        @click="selectTab('roles')"
      >
        角色 <small>{{ roleTotal }}</small>
      </ElButton>
      <ElButton
        v-if="can('iam:role:read')"
        data-testid="tab-members"
        native-type="button"
        :class="{ active: activeTab === 'members' }"
        @click="selectTab('members')"
      >
        成员授权 <small>{{ memberTotal }}</small>
      </ElButton>
      <ElButton
        v-if="can('iam:permission:read')"
        data-testid="tab-permissions"
        native-type="button"
        :class="{ active: activeTab === 'permissions' }"
        @click="selectTab('permissions')"
      >
        权限目录 <small>{{ permissionTotal }}</small>
      </ElButton>
    </nav>

    <DataState
      v-if="activeTab === 'clients'"
      :loading="clientsLoading"
      :error="clientsError"
      :empty="!clientsLoading && !clients.length"
      :has-data="Boolean(clients.length)"
      empty-text="暂无 OAuth 客户端"
      retry-test-id="clients-retry"
      @retry="loadClients"
    >
      <template #empty-action>
        <ElButton v-if="can('iam:client:write')" native-type="button" @click="openClientDrawer">
          注册第一个客户端
        </ElButton>
      </template>
      <ElTable :data="clients" border>
        <ElTableColumn label="客户端" min-width="190"
          ><template #default="{ row }"
            ><strong>{{ row.name }}</strong
            ><small>{{ row.clientId }}</small></template
          ></ElTableColumn
        >
        <ElTableColumn label="目标应用" min-width="160"
          ><template #default="{ row }"
            >{{ row.audienceAppKey }}<small>{{ row.audienceAppId }}</small></template
          ></ElTableColumn
        >
        <ElTableColumn label="模式" min-width="140"
          ><template #default="{ row }">{{ grantType(clientFrom(row)) }}</template></ElTableColumn
        >
        <ElTableColumn label="Scope" min-width="220"
          ><template #default="{ row }"
            ><span v-for="scope in row.scopes" :key="scope" class="chip">{{
              scope
            }}</span></template
          ></ElTableColumn
        >
        <ElTableColumn label="状态" width="110"
          ><template #default="{ row }"><StatusBadge :status="row.status" /></template
        ></ElTableColumn>
        <ElTableColumn label="操作" min-width="260" fixed="right"
          ><template #default="{ row: client }">
            <div v-if="can('iam:client:write')" class="row-actions">
              <ElButton
                :data-testid="`edit-client-${client.clientId}`"
                native-type="button"
                class="text-button"
                @click="openClientAccess(clientFrom(client))"
              >
                编辑配置
              </ElButton>
              <ElButton
                :data-testid="`rotate-${client.clientId}`"
                native-type="button"
                class="text-button"
                @click="openRotation(clientFrom(client))"
              >
                轮换密钥
              </ElButton>
              <details class="action-menu">
                <summary>更多</summary>
                <ElButton
                  :data-testid="`disable-${client.clientId}`"
                  native-type="button"
                  class="danger-text"
                  @click="requestDisable(clientFrom(client))"
                >
                  停用
                </ElButton>
              </details>
            </div>
          </template></ElTableColumn
        >
      </ElTable>
      <div class="pager">
        <ElButton
          native-type="button"
          class="secondary"
          :disabled="clientPaging.pageNo <= 1 || clientsLoading"
          @click="loadClients(clientPaging.pageNo - 1)"
        >
          上一页
        </ElButton>
        <span>第 {{ clientPaging.pageNo }} 页，共 {{ clientTotal }} 条</span>
        <ElButton
          data-testid="clients-next"
          native-type="button"
          class="secondary"
          :disabled="clientPaging.pageNo * clientPaging.pageSize >= clientTotal || clientsLoading"
          @click="loadClients(clientPaging.pageNo + 1)"
        >
          下一页
        </ElButton>
      </div>
    </DataState>

    <DataState
      v-if="activeTab === 'roles'"
      :loading="rolesLoading"
      :error="rolesError"
      :empty="!rolesLoading && !roles.length"
      :has-data="Boolean(roles.length)"
      empty-text="暂无应用角色"
      @retry="loadRoles"
    >
      <template #empty-action>
        <ElButton v-if="can('iam:role:write')" native-type="button" @click="openRoleDrawer()">
          创建第一个角色
        </ElButton>
      </template>
      <ElTable :data="roles" border>
        <ElTableColumn label="角色" min-width="180"
          ><template #default="{ row }"
            ><strong>{{ row.name }}</strong
            ><small>{{ row.id }}</small></template
          ></ElTableColumn
        >
        <ElTableColumn prop="code" label="编码" min-width="180" />
        <ElTableColumn label="状态" width="110"
          ><template #default="{ row }"><StatusBadge :status="row.status" /></template
        ></ElTableColumn>
        <ElTableColumn label="操作" width="120" fixed="right"
          ><template #default="{ row: role }">
            <ElButton
              v-if="can('iam:role:write')"
              :data-testid="`edit-role-${role.id}`"
              native-type="button"
              class="text-button"
              @click="openRoleDrawer(roleFrom(role))"
            >
              编辑权限
            </ElButton>
          </template></ElTableColumn
        >
      </ElTable>
      <div class="pager">
        <ElButton
          native-type="button"
          class="secondary"
          :disabled="rolePaging.pageNo <= 1 || rolesLoading"
          @click="loadRoles(rolePaging.pageNo - 1)"
        >
          上一页
        </ElButton>
        <span>第 {{ rolePaging.pageNo }} 页，共 {{ roleTotal }} 条</span>
        <ElButton
          native-type="button"
          class="secondary"
          :disabled="rolePaging.pageNo * rolePaging.pageSize >= roleTotal || rolesLoading"
          @click="loadRoles(rolePaging.pageNo + 1)"
        >
          下一页
        </ElButton>
      </div>
    </DataState>

    <DataState
      v-if="activeTab === 'members'"
      :loading="membersLoading"
      :error="membersError"
      :empty="!membersLoading && !members.length"
      :has-data="Boolean(members.length)"
      empty-text="暂无可授权的管理账号"
      @retry="loadMembers"
    >
      <ElTable :data="members" border>
        <ElTableColumn label="管理员" min-width="180"
          ><template #default="{ row }"
            ><strong>{{ row.username }}</strong
            ><small>#{{ row.id }}</small></template
          ></ElTableColumn
        >
        <ElTableColumn prop="email" label="邮箱" min-width="220" />
        <ElTableColumn label="状态" width="110"
          ><template #default="{ row }"><StatusBadge :status="row.status" /></template
        ></ElTableColumn>
        <ElTableColumn label="操作" width="150" fixed="right"
          ><template #default="{ row: administrator }">
            <ElButton
              v-if="can('iam:role:write')"
              :data-testid="`assign-application-roles-${administrator.id}`"
              native-type="button"
              class="text-button"
              @click="openApplicationRoles(administratorFrom(administrator))"
            >
              分配应用角色
            </ElButton>
          </template></ElTableColumn
        >
      </ElTable>
      <div class="pager">
        <ElButton
          native-type="button"
          class="secondary"
          :disabled="memberPaging.pageNo <= 1 || membersLoading"
          @click="loadMembers(memberPaging.pageNo - 1)"
        >
          上一页
        </ElButton>
        <span>第 {{ memberPaging.pageNo }} 页，共 {{ memberTotal }} 条</span>
        <ElButton
          native-type="button"
          class="secondary"
          :disabled="memberPaging.pageNo * memberPaging.pageSize >= memberTotal || membersLoading"
          @click="loadMembers(memberPaging.pageNo + 1)"
        >
          下一页
        </ElButton>
      </div>
    </DataState>

    <div v-if="activeTab === 'permissions'">
      <form class="toolbar" @submit.prevent="searchPermissions">
        <ElInput v-model="permissionKeyword" placeholder="搜索权限编码或名称" clearable />
        <ElButton native-type="submit" class="secondary">搜索</ElButton>
      </form>
      <DataState
        :loading="permissionsLoading"
        :error="permissionsError"
        :empty="!permissionsLoading && !permissions.length"
        :has-data="Boolean(permissions.length)"
        empty-text="暂无权限目录"
        @retry="loadPermissions"
      >
        <ElTable :data="permissions" border>
          <ElTableColumn label="权限" min-width="180"
            ><template #default="{ row }"
              ><strong>{{ row.name }}</strong
              ><small>{{ row.id }}</small></template
            ></ElTableColumn
          >
          <ElTableColumn label="编码" min-width="220"
            ><template #default="{ row }"
              ><span class="chip">{{ row.code }}</span></template
            ></ElTableColumn
          >
          <ElTableColumn prop="description" label="说明" min-width="260" />
        </ElTable>
        <div class="pager">
          <ElButton
            native-type="button"
            class="secondary"
            :disabled="permissionPaging.pageNo <= 1 || permissionsLoading"
            @click="loadPermissions(permissionPaging.pageNo - 1)"
          >
            上一页
          </ElButton>
          <span>第 {{ permissionPaging.pageNo }} 页，共 {{ permissionTotal }} 条</span>
          <ElButton
            native-type="button"
            class="secondary"
            :disabled="
              permissionPaging.pageNo * permissionPaging.pageSize >= permissionTotal ||
              permissionsLoading
            "
            @click="loadPermissions(permissionPaging.pageNo + 1)"
          >
            下一页
          </ElButton>
        </div>
      </DataState>
    </div>
  </section>

  <AppDrawer
    :open="clientDrawerOpen"
    title="注册 OAuth 客户端"
    :description="`归属于 ${application?.appKey ?? ''}`"
    :dirty="clientFormDirty"
    :pending="clientPending"
    @close="clientDrawerOpen = false"
  >
    <form
      id="client-form"
      data-testid="client-form"
      class="drawer-form"
      @submit.prevent="registerClient"
    >
      <div class="segmented">
        <ElButton
          data-testid="client-type-browser"
          native-type="button"
          :class="{ active: clientForm.type === 'browser' }"
          @click="selectClientType('browser')"
        >
          浏览器 Web</ElButton
        ><ElButton
          data-testid="client-type-machine"
          native-type="button"
          :class="{ active: clientForm.type === 'machine' }"
          @click="selectClientType('machine')"
        >
          机器调用
        </ElButton>
      </div>
      <p v-if="clientForm.type === 'browser'" class="field-help">
        使用 Authorization Code、Refresh Token 与 PKCE。
      </p>
      <p class="field-help warning-help">Client Secret 仅在创建时输入，系统不会再次展示。</p>
      <p v-if="clientFormError" class="form-error" role="alert">{{ clientFormError }}</p>
      <label
        >客户端名称<input v-model="clientForm.name" data-testid="client-name" required
      /></label>
      <label
        >Client ID<input v-model="clientForm.clientId" data-testid="client-id" required
      /></label>
      <label
        >Client Secret<input
          v-model="clientForm.clientSecret"
          data-testid="client-secret"
          type="password"
          required
      /></label>
      <label
        >目标应用<select v-model="clientForm.audienceAppId" data-testid="client-audience" required>
          <option v-for="item in applications" :key="item.appId" :value="item.appId">
            {{ item.appKey }} · {{ item.name }}
          </option>
        </select></label
      >
      <label
        >Scope（英文逗号分隔）<input
          v-model="clientForm.scopes"
          data-testid="client-scopes"
          required
      /></label>
      <label v-if="clientForm.type === 'browser'"
        >登录回调 URI<input
          v-model="clientForm.redirectUri"
          data-testid="client-redirect-uri"
          type="url"
          required
      /></label>
      <label v-if="clientForm.type === 'browser'"
        >退出回调 URI<input
          v-model="clientForm.logoutUri"
          data-testid="client-logout-uri"
          type="url"
          required
      /></label>
    </form>
    <template #footer="{ close }"
      ><ElButton native-type="button" class="secondary" :disabled="clientPending" @click="close"
        >取消</ElButton
      ><ElButton native-type="submit" form="client-form" :disabled="clientPending">
        {{ clientPending ? '注册中…' : '注册客户端' }}
      </ElButton></template
    >
  </AppDrawer>

  <AppDrawer
    :open="Boolean(accessClient)"
    title="编辑客户端访问配置"
    :description="accessClient?.clientId"
    :dirty="
      Boolean(accessForm.scopes || accessForm.redirectUris || accessForm.postLogoutRedirectUris)
    "
    :pending="accessPending"
    @close="accessClient = undefined"
  >
    <form
      id="client-access-form"
      data-testid="client-access-form"
      class="drawer-form"
      @submit.prevent="saveClientAccess"
    >
      <p class="field-help">
        {{ grantType(accessClient!) }}客户端的授权模式保持不变。修改后会撤销该客户端的现有授权。
      </p>
      <p v-if="accessError" class="form-error" role="alert">{{ accessError }}</p>
      <label
        >Scope（英文逗号分隔）<input
          v-model="accessForm.scopes"
          data-testid="edit-client-scopes"
          required
      /></label>
      <template v-if="accessClient && grantType(accessClient) === '浏览器'">
        <label
          >登录回调 URI（每行一个）<textarea
            v-model="accessForm.redirectUris"
            data-testid="edit-client-redirect-uri"
            rows="4"
            required
          />
        </label>
        <label
          >退出回调 URI（每行一个）<textarea
            v-model="accessForm.postLogoutRedirectUris"
            data-testid="edit-client-logout-uri"
            rows="4"
            required
          />
        </label>
      </template>
    </form>
    <template #footer="{ close }"
      ><ElButton native-type="button" class="secondary" :disabled="accessPending" @click="close"
        >取消</ElButton
      ><ElButton native-type="submit" form="client-access-form" :disabled="accessPending">
        {{ accessPending ? '保存中…' : '保存配置' }}
      </ElButton></template
    >
  </AppDrawer>

  <AppDrawer
    :open="Boolean(rotationClient)"
    title="轮换客户端密钥"
    :description="rotationClient?.clientId"
    :dirty="Boolean(rotationSecret)"
    :pending="rotationPending"
    @close="rotationClient = undefined"
  >
    <form
      id="rotation-form"
      data-testid="rotation-form"
      class="drawer-form"
      @submit.prevent="requestRotation"
    >
      <p class="field-help warning-help">
        新密钥保存后，旧密钥将立即失效，请先确认调用方可以同步更新。
      </p>
      <p v-if="rotationError" class="form-error">{{ rotationError }}</p>
      <label
        >新 Client Secret<input
          v-model="rotationSecret"
          data-testid="rotation-secret"
          type="password"
          required
      /></label>
    </form>
    <template #footer="{ close }"
      ><ElButton native-type="button" class="secondary" @click="close">取消</ElButton
      ><ElButton native-type="submit" form="rotation-form">继续</ElButton></template
    >
  </AppDrawer>

  <AppDrawer
    :open="roleDrawerOpen"
    :title="editingRole ? '编辑应用角色' : '创建应用角色'"
    :description="application?.appKey"
    :dirty="roleFormDirty"
    :pending="rolePending"
    @close="roleDrawerOpen = false"
  >
    <form id="role-form" data-testid="role-form" class="drawer-form" @submit.prevent="saveRole">
      <p v-if="roleFormError" class="form-error">{{ roleFormError }}</p>
      <label
        >角色编码<input
          v-model="roleForm.code"
          data-testid="role-code"
          :disabled="Boolean(editingRole)"
          required /></label
      ><label>角色名称<input v-model="roleForm.name" data-testid="role-name" required /></label>
      <fieldset>
        <legend>权限</legend>
        <div class="permission-search">
          <input
            v-model="permissionKeyword"
            data-testid="permission-keyword"
            placeholder="搜索权限"
          /><ElButton
            data-testid="permission-search"
            native-type="button"
            class="secondary"
            @click="searchPermissions"
          >
            搜索
          </ElButton>
        </div>
        <p v-if="permissionsError" class="form-error" role="alert">
          {{ permissionsError }}
        </p>
        <label v-for="permission in permissions" :key="permission.id" class="check-row"
          ><input
            v-model="selectedPermissionIds"
            :data-testid="`permission-${permission.id}`"
            type="checkbox"
            :value="permission.id"
          /><span
            >{{ permission.code }}<small>{{ permission.name }}</small></span
          ></label
        >
        <p v-if="selectedPermissionIds.length" class="field-help">
          已选择 {{ selectedPermissionIds.length }} 项权限
        </p>
        <div class="pager compact-pager">
          <ElButton
            native-type="button"
            class="secondary"
            :disabled="permissionPaging.pageNo <= 1 || permissionsLoading"
            @click="loadPermissions(permissionPaging.pageNo - 1)"
          >
            上一页
          </ElButton>
          <span>第 {{ permissionPaging.pageNo }} 页，共 {{ permissionTotal }} 条</span>
          <ElButton
            native-type="button"
            class="secondary"
            :disabled="
              permissionPaging.pageNo * permissionPaging.pageSize >= permissionTotal ||
              permissionsLoading
            "
            @click="loadPermissions(permissionPaging.pageNo + 1)"
          >
            下一页
          </ElButton>
        </div>
      </fieldset>
    </form>
    <template #footer="{ close }"
      ><ElButton native-type="button" class="secondary" @click="close">取消</ElButton
      ><ElButton native-type="submit" form="role-form" :disabled="rolePending">
        {{ rolePending ? '保存中…' : editingRole ? '保存修改' : '创建角色' }}
      </ElButton></template
    >
  </AppDrawer>

  <AppDrawer
    :open="Boolean(assignmentAdministrator)"
    title="分配应用角色"
    :description="assignmentAdministrator?.username"
    :dirty="Boolean(assignedApplicationRoleIds.length)"
    :pending="assignmentPending"
    @close="assignmentAdministrator = undefined"
  >
    <form
      id="application-role-form"
      data-testid="application-role-form"
      class="drawer-form"
      @submit.prevent="saveApplicationRoles"
    >
      <p class="field-help">仅调整该管理员在当前应用中的角色，不影响其他应用授权。</p>
      <p v-if="assignmentError" class="form-error" role="alert">{{ assignmentError }}</p>
      <div v-if="assignmentLoading" class="inline-loading">正在加载角色分配…</div>
      <fieldset v-else>
        <legend>当前应用角色</legend>
        <label v-for="role in roles" :key="role.id" class="check-row"
          ><input
            v-model="assignedApplicationRoleIds"
            :data-testid="`application-role-${role.id}`"
            type="checkbox"
            :value="role.id"
            :disabled="role.status !== 'ACTIVE'"
          /><span
            >{{ role.name }}<small>{{ role.code }}</small></span
          ></label
        >
        <p v-if="!roles.length" class="field-help">当前应用暂无可分配角色</p>
      </fieldset>
    </form>
    <template #footer="{ close }"
      ><ElButton
        native-type="button"
        class="secondary"
        :disabled="assignmentPending"
        @click="close"
      >
        取消</ElButton
      ><ElButton
        native-type="submit"
        form="application-role-form"
        :disabled="assignmentLoading || assignmentPending || Boolean(assignmentError)"
      >
        {{ assignmentPending ? '保存中…' : '保存角色' }}
      </ElButton></template
    >
  </AppDrawer>

  <ConfirmDialog
    :open="Boolean(confirmAction)"
    :title="confirmAction?.type === 'rotate' ? '确认轮换密钥' : '确认停用客户端'"
    :message="
      confirmAction?.type === 'rotate'
        ? `轮换 ${confirmAction.client.clientId} 后，旧密钥将立即失效。`
        : `停用 ${confirmAction?.client.clientId ?? ''} 后，该客户端将无法继续访问。`
    "
    :confirm-label="confirmAction?.type === 'rotate' ? '确认轮换' : '确认停用'"
    danger
    :pending="rotationPending"
    @cancel="confirmAction = undefined"
    @confirm="confirmClientAction"
  />
</template>
