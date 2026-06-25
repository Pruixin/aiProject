<template>
  <div class="page">
    <div class="top">
      <div class="title">
        <div class="h1">饮食数据大屏</div>
        <div class="h2">围绕健康评分、营养结构和推荐饮食策略，展示更科学的饮食洞察</div>
      </div>
    </div>

    <div v-if="errorMessage" class="error-banner">
      <span>{{ errorMessage }}</span>
      <el-button size="small" @click="refresh" :loading="loading">重试</el-button>
    </div>

    <el-empty v-if="!loading && errorMessage && !data" description="饮食数据暂时不可用，请检查 Python 服务后重试" />

    <div v-else class="grid">
      <div class="card span-6">
        <div class="card-title">不同食物大类的健康评分分布</div>
        <EChart v-if="optFoodGroupBox" class="chart tall" :option="optFoodGroupBox" />
      </div>
      <div class="card span-6">
        <div class="card-title">热量等级下的宏量营养供能比</div>
        <EChart v-if="optMacroRatio" class="chart tall" :option="optMacroRatio" />
      </div>
      <div class="card span-12">
        <div class="card-title">游离糖 / 膳食纤维 / 健康评分关系</div>
        <EChart v-if="optSugarFibre" class="chart tall" :option="optSugarFibre" />
      </div>
      <div class="card span-6">
        <div class="card-title">不同餐别的平均宏量营养</div>
        <EChart v-if="optMacro" class="chart" :option="optMacro" />
      </div>
      <div class="card span-6">
        <div class="card-title">过敏原 Top</div>
        <EChart v-if="optAllergy" class="chart" :option="optAllergy" />
      </div>
      <div class="card span-12">
        <div class="card-title">食物大类平均健康评分 vs 营养评分</div>
        <EChart v-if="optFoodGroupAvg" class="chart" :option="optFoodGroupAvg" />
      </div>
      <div class="card span-7">
        <div class="card-title">能量密度与健康评分关系</div>
        <EChart v-if="optEnergyHealth" class="chart tall" :option="optEnergyHealth" />
      </div>
      <div class="card span-5">
        <div class="card-title">高蛋白低脂健康食物</div>
        <div class="food-grid compact">
          <div v-for="(f, idx) in proteinFoods" :key="`p-${idx}`" class="food">
            <div class="food-name">{{ f.name }}</div>
            <div class="food-meta">
              <span class="tag">蛋白比 {{ fmtNum(f.protein_calorie_ratio) }}</span>
              <span class="tag">脂肪 {{ fmtNum(f.fat_g) }}g</span>
              <span class="tag">评分 {{ fmtNum(f.health_score) }}</span>
            </div>
          </div>
        </div>
      </div>
      <div class="card span-12">
        <div class="card-title">高健康评分食物 Top</div>
        <div class="food-grid">
          <div v-for="(f, idx) in foods" :key="idx" class="food">
            <div class="food-name">{{ f.name }}</div>
            <div class="food-meta">
              <span class="tag">评分 {{ fmtNum(f.health_score) }}</span>
              <span class="tag">能量 {{ fmtNum(f.energy_kcal) }}kcal</span>
              <span class="tag">{{ f.food_type }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { fetchDietData } from '../../api/dashboard'
import EChart from '../../components/EChart.vue'

// 饮食大屏只依赖 Python 服务的饮食数据接口，不依赖 Java 后端业务接口。
const loading = ref(false)
const data = ref(null)
const errorMessage = ref('')

const fmtNum = (v) => {
  // 小数类营养指标统一保留 2 位。
  const n = Number(v)
  if (!Number.isFinite(n)) return '—'
  return n.toFixed(2)
}
const fmtInt = (v) => {
  // 样本数、次数这类整数指标统一做千分位格式化。
  const n = Number(v)
  if (!Number.isFinite(n)) return '—'
  return Math.round(n).toLocaleString()
}

const refresh = async () => {
  // 刷新时拉取完整饮食数据并更新图表；失败时展示错误提示并允许重试。
  loading.value = true
  try {
    data.value = await fetchDietData()
    errorMessage.value = ''
  } catch (error) {
    errorMessage.value = '饮食大屏数据加载失败，当前无法连接 Python 数据服务。'
  } finally {
    loading.value = false
  }
}

onMounted(refresh)

// 下面两个列表不是图表，而是“高健康评分食物”和“高蛋白低脂食物”卡片区的数据源。
const foods = computed(() => data.value?.top_health_foods || [])
const proteinFoods = computed(() => data.value?.high_protein_low_fat_foods || [])

const optFoodGroupBox = computed(() => {
  // 各食物大类的健康评分分布使用箱线图展示离散程度。
  const list = data.value?.food_group_health_box || []
  if (!list.length) return null
  return {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'item' },
    grid: { left: 64, right: 24, top: 28, bottom: 52, containLabel: true },
    xAxis: { type: 'category', name: '食物大类', nameLocation: 'middle', nameGap: 34, data: list.map((x) => x.name), axisLabel: { color: 'rgba(255,255,255,0.65)' } },
    yAxis: { type: 'value', name: '健康评分', nameTextStyle: { color: 'rgba(255,255,255,0.78)' }, axisLabel: { color: 'rgba(255,255,255,0.65)' } },
    series: [{ type: 'boxplot', data: list.map((x) => x.value), itemStyle: { color: '#24cf5f', borderColor: '#24cf5f' } }],
  }
})

