<script setup lang="ts">
import { onMounted, ref } from "vue";
import { SwitchButton, VideoPlay, View } from "@element-plus/icons-vue";
import {
  ElAlert,
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElDrawer,
  ElMessageBox,
  ElSkeleton,
  ElTag,
  ElTable,
  ElTableColumn,
} from "element-plus";
import {
  getBroker,
  getBrokerClusterState,
  getBrokers,
  operateBroker,
  type BrokerClusterState,
  type BrokerNode,
  type BrokerNodeOverview,
  type BrokerNodeFailure,
} from "../api/ops";
import { can } from "../state/auth";
import FailureNotice from "../components/FailureNotice.vue";
import { formatTime } from "../utils/time";

const brokers = ref<BrokerNode[]>([]);
const failures = ref<BrokerNodeFailure[]>([]);
const detail = ref<BrokerNodeOverview>();
const detailVisible = ref(false);
const loading = ref(false);
const error = ref("");
const clusterState = ref<BrokerClusterState>();
const operationMessage = ref("");
const operatingBroker = ref("");

async function load() {
  loading.value = true;
  error.value = "";
  try {
    const [result, committedState] = await Promise.all([
      getBrokers(),
      getBrokerClusterState(),
    ]);
    brokers.value = result.values.map((item) => item.value);
    failures.value = result.failures;
    clusterState.value = committedState;
  } catch {
    error.value = "Broker 列表加载失败";
  } finally {
    loading.value = false;
  }
}

function committedBroker(brokerId: string) {
  return clusterState.value?.brokers.find((item) => item.brokerId === brokerId);
}

async function operate(
  brokerId: string,
  action: "activate" | "drain" | "deactivate",
) {
  if (!clusterState.value || operatingBroker.value) return;
  try {
    await ElMessageBox.confirm(
      action === "drain" ? `确认排空 ${brokerId}？` : `确认激活 ${brokerId}？`,
      "提交集群变更",
      { confirmButtonText: "确认提交", cancelButtonText: "取消", type: "warning" },
    );
  } catch {
    return;
  }
  operatingBroker.value = brokerId;
  operationMessage.value = "";
  try {
    const result = await operateBroker(
      brokerId,
      action,
      clusterState.value.epoch,
    );
    operationMessage.value = `${result.operationId}：${result.status}${result.message ? `（${result.message}）` : ""}`;
    await load();
  } catch {
    operationMessage.value = "操作提交失败，请刷新权威状态后重试";
  } finally {
    operatingBroker.value = "";
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
        <p>
          权威 epoch {{ clusterState?.epoch ?? "-" }} ·
          {{ clusterState?.algorithm ?? "-" }}
        </p>
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
    <ElAlert
      v-if="operationMessage"
      :title="operationMessage"
      type="info"
      show-icon
      closable
      @close="operationMessage = ''"
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
          formatTime(row.registeredAtMs)
        }}</template></ElTableColumn
      >
      <ElTableColumn label="Raft / 分流" min-width="180"
        ><template #default="{ row }">
          <ElTag size="small">{{ committedBroker(row.brokerId)?.membershipRole || "-" }}</ElTag>
          <ElTag size="small" type="info">{{ committedBroker(row.brokerId)?.routingState || "-" }}</ElTag>
        </template></ElTableColumn
      >
      <ElTableColumn label="操作" width="250" fixed="right"
        ><template #default="{ row }"
          ><ElButton
            :icon="View"
            link
            aria-label="查看详情"
            @click="selectBroker(row.brokerId)" />
          <template v-if="can('ops:broker:cluster-state:write')">
            <ElButton
              v-if="committedBroker(row.brokerId)?.routingState === 'READY'"
              :icon="VideoPlay"
              link
              :loading="operatingBroker === row.brokerId"
              @click="operate(row.brokerId, 'activate')"
              >激活</ElButton
            >
            <ElButton
              v-if="committedBroker(row.brokerId)?.routingState === 'ACTIVE'"
              :icon="SwitchButton"
              link
              type="warning"
              :loading="operatingBroker === row.brokerId"
              @click="operate(row.brokerId, 'drain')"
              >排空</ElButton
            >
          </template></template
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
