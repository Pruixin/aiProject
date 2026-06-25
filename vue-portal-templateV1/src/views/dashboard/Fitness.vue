<template>
  <div class="page">
    <div class="top">
      <div class="title">
        <div class="h1">训练数据大屏</div>
        <div class="h2">运动方式、频率与身体指标之间的影响关系</div>
      </div>
    </div>

    <div v-if="errorMessage" class="error-banner">
      <span>{{ errorMessage }}</span>
      <el-button size="small" @click="refresh" :loading="loading">重试</el-button>
    </div>

    <el-empty v-if="!loading && errorMessage && !data" description="训练数据暂时不可用，请检查 Python 服务后重试" />

    <div v-else class="grid">
      <div class="card span-6">
        <div class="card-title">BMI 分组下的运动表现</div>
        <EChart v-if="optBmiPerformance" class="chart tall" :option="optBmiPerformance" />
      </div>
      <div class="card span-6">
        <div class="card-title">年龄与心率恢复能力</div>
        <EChart v-if="optAgeRecovery" class="chart tall" :option="optAgeRecovery" />
      </div>
      <div class="card span-6">
        <div class="card-title">不同训练类型对 BMI / 体脂率 / 消耗的影响</div>
        <EChart v-if="optWorkoutImpact" class="chart tall" :option="optWorkoutImpact" />
      </div>
      <div class="card span-6">
        <div class="card-title">每周运动频率对 BMI / 体脂率的影响</div>
        <EChart v-if="optFrequencyEffect" class="chart tall" :option="optFrequencyEffect" />
      </div>
      <div class="card span-7">
        <div class="card-title">训练时长与热量消耗效率</div>
        <EChart v-if="optScatter" class="chart tall" :option="optScatter" />
      </div>
      <div class="card span-5">
        <div class="card-title">关键指标相关热力图</div>
        <EChart v-if="optCorrelation" class="chart tall" :option="optCorrelation" />
      </div>
      <div class="card span-7">
        <div class="card-title">BMI 分类下不同训练的平均消耗</div>
        <EChart v-if="optBmiWorkout" class="chart tall" :option="optBmiWorkout" />
      </div>
      <div class="card span-5">
        <div class="card-title">经验等级运动表现雷达图</div>
        <EChart v-if="optExperienceRadar" class="chart tall" :option="optExperienceRadar" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { fetchFitnessData } from '../../api/dashboard'
import EChart from '../../components/EChart.vue'

const loading = ref(false)
const data = ref(null)
const errorMessage = ref('')

const fmtNum = (v) => {
  // 图表和 KPI 里所有小数统一保留 2 位，空值显示为 `—`。
  const n = Number(v)
  if (!Number.isFinite(n)) return '—'
  return n.toFixed(2)
}
const fmtInt = (v) => {
  // 整数类指标统一做千分位格式化。
  const n = Number(v)
  if (!Number.isFinite(n)) return '—'
  return Math.round(n).toLocaleString()
}

const refresh = async () => {
  loading.value = true
  try {
    data.value = await fetchFitnessData()
    errorMessage.value = ''
  } catch (error) {
    errorMessage.value = '训练大屏数据加载失败，当前无法连接 Python 数据服务。'
  } finally {
    loading.value = false
  }
}

onMounted(refresh)

