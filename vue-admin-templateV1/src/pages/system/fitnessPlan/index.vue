<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deleteFitnessPlan, getFitnessPlanById, getFitnessPlanList } from '@/api/fitnessPlan'
import { usePermission } from '@/composables/usePermission'

// 健身方案管理页：主要用于后台查看用户已生成的 AI 方案和做人工清理。
const loading = ref(false)
const detailLoading = ref(false)
const tableData = ref([])
const total = ref(0)
const showSearch = ref(true)
const showDetail = ref(false)
const currentPlan = ref({})
const { hasPerm } = usePermission()

const queryParams = reactive({
  phone: '',
  nickName: '',
  goal: '',
  pageNum: 1,
  pageSize: 10,
})

const prettyPlanData = computed(() => {
  // 详情弹窗里把 planData 格式化成易读的 JSON 文本。
  if (!currentPlan.value?.planData) return ''
  return JSON.stringify(currentPlan.value.planData, null, 2)
})

const getList = async () => {
  // 支持按手机号、昵称、目标筛选；空字符串在请求时转为 undefined，避免影响后端条件判断。
  loading.value = true
  try {
    const res = await getFitnessPlanList({
      ...queryParams,
      phone: queryParams.phone || undefined,
      nickName: queryParams.nickName || undefined,
      goal: queryParams.goal || undefined,
    })
    tableData.value = res.data?.data || []
    total.value = Number(res.data?.total || 0)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  // 查询时回到第一页。
  queryParams.pageNum = 1
  getList()
}

const resetSearch = () => {
  // 重置全部筛选条件。
  queryParams.phone = ''
  queryParams.nickName = ''
  queryParams.goal = ''
  queryParams.pageNum = 1
  queryParams.pageSize = 10
  getList()
}

const handleSizeChange = (val) => {
  queryParams.pageSize = val
  getList()
}

const handleCurrentChange = (val) => {
  queryParams.pageNum = val
  getList()
}

const openDetail = async (id) => {
  // 详情接口会返回 planData，列表接口为了性能不会返回完整 JSON。
  detailLoading.value = true
  showDetail.value = true
  try {
    const res = await getFitnessPlanById(id)
    currentPlan.value = res.data || {}
  } finally {
    detailLoading.value = false
  }
}

const closeDetail = () => {
  // 关闭弹窗并清空当前详情，避免上一次数据闪现到下一次弹窗中。
  showDetail.value = false
  currentPlan.value = {}
}

const handleDelete = async (id) => {
  // 删除后如果当前页只剩最后一条，则自动回退一页，避免出现空白页。
  try {
    await ElMessageBox.confirm('确认删除该健身方案吗？删除后无法恢复。', '提示', { type: 'warning' })
    const res = await deleteFitnessPlan(id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      if (tableData.value.length === 1 && queryParams.pageNum > 1) {
        queryParams.pageNum -= 1
      }
      getList()
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除健身方案失败', error)
    }
  }
}

// 页面进入后立即加载方案列表。
getList()
</script>

<template>
  <div class="app-container">
    <el-form v-show="showSearch" :model="queryParams" inline label-width="84px">
      <el-form-item label="手机号">
        <el-input v-model="queryParams.phone" placeholder="请输入手机号" clearable />
      </el-form-item>
      <el-form-item label="昵称">
        <el-input v-model="queryParams.nickName" placeholder="请输入昵称" clearable />
      </el-form-item>
      <el-form-item label="目标">
        <el-input v-model="queryParams.goal" placeholder="请输入健身目标" clearable />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table v-loading="loading" :data="tableData" stripe style="width: 100%">
      <el-table-column prop="id" label="方案ID" min-width="100" />
      <el-table-column prop="userId" label="用户ID" min-width="100" />
      <el-table-column prop="userPhone" label="手机号" min-width="150" />
      <el-table-column prop="userNickName" label="昵称" min-width="120" />
      <el-table-column prop="height" label="身高" min-width="100" />
      <el-table-column prop="weight" label="体重" min-width="100" />
      <el-table-column prop="goal" label="目标" min-width="120" />
      <el-table-column prop="heartDisease" label="既往病史" min-width="160" show-overflow-tooltip />
      <el-table-column prop="createTime" label="生成时间" min-width="180" />
      <el-table-column label="操作" width="180" fixed="right" align="center">
        <template #default="scope">
          <el-button v-if="hasPerm('system:fitness-plan:list')" type="primary" link @click="openDetail(scope.row.id)">查看</el-button>
          <el-button v-if="hasPerm('system:fitness-plan:list')" type="danger" link @click="handleDelete(scope.row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>

    <el-dialog v-model="showDetail" title="健身方案详情" width="860px" @close="closeDetail">
      <div v-loading="detailLoading" class="plan-detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="方案ID">{{ currentPlan.id || '—' }}</el-descriptions-item>
          <el-descriptions-item label="用户ID">{{ currentPlan.userId || '—' }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ currentPlan.userPhone || '—' }}</el-descriptions-item>
          <el-descriptions-item label="昵称">{{ currentPlan.userNickName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="身高">{{ currentPlan.height || '—' }}</el-descriptions-item>
          <el-descriptions-item label="体重">{{ currentPlan.weight || '—' }}</el-descriptions-item>
          <el-descriptions-item label="目标">{{ currentPlan.goal || '—' }}</el-descriptions-item>
          <el-descriptions-item label="生成时间">{{ currentPlan.createTime || '—' }}</el-descriptions-item>
          <el-descriptions-item label="既往病史" :span="2">{{ currentPlan.heartDisease || '无' }}</el-descriptions-item>
          <el-descriptions-item label="计划内容" :span="2">
            <pre class="plan-json">{{ prettyPlanData || '暂无计划内容' }}</pre>
          </el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped lang="scss">
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.plan-detail {
  min-height: 200px;
}

.plan-json {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.7;
  font-size: 13px;
  color: #303133;
}
</style>
