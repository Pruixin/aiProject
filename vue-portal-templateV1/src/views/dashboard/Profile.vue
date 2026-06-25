<template>
    <div class="page">
        <div class="top">
            <div class="title">
                <div class="h1">人群与推荐大屏</div>
                <div class="h2">基于性别、运动与饮食偏好的人群特征分析与个性化推荐</div>
            </div>
        </div>

        <div v-if="errorMessage" class="error-banner">
            <span>{{ errorMessage }}</span>
            <el-button size="small" @click="refresh" :loading="loading">重试</el-button>
        </div>

        <el-empty v-if="!loading && errorMessage && !data" description="人群推荐数据暂时不可用，请检查 Python 服务后重试" />

        <div v-else class="grid">
            <div class="card span-4">
                <div class="card-title">性别 x 运动类型偏好热力图</div>
                <EChart v-if="optGenderWorkout" class="chart" :option="optGenderWorkout" />
            </div>
            <div class="card span-4">
                <div class="card-title">BMI分类 × 运动类型推荐热力图</div>
                <EChart v-if="optBmiWorkout" class="chart" :option="optBmiWorkout" />
            </div>
            <div class="card span-4">
                <div class="card-title">推荐训练单元 vs 实际训练频次</div>
                <EChart v-if="optRecommendVsActual" class="chart" :option="optRecommendVsActual" />
            </div>
            <div class="card span-6">
                <div class="card-title">训练频率 × 饮食策略偏好</div>
                <EChart v-if="optFreqDiet" class="chart" :option="optFreqDiet" />
            </div>
            <div class="card span-6">
                <div class="card-title">性别 x 每日饮水与餐频</div>
                <EChart v-if="optGenderLifestyle" class="chart" :option="optGenderLifestyle" />
            </div>
            <div class="card span-6">
                <div class="card-title">热量平衡 × 体脂率散点（按饮食策略着色）</div>
                <EChart v-if="optDietScatter" class="chart" :option="optDietScatter" />
            </div>
            <div class="card span-6">
                <div class="card-title">BMI分类 × 运动类型消耗效果箱线图</div>
                <EChart v-if="optBmiWorkoutBox" class="chart" :option="optBmiWorkoutBox" />
            </div>
            <div class="card span-12">
                <div class="card-title">运动类型 x 目标肌肉群桑基图</div>
                <EChart v-if="optSankey" class="chart tall" :option="optSankey" />
            </div>
            <div class="card span-12">
                <div class="card-title">30分钟高效燃脂运动 Top 20</div>
                <EChart v-if="optTopExercise" class="chart tall" :option="optTopExercise" />
            </div>
        </div>
    </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { fetchGymPlanData } from '../../api/dashboard'
import EChart from '../../components/EChart.vue'

const loading = ref(false)
const data = ref(null)
const errorMessage = ref('')

const refresh = async function () {
    loading.value = true
    try {
        data.value = await fetchGymPlanData()
        errorMessage.value = ''
    } catch (err) {
        errorMessage.value = '人群推荐大屏数据加载失败，当前无法连接 Python 数据服务。'
    } finally {
        loading.value = false
    }
}

onMounted(refresh)

var optGenderWorkout = computed(function () {
    var m = data.value && data.value.chart1_gender_workout
    if (!m || !m.data || !m.data.length) return null
    return {
        backgroundColor: 'transparent',
        tooltip: {
            position: 'top',
            formatter: function (p) { return m.y[p.data[1]] + ' / ' + m.x[p.data[0]] + '<br/>占比：' + p.data[2] + '\u0025' }
        },
        grid: { left: 60, right: 24, top: 48, bottom: 48, containLabel: true },
        xAxis: { type: 'category', name: '运动类型', nameLocation: 'middle', nameGap: 28, data: m.x, axisLabel: { color: 'rgba(255,255,255,0.68)', rotate: 15 } },
        yAxis: { type: 'category', name: '性别', nameLocation: 'middle', nameGap: 38, nameTextStyle: { color: 'rgba(255,255,255,0.78)' }, data: m.y, axisLabel: { color: 'rgba(255,255,255,0.68)' } },
        visualMap: { min: 0, max: m.max, orient: 'horizontal', left: 'center', bottom: 0, textStyle: { color: 'rgba(255,255,255,0.72)' }, inRange: { color: ['#14213d', '#24cf5f', '#ffe66d'] } },
        series: [{ type: 'heatmap', data: m.data, label: { show: true, color: '#fff', formatter: function (p) { return p.data[2] + '\u0025' } } }],
    }
})

