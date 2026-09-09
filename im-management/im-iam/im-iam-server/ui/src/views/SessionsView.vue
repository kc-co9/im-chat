<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElButton, ElPagination, ElTable, ElTableColumn } from 'element-plus'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import DataState from '../components/DataState.vue'
import { iamApi, type OAuthSession } from '../api/iam'
import { can } from '../state/auth'
import { notify } from '../state/feedback'
import { formatTimestamp, safeWireNumber } from '../support/wire'

const records = ref<OAuthSession[]>([])
const total = ref(0)
const loading = ref(false)
const errorMessage = ref('')
const paging = reactive({ pageNo: 1, pageSize: 20 })
const selectedSession = ref<OAuthSession>()
const pendingSessionId = ref('')

async function load(pageNo = paging.pageNo) {
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await iamApi.sessions({ pageNo, pageSize: paging.pageSize })
    records.value = response.data.records
    total.value = safeWireNumber(response.data.total)
    paging.pageNo = pageNo
    return true
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '在线会话加载失败'
    return false
  } finally {
    loading.value = false
  }
}

async function confirmRevoke() {
  const session = selectedSession.value
  if (!session || pendingSessionId.value) return
  pendingSessionId.value = session.id
  try {
    await iamApi.revokeSession(session.id)
    selectedSession.value = undefined
    notify('OAuth 会话已撤销')
    await load()
  } finally {
    pendingSessionId.value = ''
  }
}

function selectSession(row: unknown) {
  selectedSession.value = row as OAuthSession
}

onMounted(() => load())
</script>

<template>
  <section class="console-page">
    <header class="console-page-header">
      <div>
        <p class="eyebrow">权限管理</p>
        <h2>在线会话</h2>
        <p>查询和撤销管理员 OAuth 授权会话</p>
      </div>
      <ElButton data-testid="refresh-sessions" :icon="Refresh" :disabled="loading" @click="load()"
        >刷新</ElButton
      >
    </header>
    <DataState
      :loading="loading"
      :error="errorMessage"
      :empty="!loading && !records.length"
      :has-data="Boolean(records.length)"
      empty-text="暂无在线会话"
      @retry="load()"
    >
      <ElTable
        :data="records"
        border
        :row-class-name="({ row }) => (pendingSessionId === row.id ? 'row-pending' : '')"
      >
        <ElTableColumn label="管理员" min-width="150"
          ><template #default="{ row }"
            ><strong>{{ row.username }}</strong
            ><small>#{{ row.administratorId }}</small></template
          ></ElTableColumn
        >
        <ElTableColumn prop="id" label="授权 ID" min-width="220" />
        <ElTableColumn label="创建时间" min-width="180"
          ><template #default="{ row }">{{
            formatTimestamp(row.createdAt)
          }}</template></ElTableColumn
        >
        <ElTableColumn label="最后访问" min-width="180"
          ><template #default="{ row }">{{
            formatTimestamp(row.lastAccessAt)
          }}</template></ElTableColumn
        >
        <ElTableColumn label="失效时间" min-width="180"
          ><template #default="{ row }">{{
            formatTimestamp(row.expiresAt)
          }}</template></ElTableColumn
        >
        <ElTableColumn label="操作" width="90" fixed="right"
          ><template #default="{ row }"
            ><ElButton
              v-if="can('iam:session:revoke')"
              :data-testid="`revoke-${row.id}`"
              link
              type="danger"
              @click="selectSession(row)"
              >撤销</ElButton
            ></template
          ></ElTableColumn
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
  <ConfirmDialog
    :open="Boolean(selectedSession)"
    title="确认撤销 OAuth 会话"
    :message="`撤销“${selectedSession?.username ?? ''}”的当前会话后，该授权将立即失效。`"
    confirm-label="确认撤销"
    danger
    :pending="Boolean(pendingSessionId)"
    @cancel="selectedSession = undefined"
    @confirm="confirmRevoke"
  />
</template>
