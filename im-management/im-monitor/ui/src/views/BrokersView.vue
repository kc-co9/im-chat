<script setup lang="ts">
import { onMounted, ref } from "vue";
import { View } from "@element-plus/icons-vue";
import {
  ElAlert,
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElDrawer,
  ElSkeleton,
  ElTable,
  ElTableColumn,
} from "element-plus";
import {
  getBroker,
  getBrokers,
  type BrokerNode,
  type BrokerNodeOverview,
  type BrokerNodeFailure,
} from "../api/monitor";
import FailureNotice from "../components/FailureNotice.vue";
import { formatTime } from "../utils/time";

const brokers = ref<BrokerNode[]>([]);
const failures = ref<BrokerNodeFailure[]>([]);
const detail = ref<BrokerNodeOverview>();
const detailVisible = ref(false);
const loading = ref(false);
const error = ref("");

async function load() {
  loading.value = true;
  error.value = "";
  try {
    const result = await getBrokers();
    brokers.value = result.values.map((item) => item.value);
    failures.value = result.failures;
  } catch {
    error.value = "Broker 列表加载失败";
  } finally {
    loading.value = false;
  }
}

async function selectBroker(brokerId: string) {
  detail.value = await getBroker(brokerId);
  detailVisible.value = true;
}

onMounted(load);
</script>

<template>
  <section class="console-page">
    <header class="console-page-header">
      <div>
        <h2>Broker 节点</h2>
        <p>查看发现到的 Broker 与单节点运行状态</p>
      </div>
    </header>
    <FailureNotice :failures="failures" />
    <ElAlert
      v-if="error"
      :title="error"
      type="error"
      show-icon
      :closable="false"
    />
    <ElSkeleton v-if="loading && !brokers.length" :rows="5" animated />
    <ElTable v-else :data="brokers" border empty-text="暂无 Broker 数据">
      <ElTableColumn prop="brokerId" label="Broker" min-width="180" />
      <ElTableColumn label="地址" min-width="180"
        ><template #default="{ row }"
          >{{ row.host }}:{{ row.port }}</template
        ></ElTableColumn
      >
      <ElTableColumn label="注册时间" min-width="180"
        ><template #default="{ row }">{{
          formatTime(row.registeredAt)
        }}</template></ElTableColumn
      >
      <ElTableColumn label="操作" width="80" fixed="right"
        ><template #default="{ row }"
          ><ElButton
            :icon="View"
            link
            aria-label="查看详情"
            @click="selectBroker(row.brokerId)" /></template
      ></ElTableColumn>
    </ElTable>
    <ElDrawer
      v-model="detailVisible"
      title="Broker 详情"
      size="min(480px, 100vw)"
    >
      <ElDescriptions v-if="detail" :column="1" border>
        <ElDescriptionsItem label="Broker">{{
          detail.brokerId
        }}</ElDescriptionsItem>
        <ElDescriptionsItem label="状态">{{
          detail.status === "HEALTHY" ? "健康" : "不可达"
        }}</ElDescriptionsItem>
        <ElDescriptionsItem label="说明">{{
          detail.errorSummary || "-"
        }}</ElDescriptionsItem>
      </ElDescriptions>
    </ElDrawer>
  </section>
</template>
