<script setup lang="ts">
import { ref } from "vue";
import { Search } from "@element-plus/icons-vue";
import {
  ElButton,
  ElEmpty,
  ElInput,
  ElSkeleton,
  ElTable,
  ElTableColumn,
} from "element-plus";
import {
  getConnections,
  type BrokerNodeFailure,
  type ConnectionRoute,
} from "../api/monitor";
import FailureNotice from "../components/FailureNotice.vue";
import { formatTime } from "../utils/time";

const userId = ref("");
const routes = ref<ConnectionRoute[]>([]);
const failures = ref<BrokerNodeFailure[]>([]);
const searched = ref(false);
const loading = ref(false);

async function search() {
  const requestedUserId = userId.value.trim();
  if (!/^[1-9]\d*$/.test(requestedUserId)) return;
  loading.value = true;
  try {
    const result = await getConnections(requestedUserId);
    routes.value = result.values.map((item) => item.value);
    failures.value = result.failures;
    searched.value = true;
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <section class="console-page">
    <header class="console-page-header">
      <div>
        <h2>用户连接路由</h2>
        <p>按用户 ID 查询当前 Gateway 路由</p>
      </div>
    </header>
    <form class="compact-search" @submit.prevent="search">
      <ElInput
        v-model="userId"
        inputmode="numeric"
        maxlength="19"
        placeholder="输入用户 ID"
      />
      <ElButton
        native-type="submit"
        type="primary"
        :icon="Search"
        :loading="loading"
        >查询</ElButton
      >
    </form>
    <FailureNotice :failures="failures" />
    <ElSkeleton v-if="loading" :rows="3" animated />
    <ElEmpty
      v-else-if="searched && !routes.length"
      description="未找到该用户的连接路由"
    />
    <ElTable v-else-if="searched" :data="routes" border>
      <ElTableColumn prop="userId" label="用户" min-width="140" />
      <ElTableColumn prop="gatewayId" label="Gateway" min-width="180" />
      <ElTableColumn label="最后活跃" min-width="180"
        ><template #default="{ row }">{{
          formatTime(row.lastSeenAt)
        }}</template></ElTableColumn
      >
    </ElTable>
  </section>
</template>
