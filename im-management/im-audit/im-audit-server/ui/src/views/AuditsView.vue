<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { Download, Filter, Refresh, Search, View } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { api, type AuditDetail, type AuditListItem } from '../api/audit'
import { can } from '../state/auth'
import { formatTimestamp, safeWireNumber, userTimeZone } from '../support/dateTime'

const DAY_MILLIS = 24 * 60 * 60 * 1000
const now = new Date()
const rows = ref<AuditListItem[]>([])
const total = ref(0)
const loading = ref(false)
const exporting = ref(false)
const errorMessage = ref('')
const detail = ref<AuditDetail>()
const detailVisible = ref(false)
const tab = ref<'BUSINESS' | 'SECURITY'>('BUSINESS')
const advancedFiltersOpen = ref(false)
const query = reactive({
  pageNo: 1,
  pageSize: 20,
  sourceApp: '',
  action: '',
  outcome: '',
  actorId: '',
  targetType: '',
  targetId: '',
  traceId: '',
  occurredFrom: new Date(now.getTime() - DAY_MILLIS),
  occurredTo: now,
})
const request = computed(() => {
  const optional = Object.fromEntries(
    Object.entries({
      sourceApp: query.sourceApp,
      action: query.action,
      outcome: query.outcome,
      actorId: query.actorId,
      targetType: query.targetType,
      targetId: query.targetId,
      traceId: query.traceId,
    })
      .map(([key, value]) => [key, value.trim()])
      .filter(([, value]) => value),
  )
  return {
    pageNo: query.pageNo,
    pageSize: query.pageSize,
    type: tab.value,
    occurredFrom: query.occurredFrom.getTime(),
    occurredTo: query.occurredTo.getTime(),
    ...optional,
  }
})

async function load() {
  loading.value = true
  errorMessage.value = ''
  try {
    const { data } = await api.audits(request.value)
    rows.value = data.records
    total.value = safeWireNumber(data.total)
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '审计记录加载失败'
  } finally {
    loading.value = false
  }
}

async function search() {
  query.pageNo = 1
  await load()
}

async function changeTab(type: 'BUSINESS' | 'SECURITY') {
  tab.value = type
  query.pageNo = 1
  await load()
}

function tabChanged(value: string | number) {
  return changeTab(String(value) as 'BUSINESS' | 'SECURITY')
}

async function openDetail(row: AuditListItem) {
  detail.value = (await api.audit(row.auditId)).data
  detailVisible.value = true
}

async function exportWorkbook() {
  exporting.value = true
  try {
    const { pageNo: ignoredPageNo, pageSize: ignoredPageSize, ...filters } = request.value
    void ignoredPageNo
    void ignoredPageSize
    const { data } = await api.exportAudits({ ...filters, timeZone: userTimeZone })
    const url = URL.createObjectURL(data)
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = 'audit-export.xlsx'
    anchor.click()
    URL.revokeObjectURL(url)
  } catch (error) {
    const message = error instanceof Error ? error.message : '导出失败'
    ElMessage.error(message)
  } finally {
    exporting.value = false
  }
}

onMounted(load)
</script>

<template>
  <section class="console-page">
    <header class="console-page-header">
      <div>
        <h1>集中审计</h1>
        <p>跨管理应用查询不可变的业务与安全审计事实</p>
      </div>
      <div class="header-actions">
        <el-button
          v-if="can('audit:export')"
          :icon="Download"
          :loading="exporting"
          @click="exportWorkbook"
        >
          导出
        </el-button>
        <el-button :icon="Refresh" circle title="刷新" @click="load" />
      </div>
    </header>

    <el-tabs :model-value="tab" @tab-change="tabChanged">
      <el-tab-pane label="业务审计" name="BUSINESS" />
      <el-tab-pane label="安全审计" name="SECURITY" />
    </el-tabs>

    <div class="toolbar">
      <el-input v-model="query.sourceApp" placeholder="来源应用" clearable />
      <el-input v-model="query.action" placeholder="动作码" clearable />
      <el-select v-model="query.outcome" placeholder="全部结果" clearable>
        <el-option label="成功" value="SUCCESS" />
        <el-option label="失败" value="FAILURE" />
      </el-select>
      <el-button :icon="Filter" @click="advancedFiltersOpen = !advancedFiltersOpen">
        {{ advancedFiltersOpen ? '收起高级筛选' : '高级筛选' }}
      </el-button>
      <el-button type="primary" :icon="Search" @click="search">查询</el-button>
    </div>
    <div v-show="advancedFiltersOpen" class="advanced-filters">
      <el-input v-model="query.actorId" placeholder="操作者 ID" clearable />
      <el-input v-model="query.targetType" placeholder="目标类型" clearable />
      <el-input v-model="query.targetId" placeholder="目标 ID" clearable />
      <el-date-picker
        v-model="query.occurredFrom"
        type="datetime"
        :clearable="false"
        placeholder="开始时间"
      />
      <el-date-picker
        v-model="query.occurredTo"
        type="datetime"
        :clearable="false"
        placeholder="结束时间"
      />
    </div>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />
    <el-table :data="rows" v-loading="loading" empty-text="当前范围内暂无审计记录">
      <el-table-column label="发生时间" min-width="180">
        <template #default="{ row }">{{ formatTimestamp(row.occurredAt) }}</template>
      </el-table-column>
      <el-table-column prop="sourceApp" label="来源应用" width="120" />
      <el-table-column prop="action" label="动作" min-width="170" />
      <el-table-column label="操作者" min-width="150">
        <template #default="{ row }">{{ row.actorName || row.actorId || '-' }}</template>
      </el-table-column>
      <el-table-column label="目标" min-width="170">
        <template #default="{ row }">{{ row.targetType }} / {{ row.targetId || '-' }}</template>
      </el-table-column>
      <el-table-column label="结果" width="100">
        <template #default="{ row }">
          <el-tag :type="row.outcome === 'SUCCESS' ? 'success' : 'danger'">{{
            row.outcome
          }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="查看" width="76" fixed="right">
        <template #default="{ row }">
          <el-button :icon="View" link title="查看详情" @click="openDetail(row)" />
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

    <el-drawer v-model="detailVisible" title="审计详情" size="min(520px, 100vw)">
      <el-descriptions v-if="detail" :column="1" border>
        <el-descriptions-item label="审计标识">{{ detail.auditId }}</el-descriptions-item>
        <el-descriptions-item label="来源 / 类别"
          >{{ detail.sourceApp }} / {{ detail.type }}</el-descriptions-item
        >
        <el-descriptions-item label="动作">{{ detail.action }}</el-descriptions-item>
        <el-descriptions-item label="操作者"
          >{{ detail.actorType }} / {{ detail.actorId || '-' }}</el-descriptions-item
        >
        <el-descriptions-item label="目标"
          >{{ detail.targetType }} / {{ detail.targetId || '-' }}</el-descriptions-item
        >
        <el-descriptions-item label="结果">{{ detail.outcome }}</el-descriptions-item>
        <el-descriptions-item label="错误码">{{ detail.errorCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="说明">{{ detail.description }}</el-descriptions-item>
        <el-descriptions-item label="发生时间">{{
          formatTimestamp(detail.occurredAt)
        }}</el-descriptions-item>
        <el-descriptions-item label="Trace ID">{{ detail.traceId || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </section>
</template>
