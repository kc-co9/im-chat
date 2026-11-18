<script setup lang="ts">
import { onMounted, ref } from "vue";
import {
  ElSkeleton,
  ElTabPane,
  ElTable,
  ElTableColumn,
  ElTabs,
} from "element-plus";
import { getGossipRecords, type DiagnosticRecord } from "../api/ops";
import { formatTime, parseMilliseconds } from "../utils/time";

const gossip = ref<DiagnosticRecord[]>([]);
const loading = ref(true);

onMounted(async () => {
  try {
    const gossipResult = await getGossipRecords();
    gossip.value = gossipResult.values.map((item) => item.value);
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <section class="console-page">
    <header class="console-page-header">
      <div>
        <h2>诊断记录</h2>
        <p>查看 Gossip 同步的最近执行结果</p>
      </div>
    </header>
    <ElSkeleton v-if="loading" :rows="6" animated />
    <ElTabs v-else>
      <ElTabPane label="Gossip 同步">
        <ElTable :data="gossip" border empty-text="暂无 Gossip 记录">
          <ElTableColumn label="执行时间" min-width="180"
            ><template #default="{ row }">{{
              formatTime(row.executedAtMs)
            }}</template></ElTableColumn
          >
          <ElTableColumn prop="target" label="目标" min-width="180" />
          <ElTableColumn prop="status" label="状态" width="120" />
          <ElTableColumn label="耗时(ms)" width="120"><template #default="{ row }">{{ parseMilliseconds(row.durationMs) ?? "-" }}</template></ElTableColumn>
        </ElTable>
      </ElTabPane>
    </ElTabs>
  </section>
</template>