const optBmiPerformance = computed(() => {
  // 每个图表 option 都由后端返回的数据结构直接映射成 ECharts 配置，
  // 页面本身只负责“把数据变成图”，不做额外业务计算。
  const list = data.value?.bmi_performance || []
  if (!list.length) return null
  return {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis' },
    legend: { top: 0, textStyle: { color: 'rgba(255,255,255,0.74)' } },
    grid: { left: 60, right: 52, top: 46, bottom: 52, containLabel: true },
    xAxis: { type: 'category', name: 'BMI 分组', nameLocation: 'middle', nameGap: 34, data: list.map((x) => x.name), axisLabel: { color: 'rgba(255,255,255,0.68)' } },
    yAxis: [
      { type: 'value', name: '时长(h)', nameTextStyle: { color: 'rgba(255,255,255,0.78)' }, axisLabel: { color: 'rgba(255,255,255,0.68)' } },
      { type: 'value', name: '平均心率', nameTextStyle: { color: 'rgba(255,255,255,0.78)' }, axisLabel: { color: 'rgba(255,255,255,0.68)' } },
    ],
    series: [
      { name: '平均时长', type: 'bar', data: list.map((x) => x.avg_duration), itemStyle: { color: '#24cf5f', borderRadius: [10, 10, 0, 0] } },
      { name: '平均心率', type: 'line', yAxisIndex: 1, smooth: true, data: list.map((x) => x.avg_bpm), lineStyle: { color: '#ffb347', width: 3 }, itemStyle: { color: '#ffb347' } },
    ],
  }
})

const optAgeRecovery = computed(() => {
  // 年龄组 -> 心率恢复值，一组分类柱状图。
  const list = data.value?.age_recovery || []
  if (!list.length) return null
  return {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis' },
    grid: { left: 60, right: 20, top: 28, bottom: 52, containLabel: true },
    xAxis: { type: 'category', name: '年龄组', nameLocation: 'middle', nameGap: 34, data: list.map((x) => x.name), axisLabel: { color: 'rgba(255,255,255,0.68)' } },
    yAxis: { type: 'value', name: '心率恢复值', nameTextStyle: { color: 'rgba(255,255,255,0.78)' }, axisLabel: { color: 'rgba(255,255,255,0.68)' } },
    series: [{ type: 'bar', data: list.map((x) => x.value), itemStyle: { color: '#3ea3ff', borderRadius: [10, 10, 0, 0] } }],
  }
})

const optWorkoutImpact = computed(() => {
  // 同时展示训练类型对 BMI / 体脂率 / 热量消耗的影响，因此使用柱线组合图。
  const list = data.value?.workout_body_metrics || []
  if (!list.length) return null
  return {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis' },
    legend: { top: 0, textStyle: { color: 'rgba(255,255,255,0.74)' } },
    grid: { left: 62, right: 58, top: 50, bottom: 52, containLabel: true },
    xAxis: {
      type: 'category',
      name: '训练类型',
      nameLocation: 'middle',
      nameGap: 34,
      data: list.map((x) => x.name),
      axisLabel: { color: 'rgba(255,255,255,0.68)', interval: 0, rotate: 10 },
    },
    yAxis: [
      { type: 'value', name: 'BMI / 体脂率', nameTextStyle: { color: 'rgba(255,255,255,0.78)' }, axisLabel: { color: 'rgba(255,255,255,0.68)' } },
      { type: 'value', name: '热量消耗(kcal)', nameTextStyle: { color: 'rgba(255,255,255,0.78)' }, axisLabel: { color: 'rgba(255,255,255,0.68)' } },
    ],
    series: [
      { name: '平均 BMI', type: 'bar', data: list.map((x) => x.avg_bmi), itemStyle: { color: '#24cf5f', borderRadius: [10, 10, 0, 0] } },
      { name: '平均体脂率', type: 'bar', data: list.map((x) => x.avg_fat), itemStyle: { color: '#ffb347', borderRadius: [10, 10, 0, 0] } },
      { name: '平均热量消耗', type: 'line', yAxisIndex: 1, smooth: true, data: list.map((x) => x.avg_calories), lineStyle: { color: '#8d7dff', width: 3 }, itemStyle: { color: '#8d7dff' } },
    ],
  }
})

