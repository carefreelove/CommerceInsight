<template>
  <div ref="container" class="commerce-chart" role="img" :aria-label="title">
    <div :key="JSON.stringify(rows) + x + y" class="md-echarts" :data-echarts-config="JSON.stringify(options)" style="height: 300px; width: 100%" />
  </div>
</template>
<script setup lang="ts">
import { computed, ref, watch, onMounted, onBeforeUnmount, nextTick } from 'vue';
import * as echarts from 'echarts/core';
import { useEchartsRenderer } from '~/composables/useEchartsRenderer';
const props = defineProps<{ rows: Record<string, any>[]; x: string; y: string; title: string; line?: boolean }>();
const container = ref<HTMLElement | null>(null);
const { renderECharts, disposeEChartsInContainer } = useEchartsRenderer();
const options = computed(() => ({
  color: ['#3267d6'],
  tooltip: { trigger: 'axis', renderMode: 'richText' },
  grid: { left: 65, right: 24, top: 30, bottom: 45 },
  xAxis: { type: 'category', data: props.rows.map(row => String(row[props.x] ?? '')), axisTick: { show: false }, axisLine: { lineStyle: { color: '#d6deea' } }, axisLabel: { color: '#6f7f96' } },
  yAxis: { type: 'value', splitLine: { lineStyle: { color: '#edf0f5' } }, axisLabel: { color: '#6f7f96' } },
  series: [{ type: props.line ? 'line' : 'bar', data: props.rows.map(row => row[props.y] === null ? null : Number(row[props.y])), barMaxWidth: 56, smooth: true, areaStyle: props.line ? { opacity: 0.08 } : undefined, itemStyle: { borderRadius: [5, 5, 0, 0] } }],
}));
let observer: ResizeObserver | undefined;
async function render() { await nextTick(); disposeEChartsInContainer(container.value); renderECharts(container.value); }
watch(options, render);
onMounted(() => {
  render();
  observer = new ResizeObserver(() => { if (container.value) container.value.querySelectorAll<HTMLElement>('.md-echarts').forEach(el => echarts.getInstanceByDom(el)?.resize()); });
  if (container.value) observer.observe(container.value);
});
onBeforeUnmount(() => observer?.disconnect());
</script>
