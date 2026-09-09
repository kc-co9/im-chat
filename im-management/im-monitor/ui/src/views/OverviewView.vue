<script setup lang="ts">
import { defineAsyncComponent, onMounted, ref } from "vue";
import { Refresh } from "@element-plus/icons-vue";
import {
  ElAlert,
  ElButton,
  ElSkeleton,
  ElStatistic,
  ElTable,
  ElTableColumn,
  ElTag,
} from "element-plus";
import { getOverview, type ClusterOverview } from "../api/monitor";

const OverviewChart = defineAsyncComponent(
  () => import("../components/OverviewChart.vue"),
);
const overview = ref<ClusterOverview>();
const loading = ref(false);
const error = ref("");

async function load() {
  loading.value = true;
  error.value = "";
  try {
    overview.value = await getOverview();
  } catch {
    error.value = "总览加载失败";
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <section class="console-page">
    <header class="console-page-header">
      <div>
        <h2>集群总览</h2>
        <p>Broker、Gateway 与连接路由的实时只读视图</p>
      </div>
      <ElButton
        :icon="Refresh"
        circle
        aria-label="刷新"
        :loading="loading"
        @click="load"
      />
    </header>
    <ElAlert
      v-if="error"
      :title="error"
      type="error"
      show-icon
      :closable="false"
    />
    <ElSkeleton v-if="loading && !overview" :rows="6" animated />
    <template v-else-if="overview">
      <div class="metric-grid">
        <ElStatistic title="Broker" :value="overview.brokerCount" />
        <ElStatistic title="Gateway" :value="overview.gatewayCount" />
        <ElStatistic title="连接路由" :value="overview.connectionCount" />
        <span class="sr-only">连接路由 {{ overview.connectionCount }}</span>
      </div>
      <section class="data-section">
        <h3>资源分布</h3>
        <OverviewChart
          :broker-count="overview.brokerCount"
          :gateway-count="overview.gatewayCount"
          :connection-count="overview.connectionCount"
        />
      </section>
      <section class="data-section">
        <h3>节点状态</h3>
        <ElTable :data="overview.nodes" border empty-text="暂无节点状态">
          <ElTableColumn prop="brokerId" label="Broker" min-width="180" />
          <ElTableColumn label="状态" width="110"
            ><template #default="{ row }"
              ><ElTag :type="row.status === 'HEALTHY' ? 'success' : 'danger'">{{
                row.status === "HEALTHY" ? "健康" : "不可达"
              }}</ElTag></template
            ></ElTableColumn
          >
          <ElTableColumn label="说明" min-width="220"
            ><template #default="{ row }">{{
              row.errorSummary || "-"
            }}</template></ElTableColumn
          >
        </ElTable>
      </section>
    </template>
  </section>
</template>