const optMacroRatio = computed(() => {
  // 用堆叠柱图比较不同热量等级下碳水/蛋白质/脂肪的供能结构。
  const list = data.value?.macro_ratio_by_energy || []
  if (!list.length) return null
  return {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis' },
    legend: { top: 0, textStyle: { color: 'rgba(255,255,255,0.72)' } },
    grid: { left: 62, right: 22, top: 46, bottom: 52, containLabel: true },
    xAxis: { type: 'category', name: '热量等级', nameLocation: 'middle', nameGap: 34, data: list.map((x) => x.name), axisLabel: { color: 'rgba(255,255,255,0.65)' } },
    yAxis: { type: 'value', name: '供能占比(%)', nameTextStyle: { color: 'rgba(255,255,255,0.78)' }, axisLabel: { color: 'rgba(255,255,255,0.65)' } },
    series: [
      { name: '碳水供能占比', type: 'bar', stack: 'macro', data: list.map((x) => x.carb_ratio), itemStyle: { color: '#4da3ff' } },
      { name: '蛋白质供能占比', type: 'bar', stack: 'macro', data: list.map((x) => x.protein_ratio), itemStyle: { color: '#24cf5f' } },
      { name: '脂肪供能占比', type: 'bar', stack: 'macro', data: list.map((x) => x.fat_ratio), itemStyle: { color: '#ff8f5a' } },
    ],
  }
})

const optSugarFibre = computed(() => {
  // 散点三元关系：x=游离糖，y=膳食纤维，气泡尺寸=健康评分。
  const points = data.value?.sugar_fibre_health_scatter || []
  if (!points.length) return null
  const groups = Array.from(new Set(points.map((x) => x.group)))
  // 按分组拆成多组散点 series，方便在 legend 中切换。
  const byGroup = Object.fromEntries(groups.map((g) => [g, []]))
  points.forEach((p) => byGroup[p.group].push([p.sugar, p.fibre, p.health]))
  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      formatter: (p) => `${p.seriesName}<br/>游离糖: ${p.value[0]}g<br/>膳食纤维: ${p.value[1]}g<br/>健康评分: ${p.value[2]}`,
    },
    legend: { top: 0, textStyle: { color: 'rgba(255,255,255,0.72)' } },
    grid: { left: 68, right: 26, top: 50, bottom: 58, containLabel: true },
    xAxis: { type: 'value', name: '游离糖(g)', nameLocation: 'middle', nameGap: 38, axisLabel: { color: 'rgba(255,255,255,0.65)' } },
    yAxis: { type: 'value', name: '膳食纤维(g)', nameTextStyle: { color: 'rgba(255,255,255,0.78)' }, axisLabel: { color: 'rgba(255,255,255,0.65)' } },
    series: groups.map((g) => ({
      name: g,
      type: 'scatter',
      data: byGroup[g],
      symbolSize: (value) => Math.max(8, Math.min(28, value[2] / 4)),
    })),
  }
})

const optAllergy = computed(() => {
  // 过敏原 Top 更适合横向柱状图，长标签也更易读。
  const list = data.value?.allergy_top || []
  if (!list.length) return null
  return {
    backgroundColor: 'transparent',
    grid: { left: 100, top: 30, bottom: 50, containLabel: true },
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'value', name: '出现次数', nameLocation: 'middle', nameGap: 30, nameTextStyle: { color: 'rgba(255,255,255,0.78)' }, axisLabel: { color: 'rgba(255,255,255,0.65)' } },
    yAxis: { type: 'category', name: '过敏原', nameLocation: 'middle', nameGap: 48, nameTextStyle: { color: 'rgba(255,255,255,0.78)', fontSize: 13, fontWeight: 'bold' }, data: list.map((x) => x.name).reverse(), axisLabel: { color: 'rgba(255,255,255,0.65)' } },
    series: [{ type: 'bar', data: list.map((x) => x.value).reverse(), itemStyle: { color: '#6dff9a', borderRadius: 10 } }],
  }
})

