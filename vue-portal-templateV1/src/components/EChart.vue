<template>
  <div ref="el" class="echart" />
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import * as echarts from 'echarts'

const props = defineProps({
  option: {
    type: Object,
    required: true,
  },
})

const el = ref(null)
let chart = null
let ro = null

const render = () => {
  if (!chart) return
  chart.setOption(props.option, { notMerge: true })
}

onMounted(() => {
  chart = echarts.init(el.value)
  render()
  ro = new ResizeObserver(() => chart && chart.resize())
  ro.observe(el.value)
})

watch(
  () => props.option,
  () => render(),
  { deep: true },
)

onBeforeUnmount(() => {
  if (ro && el.value) ro.unobserve(el.value)
  ro = null
  if (chart) chart.dispose()
  chart = null
})
</script>

<style scoped>
.echart {
  width: 100%;
  height: 100%;
}
</style>