var optBmiWorkout = computed(function () {
    var m = data.value && data.value.chart2_bmi_workout
    if (!m || !m.data || !m.data.length) return null
    return {
        backgroundColor: 'transparent',
        tooltip: {
            position: 'top',
            formatter: function (p) { return m.y[p.data[1]] + ' / ' + m.x[p.data[0]] + '<br/>平均消耗：' + p.data[2] + ' kcal' }
        },
        grid: { left: 60, right: 24, top: 48, bottom: 48, containLabel: true },
        xAxis: { type: 'category', name: '运动类型', nameLocation: 'middle', nameGap: 28, data: m.x, axisLabel: { color: 'rgba(255,255,255,0.68)', rotate: 15 } },
        yAxis: { type: 'category', name: 'BMI分类', nameLocation: 'middle', nameGap: 38, nameTextStyle: { color: 'rgba(255,255,255,0.78)' }, data: m.y, axisLabel: { color: 'rgba(255,255,255,0.68)' } },
        visualMap: { min: 0, max: m.max, orient: 'horizontal', left: 'center', bottom: 0, textStyle: { color: 'rgba(255,255,255,0.72)' }, inRange: { color: ['#14213d', '#24cf5f', '#e63946'] } },
        series: [{ type: 'heatmap', data: m.data, label: { show: true, color: '#fff', formatter: function (p) { return p.data[2] + ' kcal' } } }],
    }
})

var optRecommendVsActual = computed(function () {
    var list = (data.value && data.value.chart3_recommend_vs_actual) || []
    if (!list.length) return null
    return {
        backgroundColor: 'transparent',
        tooltip: { trigger: 'axis' },
        legend: { top: 0, textStyle: { color: 'rgba(255,255,255,0.74)' } },
        grid: { left: 24, right: 24, top: 60, bottom: 48, containLabel: true },
        xAxis: { type: 'category', name: 'BMI 分类', nameLocation: 'middle', nameGap: 34, data: list.map(function (x) { return x.name }), axisLabel: { color: 'rgba(255,255,255,0.68)' } },
        yAxis: { type: 'value', name: '次数/周', nameTextStyle: { color: 'rgba(255,255,255,0.78)' }, axisLabel: { color: 'rgba(255,255,255,0.68)' } },
        series: [
            { name: '推荐训练单元', type: 'bar', data: list.map(function (x) { return x.recommended_units }), itemStyle: { color: '#8d7dff', borderRadius: [10, 10, 0, 0] } },
            { name: '实际训练频次', type: 'bar', data: list.map(function (x) { return x.actual_frequency }), itemStyle: { color: '#24cf5f', borderRadius: [10, 10, 0, 0] } },
        ],
    }
})

