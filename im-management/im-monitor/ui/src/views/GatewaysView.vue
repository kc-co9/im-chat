<script setup lang="ts">
import { onMounted, ref } from "vue";
import { ElAlert, ElSkeleton, ElTable, ElTableColumn } from "element-plus";
import {
  getGateways,
  type BrokerNodeFailure,
  type GatewayNode,
} from "../api/monitor";
import FailureNotice from "../components/FailureNotice.vue";
import { formatTime } from "../utils/time";

const gateways = ref<GatewayNode[]>([]);
const failures = ref<BrokerNodeFailure[]>([]);
const loading = ref(false);
const error = ref("");

onMounted(async () => {
  loading.value = true;
  try {
    const result = await getGateways();
    gateways.value = result.values.map((item) => item.value);
    failures.value = result.failures;
  } catch {
    error.value = "Gateway 列表加载失败";
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <section class="console-page">
    <header class="console-page-header">
      <div>
        <h2>Gateway 节点</h2>
        <p>查看 Gateway 注册与最近心跳状态</p>
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
    <ElSkeleton v-if="loading && !gateways.length" :rows="5" animated />
    <ElTable v-else :data="gateways" border empty-text="暂无 Gateway 数据">
      <ElTableColumn prop="gatewayId" label="Gateway" min-width="180" />
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
      <ElTableColumn label="最后心跳" min-width="180"
        ><template #default="{ row }">{{
          formatTime(row.lastSeenAt)
        }}</template></ElTableColumn
      >
    </ElTable>
  </section>
</template>