const optMacro = computed(() => {
  // 餐别维度上的宏量营养对比同样用堆叠柱图。
  const list = data.value?.macro_avg_by_food_type || []
  if (!list.length) return null
  return {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis' },
    legend: { top: 0, textStyle: { color: 'rgba(255,255,255,0.72)' } },
    grid: { left: 62, right: 24, top: 46, bottom: 52, containLabel: true },
    xAxis: { type: 'category', name: '餐别', nameLocation: 'middle', nameGap: 34, data: list.map((x) => x.name), axisLabel: { color: 'rgba(255,255,255,0.65)' } },
    yAxis: { type: 'value', name: '克数(g)', nameTextStyle: { color: 'rgba(255,255,255,0.78)' }, axisLabel: { color: 'rgba(255,255,255,0.65)' } },
    series: [
      { name: '碳水(g)', type: 'bar', stack: 'macro', data: list.map((x) => x.carb_g), itemStyle: { color: '#4da3ff' } },
      { name: '蛋白质(g)', type: 'bar', stack: 'macro', data: list.map((x) => x.protein_g), itemStyle: { color: '#24cf5f' } },
      { name: '脂肪(g)', type: 'bar', stack: 'macro', data: list.map((x) => x.fat_g), itemStyle: { color: '#ffb347' } },
    ],
  }
})

const optFoodGroupAvg = computed(() => {
  // 一张图里同时对比食物大类的平均健康评分与平均营养评分。
  const list = data.value?.food_group_health_avg || []
  if (!list.length) return null
  return {
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis' },
    legend: { top: 0, textStyle: { color: 'rgba(255,255,255,0.72)' } },
    grid: { left: 62, right: 24, top: 46, bottom: 52, containLabel: true },
    xAxis: {
      type: 'category',
      name: '食物大类',
      nameLocation: 'middle',
      nameGap: 34,
      data: list.map((x) => x.name),
      axisLabel: { color: 'rgba(255,255,255,0.65)' },
    },
    yAxis: { type: 'value', name: '评分', nameTextStyle: { color: 'rgba(255,255,255,0.78)' }, axisLabel: { color: 'rgba(255,255,255,0.65)' } },
    series: [
      { name: '平均健康评分', type: 'bar', data: list.map((x) => x.avg_health_score), itemStyle: { color: '#24cf5f', borderRadius: [10, 10, 0, 0] } },
      { name: '平均营养评分', type: 'line', smooth: true, data: list.map((x) => x.avg_nutrient_score), lineStyle: { color: '#ffb347', width: 3 }, itemStyle: { color: '#ffb347' } },
    ],
  }
})

const optEnergyHealth = computed(() => {
  // 这里把“高纤高蛋白”食物单独高亮，帮助用户从普通食物中识别优质候选。
  const list = data.value?.energy_health_scatter || []
  if (!list.length) return null
  const normal = []
  const flagged = []
  list.forEach((item) => {
    const point = [item.energy, item.health, item.name]
    if (item.flag === '高纤高蛋白') flagged.push(point)
    else normal.push(point)
  })
  return {
    backgroundColor: 'transparent',
    tooltip: {
      trigger: 'item',
      formatter: (p) => `${p.value[2]}<br/>能量: ${p.value[0]} kcal<br/>健康评分: ${p.value[1]}`,
    },
    grid: { left: 64, right: 24, top: 36, bottom: 56, containLabel: true },
    xAxis: { type: 'value', name: '能量(kcal)', nameLocation: 'middle', nameGap: 36, axisLabel: { color: 'rgba(255,255,255,0.68)' } },
    yAxis: { type: 'value', name: '健康评分', nameTextStyle: { color: 'rgba(255,255,255,0.78)' }, axisLabel: { color: 'rgba(255,255,255,0.68)' } },
    legend: { top: 0, textStyle: { color: 'rgba(255,255,255,0.72)' } },
    series: [
      { name: `普通食物 (ρ=${fmtNum(data.value?.energy_health_spearman)})`, type: 'scatter', data: normal, symbolSize: 10, itemStyle: { color: 'rgba(62,163,255,0.55)' } },
      { name: '高纤高蛋白', type: 'scatter', data: flagged, symbolSize: 14, itemStyle: { color: '#24cf5f' } },
    ],
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

.span-6 {
  grid-column: span 6;
}

.span-5 {
  grid-column: span 5;
}

.span-7 {
  grid-column: span 7;
}

.span-12 {
  grid-column: span 12;
}

.food-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.food-grid.compact {
  grid-template-columns: repeat(1, minmax(0, 1fr));
  max-height: 420px;
  overflow: auto;
}

.food {
  padding: 14px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.food-name {
  font-weight: 900;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.92);
}

.food-meta {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.tag {
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(36, 207, 95, 0.12);
  border: 1px solid rgba(36, 207, 95, 0.22);
  color: rgba(255, 255, 255, 0.8);
  font-weight: 800;
  font-size: 12px;
}

@media (max-width: 1200px) {

  .span-5,
  .span-6,
  .span-7 {
    grid-column: span 12;
  }

  .food-grid {
    grid-template-columns: repeat(1, minmax(0, 1fr));
  }
}
</style>