const optFrequencyEffect = computed(() => {
  // 每周训练频率与 BMI、体脂率的关系更适合用双折线对比趋势。
  const list = data.value?.frequency_effect || []
  if (!list.length) return null
  return {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis' },
    legend: { top: 0, textStyle: { color: 'rgba(255,255,255,0.74)' } },
    grid: { left: 60, right: 52, top: 50, bottom: 52, containLabel: true },
    xAxis: {
      type: 'category',
      name: '每周运动频率',
      nameLocation: 'middle',
      nameGap: 34,
      data: list.map((x) => `${x.name}天`),
      axisLabel: { color: 'rgba(255,255,255,0.68)' },
    },
    yAxis: [
      { type: 'value', name: 'BMI', nameTextStyle: { color: 'rgba(255,255,255,0.78)' }, axisLabel: { color: 'rgba(255,255,255,0.68)' } },
      { type: 'value', name: '体脂率(%)', nameTextStyle: { color: 'rgba(255,255,255,0.78)' }, axisLabel: { color: 'rgba(255,255,255,0.68)' } },
    ],
    series: [
      { name: '平均 BMI', type: 'line', smooth: true, data: list.map((x) => x.avg_bmi), lineStyle: { color: '#24cf5f', width: 3 }, itemStyle: { color: '#24cf5f' } },
      { name: '平均体脂率', type: 'line', smooth: true, yAxisIndex: 1, data: list.map((x) => x.avg_fat), lineStyle: { color: '#ff8f5a', width: 3 }, itemStyle: { color: '#ff8f5a' } },
    ],
  }
})

const optScatter = computed(() => {
  // 散点图原始点来自每条训练样本；再额外叠加一条趋势线辅助观察整体关系。
  const pts = data.value?.scatter_duration_calories || []
  if (!pts.length) return null
  const types = Array.from(new Set(pts.map((p) => p.workout)))
  // 先按训练类型分组，生成多 series 散点。
  const byType = Object.fromEntries(types.map((t) => [t, []]))
  for (const p of pts) {
    byType[p.workout].push([p.duration, p.calories])
  }
  return {
    backgroundColor: 'transparent',
    grid: { left: 64, right: 24, top: 28, bottom: 56, containLabel: true },
    tooltip: { trigger: 'item' },
    xAxis: { type: 'value', name: '训练时长(h)', nameLocation: 'middle', nameGap: 36, axisLabel: { color: 'rgba(255,255,255,0.65)' } },
    yAxis: { type: 'value', name: '热量消耗(kcal)', nameTextStyle: { color: 'rgba(255,255,255,0.78)' }, axisLabel: { color: 'rgba(255,255,255,0.65)' } },
    series: [
      ...types.map((t) => ({
        name: t,
        type: 'scatter',
        symbolSize: 8,
        data: byType[t],
        emphasis: { focus: 'series' },
      })),
      {
        name: '回归趋势',
        type: 'line',
        data: data.value?.duration_calories_trend || [],
        smooth: false,
        symbol: 'none',
        lineStyle: { color: '#fff', width: 2, type: 'dashed' },
      },
    ],
    legend: { textStyle: { color: 'rgba(255,255,255,0.75)' } },
  }
})

const optCorrelation = computed(() => {
  // 相关矩阵由 Python 已经算好，前端只负责把 x/y/data 转成热力图。
  const matrix = data.value?.correlation_matrix
  if (!matrix?.x?.length) return null
  return {
    backgroundColor: 'transparent',
    tooltip: {
      position: 'top',
      formatter: (p) => `${matrix.y[p.data[1]]}<br/>${matrix.x[p.data[0]]}：${p.data[2]}`,
    },
    grid: { left: 110, right: 24, top: 28, bottom: 52, containLabel: true },
    xAxis: { type: 'category', name: '指标项', nameLocation: 'middle', nameGap: 36, data: matrix.x, axisLabel: { color: 'rgba(255,255,255,0.65)', rotate: 30 } },
    yAxis: { type: 'category', name: '指标项', nameTextStyle: { color: 'rgba(255,255,255,0.78)' }, data: matrix.y, axisLabel: { color: 'rgba(255,255,255,0.65)' } },
    visualMap: {
      min: -1,
      max: 1,
      calculable: true,
      orient: 'horizontal',
      left: 'center',
      bottom: 0,
      textStyle: { color: 'rgba(255,255,255,0.72)' },
      inRange: { color: ['#2d5bff', '#1b1b1b', '#24cf5f'] },
    },
    series: [{ type: 'heatmap', data: matrix.data, label: { show: true, color: '#fff', formatter: ({ data }) => data[2] } }],
  }
})