var optFreqDiet = computed(function () {
    var d = data.value && data.value.chart4_freq_diet
    if (!d || !d.x || !d.x.length || !d.series || !d.series.length) return null
    var colors = ['#3ea3ff', '#24cf5f', '#ffb347', '#e63946', '#8d7dff', '#00d2ff']
    var series = d.series.map(function (s, i) {
        return { name: s.name, type: 'bar', stack: 'total', data: s.data, itemStyle: { color: colors[i % colors.length], borderRadius: [10, 10, 0, 0] }, emphasis: { focus: 'series' } }
    })
    return {
        backgroundColor: 'transparent',
        tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
        legend: { top: 0, textStyle: { color: 'rgba(255,255,255,0.74)' } },
        grid: { left: 24, right: 24, top: 60, bottom: 48, containLabel: true },
        xAxis: { type: 'category', name: '训练频率', nameLocation: 'middle', nameGap: 30, data: d.x, axisLabel: { color: 'rgba(255,255,255,0.68)' } },
        yAxis: { type: 'value', name: '人数', nameTextStyle: { color: 'rgba(255,255,255,0.78)' }, axisLabel: { color: 'rgba(255,255,255,0.68)' } },
        series: series,
    }
})

var optGenderLifestyle = computed(function () {
    var list = (data.value && data.value.chart6_gender_lifestyle) || []
    if (!list.length) return null
    return {
        backgroundColor: 'transparent',
        tooltip: { trigger: 'axis' },
        legend: { top: 0, textStyle: { color: 'rgba(255,255,255,0.74)' }, data: ['日均饮水(L)', '日均餐频'] },
        grid: { left: 24, right: 24, top: 60, bottom: 48, containLabel: true },
        xAxis: { type: 'category', name: '性别', nameLocation: 'middle', nameGap: 30, data: list.map(function (x) { return x.name }), axisLabel: { color: 'rgba(255,255,255,0.68)' } },
        yAxis: { type: 'value', name: '数值', nameTextStyle: { color: 'rgba(255,255,255,0.78)' }, axisLabel: { color: 'rgba(255,255,255,0.68)' } },
        series: [
            { name: '日均饮水(L)', type: 'bar', data: list.map(function (x) { return x.water_avg }), itemStyle: { color: '#3ea3ff', borderRadius: [10, 10, 0, 0] } },
            { name: '日均餐频', type: 'bar', data: list.map(function (x) { return x.meals_avg }), itemStyle: { color: '#24cf5f', borderRadius: [10, 10, 0, 0] } },
        ],
    }
})

var optSankey = computed(function () {
    var sankey = data.value && data.value.chart7_sankey
    if (!sankey || !sankey.nodes || !sankey.nodes.length || !sankey.links || !sankey.links.length) return null
    return {
        backgroundColor: 'transparent',
        tooltip: { trigger: 'item', triggerOn: 'mousemove' },
        series: [{
            type: 'sankey', data: sankey.nodes, links: sankey.links,
            emphasis: { focus: 'adjacency' },
            lineStyle: { color: 'gradient', curveness: 0.5 },
            label: { color: 'rgba(255,255,255,0.82)', fontWeight: 800 },
            nodeGap: 18,
            nodeWidth: 28,
        }],
    }
})

var optDietScatter = computed(function () {
    var map = data.value && data.value.chart8_diet_scatter
    if (!map || !Object.keys(map).length) return null
    var colors = { '纯素': '#3ea3ff', '素食': '#24cf5f', '生酮': '#e63946', '低碳': '#ffb347', '原始人饮食': '#8d7dff', '均衡饮食': '#00d2ff' }
    var series = []
    var legendData = []
    Object.keys(map).forEach(function (diet) {
        legendData.push(diet)
        series.push({
            name: diet, type: 'scatter', data: map[diet], symbolSize: 5,
            itemStyle: { color: colors[diet] || '#aaa' },
        })
    })
    return {
        backgroundColor: 'transparent',
        tooltip: { trigger: 'item', formatter: function (p) { return p.seriesName + '<br/>热量平衡：' + p.data[0] + ' kcal<br/>体脂率：' + p.data[1] + '\u0025' } },
        legend: { top: 0, textStyle: { color: 'rgba(255,255,255,0.74)' }, data: legendData },
        grid: { left: 24, right: 24, top: 60, bottom: 48, containLabel: true },
        xAxis: { type: 'value', name: '热量平衡(kcal)', nameLocation: 'middle', nameGap: 30, axisLabel: { color: 'rgba(255,255,255,0.68)' } },
        yAxis: { type: 'value', name: '体脂率(\u0025)', nameTextStyle: { color: 'rgba(255,255,255,0.78)' }, axisLabel: { color: 'rgba(255,255,255,0.68)' } },
        series: series,
    }
})

