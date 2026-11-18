<script setup lang="ts">
import { BarChart } from "echarts/charts";
import { GridComponent } from "echarts/components";
import { init, use, type EChartsType } from "echarts/core";
import { CanvasRenderer } from "echarts/renderers";
import { onBeforeUnmount, onMounted, ref, watch } from "vue";

use([BarChart, GridComponent, CanvasRenderer]);

const props = defineProps<{
  brokerCount: number;
  gatewayCount: number;
  connectionCount: number;
}>();
const element = ref<HTMLElement>();
let chart: EChartsType | undefined;

function render() {
  chart?.setOption({
    grid: { left: 42, right: 20, top: 18, bottom: 32 },
    xAxis: { type: "category", data: ["Broker", "Gateway", "连接路由"] },
    yAxis: { type: "value", minInterval: 1 },
    series: [
      {
        type: "bar",
        data: [props.brokerCount, props.gatewayCount, props.connectionCount],
        itemStyle: { color: "#247662" },
      },
    ],
  });
}

onMounted(() => {
  if (element.value) {
    chart = init(element.value);
    render();
  }
  window.addEventListener("resize", resize);
});
function resize() {
  chart?.resize();
}
watch(
  () => [props.brokerCount, props.gatewayCount, props.connectionCount],
  render,
);
onBeforeUnmount(() => {
  window.removeEventListener("resize", resize);
  chart?.dispose();
});
</script>

<template>
  <div ref="element" class="overview-chart" aria-label="集群资源图表" />
</template>