const optBmiWorkout = computed(() => {
  // 同一训练类型下，按 BMI 分类拆成多组柱子做横向对比。
  const matrix = data.value?.bmi_workout_calories
  if (!matrix?.x?.length || !matrix?.series?.length) return null
  return {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis' },
    legend: { top: 0, textStyle: { color: 'rgba(255,255,255,0.74)' } },
    grid: { left: 62, right: 24, top: 48, bottom: 52, containLabel: true },
    xAxis: { type: 'category', name: '训练类型', nameLocation: 'middle', nameGap: 34, data: matrix.x, axisLabel: { color: 'rgba(255,255,255,0.68)', interval: 0, rotate: 10 } },
    yAxis: { type: 'value', name: '平均热量消耗(kcal)', nameTextStyle: { color: 'rgba(255,255,255,0.78)' }, axisLabel: { color: 'rgba(255,255,255,0.68)' } },
    series: matrix.series.map((s, index) => ({
      name: s.name,
      type: 'bar',
      data: s.data,
      itemStyle: { color: ['#24cf5f', '#3ea3ff', '#ffb347', '#8d7dff'][index % 4], borderRadius: [8, 8, 0, 0] },
    })),
  }
})

const optExperienceRadar = computed(() => {
  // 雷达图的各维度上限根据当前数据动态计算，避免图形过扁或过满。
  const list = data.value?.experience_radar || []
  if (!list.length) return null
  return {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, textStyle: { color: 'rgba(255,255,255,0.7)' } },
    radar: {
      indicator: [
        { name: '时长', max: Math.max(...list.map((x) => x.duration), 1) * 1.2 },
        { name: '消耗', max: Math.max(...list.map((x) => x.calories), 1) * 1.2 },
        { name: '平均心率', max: Math.max(...list.map((x) => x.avg_bpm), 1) * 1.2 },
        { name: '频次', max: Math.max(...list.map((x) => x.frequency), 1) * 1.2 },
        { name: '饮水量', max: Math.max(...list.map((x) => x.water), 1) * 1.2 },
      ],
      axisName: { color: 'rgba(255,255,255,0.7)' },
      splitLine: { lineStyle: { color: 'rgba(255,255,255,0.08)' } },
      splitArea: { areaStyle: { color: ['rgba(255,255,255,0.01)'] } },
    },
    series: [{
      type: 'radar',
      data: list.map((x, index) => ({
        name: x.name,
        value: [x.duration, x.calories, x.avg_bpm, x.frequency, x.water],
        areaStyle: { color: ['rgba(36,207,95,0.16)', 'rgba(62,163,255,0.16)', 'rgba(255,179,71,0.16)', 'rgba(141,125,255,0.16)'][index % 4] },
      })),
    }],
  }
})

</script>

<style scoped lang="scss">
.page {
  width: 100%;
}

.top {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 18px;
}

.h1 {
  font-size: 28px;
  font-weight: 900;
  letter-spacing: 1px;
}

.h2 {
  margin-top: 8px;
  color: rgba(255, 255, 255, 0.65);
  font-size: 13px;
  font-weight: 700;
}

.error-banner {
  margin-bottom: 18px;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(255, 128, 128, 0.12);
  border: 1px solid rgba(255, 128, 128, 0.3);
  color: rgba(255, 230, 230, 0.96);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.grid {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  gap: 14px;
}

.card {
  border-radius: 22px;
  padding: 16px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  min-height: 320px;
}

.card-title {
  font-weight: 900;
  letter-spacing: 0.6px;
  margin-bottom: 12px;
  color: rgba(255, 255, 255, 0.88);
}

.chart {
  height: 300px;
}

.tall {
  height: 420px;
}

.span-5 {
  grid-column: span 5;
}

.span-4 {
  grid-column: span 4;
}

.span-6 {
  grid-column: span 6;
}

.span-7 {
  grid-column: span 7;
}

@media (max-width: 1200px) {

  .span-4,
  .span-5,
  .span-6,
  .span-7 {
    grid-column: span 12;
  }
}
</style>