var optBmiWorkoutBox = computed(function () {
    var d = data.value && data.value.chart9_bmi_workout_box
    if (!d || !d.categories || !d.categories.length || !d.series || !d.series.length) return null
    var bmiColors = { '偏瘦': '#3ea3ff', '正常': '#24cf5f', '超重': '#ffb347', '肥胖': '#e63946' }
    var series = d.series.map(function (s) {
        return {
            name: s.name, type: 'boxplot', data: s.box,
            itemStyle: { color: bmiColors[s.name] || '#aaa', borderColor: bmiColors[s.name] || '#aaa' },
        }
    })
    return {
        backgroundColor: 'transparent',
        tooltip: { trigger: 'item', formatter: function (p) { return p.seriesName + ' - ' + d.categories[p.dataIndex] + '<br/>消耗(kcal)' } },
        legend: { top: 0, textStyle: { color: 'rgba(255,255,255,0.74)' } },
        grid: { left: 24, right: 24, top: 60, bottom: 48, containLabel: true },
        xAxis: { type: 'category', name: '运动类型', nameLocation: 'middle', nameGap: 30, data: d.categories, axisLabel: { color: 'rgba(255,255,255,0.68)', rotate: 15 } },
        yAxis: { type: 'value', name: '热量消耗(kcal)', nameTextStyle: { color: 'rgba(255,255,255,0.78)' }, axisLabel: { color: 'rgba(255,255,255,0.68)' } },
        series: series,
    }
})

var optTopExercise = computed(function () {
    var list = (data.value && data.value.chart10_top_exercise) || []
    if (!list.length) return null
    return {
        backgroundColor: 'transparent',
        tooltip: { trigger: 'axis', formatter: function (p) { return p[0].name + '<br/>30分钟燃脂：' + p[0].value + ' kcal' } },
        grid: { left: 190, right: 44, top: 28, bottom: 24, containLabel: true },
        xAxis: { type: 'value', name: '30分钟燃脂(kcal)', nameLocation: 'middle', nameGap: 30, nameTextStyle: { color: 'rgba(255,255,255,0.78)' }, axisLabel: { color: 'rgba(255,255,255,0.68)' } },
        yAxis: { type: 'category', name: '运动动作', nameLocation: 'middle', nameGap: 70, nameTextStyle: { color: 'rgba(255,255,255,0.78)', padding: [0, 0, 0, 0] }, data: list.map(function (x) { return x.name }).reverse(), axisLabel: { color: 'rgba(255,255,255,0.72)', width: 120, overflow: 'truncate' } },
        series: [{ type: 'bar', data: list.map(function (x) { return x.value }).reverse(), itemStyle: { color: function (p) { return p.data >= 350 ? '#24cf5f' : '#ffb347' }, borderRadius: [0, 10, 10, 0] }, label: { show: true, position: 'right', color: 'rgba(255,255,255,0.86)' } }],
    }
})
</script>

<style scoped lang="scss">
.page {
    width: 100%;
    overflow: visible;
}

.echart {
    width: 100%;
    height: 100%;
    overflow: visible;
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
    overflow: visible;
}

.card-title {
    font-weight: 900;
    letter-spacing: 0.6px;
    margin-bottom: 12px;
    color: rgba(255, 255, 255, 0.88);
}

.chart {
    height: 300px;
    overflow: visible;
}

.tall {
    height: 420px;
}

.span-4 {
    grid-column: span 4;
}

.span-6 {
    grid-column: span 6;
}

.span-12 {
    grid-column: span 12;
}

@media (max-width: 1200px) {

    .span-4,
    .span-6,
    .span-12 {
        grid-column: span 12;
    }
}
</style>