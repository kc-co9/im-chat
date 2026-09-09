<script setup lang="ts">
import { computed } from "vue";
import { ElAlert } from "element-plus";
import type { BrokerNodeFailure } from "../api/monitor";

const props = defineProps<{ failures: BrokerNodeFailure[] }>();
const description = computed(() =>
  props.failures
    .map((failure) => `${failure.brokerId}：${failure.errorSummary}`)
    .join("；"),
);
</script>

<template>
  <ElAlert
    v-if="failures.length"
    title="部分 Broker 查询失败"
    :description="description"
    type="warning"
    show-icon
    :closable="false"
  />
</template>
