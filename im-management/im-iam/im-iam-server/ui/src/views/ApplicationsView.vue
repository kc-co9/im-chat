<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElPagination,
  ElTable,
  ElTableColumn,
} from 'element-plus'
import AppDrawer from '../components/AppDrawer.vue'
import DataState from '../components/DataState.vue'
import StatusBadge from '../components/StatusBadge.vue'
import { iamApi, type Application } from '../api/iam'
import { can } from '../state/auth'
import { notify } from '../state/feedback'
import { safeWireNumber } from '../support/wire'

const records = ref<Application[]>([])
const total = ref(0)
const loading = ref(false)
const errorMessage = ref('')
const paging = reactive({ pageNo: 1, pageSize: 20 })
const drawerOpen = ref(false)
const submitting = ref(false)
const formError = ref('')
const applicationForm = reactive({ appKey: '', name: '' })
const formDirty = computed(() => Boolean(applicationForm.appKey || applicationForm.name))

async function load(pageNo = paging.pageNo) {
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await iamApi.applications({ pageNo, pageSize: paging.pageSize })
    records.value = response.data.records
    total.value = safeWireNumber(response.data.total)
    paging.pageNo = pageNo
    return true
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '应用列表加载失败'
    return false
  } finally {
    loading.value = false
  }
}

function openDrawer() {
  Object.assign(applicationForm, { appKey: '', name: '' })
  formError.value = ''
  drawerOpen.value = true
}

async function registerApplication() {
  if (submitting.value) return
  submitting.value = true
  formError.value = ''
  try {
    await iamApi.registerApplication({
      appKey: applicationForm.appKey.trim(),
      name: applicationForm.name.trim(),
    })
    drawerOpen.value = false
    notify('IAM 应用注册成功')
    await load(1)
  } catch (error) {
    formError.value = error instanceof Error ? error.message : 'IAM 应用注册失败'
  } finally {
    submitting.value = false
  }
}

onMounted(() => load())
</script>

<template>
  <section class="console-page">
    <header class="console-page-header">
      <div>
        <p class="eyebrow">权限管理</p>
        <h2>应用管理</h2>
        <p>管理接入 IAM 的业务应用及其 OAuth 客户端、角色和权限目录</p>
      </div>
      <div class="header-actions">
        <ElButton
          data-testid="refresh-applications"
          :icon="Refresh"
          :disabled="loading"
          @click="load()"
          >刷新</ElButton
        >
        <ElButton
          v-if="can('iam:application:write')"
          data-testid="register-application"
          type="primary"
          :icon="Plus"
          @click="openDrawer"
          >注册应用</ElButton
        >
      </div>
    </header>

    <DataState
      :loading="loading"
      :error="errorMessage"
      :empty="!loading && !records.length"
      :has-data="Boolean(records.length)"
      empty-text="暂无接入应用"
      @retry="load()"
    >
      <template #empty-action>
        <ElButton v-if="can('iam:application:write')" type="primary" @click="openDrawer"
          >注册第一个应用</ElButton
        >
      </template>
      <ElTable :data="records" border>
        <ElTableColumn prop="name" label="应用" min-width="180" />
        <ElTableColumn label="appKey" min-width="160"
          ><template #default="{ row }"
            ><span class="chip">{{ row.appKey }}</span></template
          ></ElTableColumn
        >
        <ElTableColumn prop="appId" label="应用 ID" min-width="190" />
        <ElTableColumn label="状态" width="110"
          ><template #default="{ row }"><StatusBadge :status="row.status" /></template
        ></ElTableColumn>
        <ElTableColumn label="操作" width="110" fixed="right"
          ><template #default="{ row }"
            ><RouterLink
              class="action-link"
              :to="{ name: 'application-detail', params: { appId: row.appId } }"
              >管理访问</RouterLink
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

  <AppDrawer
    :open="drawerOpen"
    title="注册 IAM 应用"
    description="应用创建后，可以继续配置 OAuth 客户端、角色和权限。"
    :dirty="formDirty"
    :pending="submitting"
    @close="drawerOpen = false"
  >
    <ElForm
      as="form"
      id="application-form"
      data-testid="application-form"
      class="drawer-form"
      @submit.prevent="registerApplication"
    >
      <p v-if="formError" class="form-error" role="alert">{{ formError }}</p>
      <ElFormItem label="appKey"
        ><ElInput
          v-model="applicationForm.appKey"
          data-testid="application-key"
          placeholder="例如：imAudit"
        /><small>稳定应用编码，将进入 Token Claim 和接入配置。</small></ElFormItem
      >
      <ElFormItem label="应用名称"
        ><ElInput
          v-model="applicationForm.name"
          data-testid="application-name"
          placeholder="例如：集中审计"
      /></ElFormItem>
    </ElForm>
    <template #footer="{ close }">
      <ElButton :disabled="submitting" @click="close">取消</ElButton>
      <ElButton type="primary" :loading="submitting" @click="registerApplication"
        >注册应用</ElButton
      >
    </template>
  </AppDrawer>
</template>
