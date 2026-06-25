<script setup>
import { ElMessage, ElMessageBox } from 'element-plus'
import { computed, onBeforeUnmount, onMounted, reactive, ref, nextTick } from 'vue'
import { addUser, deleteUser, getRegionOptions, getUserById, getUserList, getUserRoles, saveUserRoles, updateUser, uploadFile } from '@/api/user'
import { fetchAllRoles } from '@/api/role'
import { usePermission } from '@/composables/usePermission'

// 用户管理页（后台）：核心职责是分页查询 + 新增/编辑表单 + 头像上传 + 地区级联展示。
// 由于页面包含多个并发请求（列表/地区），这里用 requestSeed + alive flag 做“过期响应丢弃”，
// 避免切页/快速搜索时旧请求覆盖新状态。
const loading = ref(false)
const dialogLoading = ref(false)
const tableData = ref([])
const total = ref(0)
const showSearch = ref(true)
const showForm = ref(false)
const title = ref('新增用户')
const regionOptions = ref([])
const ruleFormRef = ref(null)
let userPageAlive = true
let listRequestSeed = 0
let regionRequestSeed = 0
const assetBaseUrl = typeof window !== 'undefined' ? window.location.origin : ''
const { hasPerm } = usePermission()

const roleDrawerShow = ref(false)
const allRoles = ref([])
const selectedRoleIds = ref([])
const currentAssignUserId = ref(null)
const roleLoading = ref(false)

// ElementPlus Cascader 数据结构适配（后端字典接口返回 {label,value,children} 树）
const cascaderProps = {
  value: 'value',
  label: 'label',
  children: 'children',
  emitPath: true,
  checkStrictly: false,
}

const queryParams = reactive({
  phone: '',
  nickName: '',
  enable: '',
  pageNum: 1,
  pageSize: 10,
})

// 表单模型：locationCodes 用于级联选择器回显；location 为最终提交到后端的叶子 code。
const ruleForm = reactive({
  id: null,
  phone: '',
  password: '',
  sex: 0,
  avatar: '',
  nickName: '',
  location: '',
  locationCodes: [],
  enable: 1,
})

const avatarPreviewUrl = computed(() => {
  if (!ruleForm.avatar) return ''
  return ruleForm.avatar.startsWith('http')
    ? ruleForm.avatar
    : `${assetBaseUrl}${ruleForm.avatar}`
})

const validatePassword = (_, value, callback) => {
  // 新增用户必须填密码；编辑用户可留空表示不修改密码。
  if (!ruleForm.id && !value) {
    callback(new Error('请输入密码'))
    return
  }
  if (value && !/^[a-zA-Z]\w{5,17}$/.test(value)) {
    callback(new Error('密码以字母开头，长度 6-18 位，只能包含字母、数字和下划线'))
    return
  }
  callback()
}

const rules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: ['blur', 'change'] },
    { pattern: /^(13[0-9]|14[01456879]|15[0-35-9]|16[2567]|17[0-8]|18[0-9]|19[0-35-9])\d{8}$/, message: '手机号格式不正确', trigger: ['blur', 'change'] },
  ],
  password: [{ validator: validatePassword, trigger: ['blur', 'change'] }],
  sex: [{ required: true, message: '请选择性别', trigger: 'change' }],
  locationCodes: [{ required: true, message: '请选择所属地区', trigger: 'change' }],
  enable: [{ required: true, message: '请选择用户状态', trigger: 'change' }],
}

const resetForm = () => {
  // 重置表单为默认值（注意：编辑时 password 为空不会回显数据库密码）
  ruleForm.id = null
  ruleForm.phone = ''
  ruleForm.password = ''
  ruleForm.sex = 0
  ruleForm.avatar = ''
  ruleForm.nickName = ''
  ruleForm.location = ''
  ruleForm.locationCodes = []
  ruleForm.enable = 1
}

const getList = async () => {
  // 分页查询用户列表：enable 空字符串视为“全部”，不传给后端。
  const requestId = ++listRequestSeed
  loading.value = true
  try {
    const params = {
      ...queryParams,
      enable: queryParams.enable === '' ? undefined : queryParams.enable,
    }
    const res = await getUserList(params)
    if (!userPageAlive || requestId !== listRequestSeed) return
    tableData.value = res.data?.data || []
    total.value = Number(res.data?.total || 0)
  } finally {
    if (!userPageAlive || requestId !== listRequestSeed) return
    loading.value = false
  }
}

const fetchRegions = async () => {
  // 地区字典用于表单的 cascader 和列表页的地区展示格式化。
  const requestId = ++regionRequestSeed
  try {
    const res = await getRegionOptions()
    if (!userPageAlive || requestId !== regionRequestSeed) return
    regionOptions.value = Array.isArray(res.data) ? res.data : []
  } catch (error) {
    if (!userPageAlive || requestId !== regionRequestSeed) return
    console.error('加载地区数据失败', error)
    regionOptions.value = []
    ElMessage.warning('地区数据加载失败，已先展示用户列表')
  }
}

const handleSearch = () => {
  // 搜索条件变化后回到第一页重新查。
  queryParams.pageNum = 1
  getList()
}

const resetSearch = () => {
  // 重置搜索条件并刷新列表。
  queryParams.phone = ''
  queryParams.nickName = ''
  queryParams.enable = ''
  queryParams.pageNum = 1
  queryParams.pageSize = 10
  getList()
}

const handleSizeChange = (val) => {
  // 每页大小变化后保持当前条件刷新。
  queryParams.pageSize = val
  getList()
}

const handleCurrentChange = (val) => {
  // 翻页刷新。
  queryParams.pageNum = val
  getList()
}

const findCodesByLabels = (options, labels) => {
  // 兼容后端 location 字段可能存的是 “省/市/区” 文本路径，
  // 这里把 labels 反查为 cascader 需要的 value path。
  const codes = []
  let current = options
  for (const label of labels) {
    const matched = current.find((item) => item.label === label)
    if (!matched) break
    codes.push(matched.value)
    current = matched.children || []
  }
  return codes
}

const findPathByCode = (options, targetCode, trail = []) => {
  // 从树中递归寻找指定 code，并返回从根到叶子的节点路径（用于回显 cascader）。
  for (const item of options) {
    const nextTrail = [...trail, item]
    if (item.value === targetCode) {
      return nextTrail
    }
    if (item.children?.length) {
      const found = findPathByCode(item.children, targetCode, nextTrail)
      if (found.length) {
        return found
      }
    }
  }
  return []
}

const formatRegionDisplay = (location) => {
  // 表格展示地区：优先显示 “省/市/区” 格式；若只有 code，则用地区树反查显示名称。
  if (!location) return '—'
  if (String(location).includes('/')) return location
  const path = findPathByCode(regionOptions.value, String(location))
  if (!path.length) return location
  return path.map((item) => item.label).join('/')
}

const handleLocationChange = (values) => {
  // cascader 选择变化时只保存最后一级 code 作为后端存储字段。
  ruleForm.location = Array.isArray(values) && values.length ? values[values.length - 1] : ''
}

const handleAvatarUpload = async ({ file }) => {
  // 头像走公共上传接口，返回 url 存入 user.avatar。
  const formData = new FormData()
  formData.append('file', file)
  const res = await uploadFile(formData)
  ruleForm.avatar = res.data?.url || ''
  ElMessage.success('头像上传成功')
}

const openAdd = () => {
  // 打开新增弹窗：清空表单。
  title.value = '新增用户'
  resetForm()
  showForm.value = true
}

const openEdit = async (id) => {
  // 编辑弹窗：先拉取用户详情，再把 location 解析为 cascader 需要的 codes。
  title.value = '编辑用户'
  resetForm()
  const res = await getUserById(id)
  const data = res.data || {}
  ruleForm.id = Number(data.id)
  ruleForm.phone = data.phone || ''
  ruleForm.sex = Number(data.sex ?? 0)
  ruleForm.avatar = data.avatar || ''
  ruleForm.nickName = data.nickName || ''
  ruleForm.location = data.location || ''
  ruleForm.enable = Number(data.enable ?? 1)
  if (ruleForm.location) {
    if (String(ruleForm.location).includes('/')) {
      const labels = ruleForm.location.split('/').filter(Boolean)
      ruleForm.locationCodes = findCodesByLabels(regionOptions.value, labels)
    } else {
      ruleForm.locationCodes = findPathByCode(regionOptions.value, String(ruleForm.location)).map((item) => item.value)
    }
  }
  showForm.value = true
}

const handleCancel = () => {
  // 关闭弹窗并清空表单。
  resetForm()
  showForm.value = false
}

const handleSubmit = async () => {
  if (!ruleFormRef.value) return
  const valid = await ruleFormRef.value.validate().catch(() => false)
  if (!valid) return

  // 提交时按是否有 id 分流到新增/更新接口。
  dialogLoading.value = true
  try {
    const payload = {
      id: ruleForm.id || undefined,
      phone: ruleForm.phone,
      password: ruleForm.password,
      sex: Number(ruleForm.sex),
      avatar: ruleForm.avatar,
      nickName: ruleForm.nickName,
      location: ruleForm.location,
      enable: Number(ruleForm.enable),
    }
    const res = ruleForm.id ? await updateUser(payload) : await addUser(payload)
    if (res.code === 200) {
      ElMessage.success(ruleForm.id ? '修改成功' : '新增成功')
      handleCancel()
      getList()
    }
  } finally {
    dialogLoading.value = false
  }
}

const handleDelete = async (id) => {
  // 删除前二次确认，避免误删。
  try {
    await ElMessageBox.confirm('确认删除该用户吗？', '提示', { type: 'warning' })
    const res = await deleteUser(id)
    if (res.code === 200) {
      ElMessage.success('删除成功')
      getList()
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败', error)
    }
  }
}

const openRoleAssign = async (userId) => {
  currentAssignUserId.value = userId
  roleDrawerShow.value = true
  roleLoading.value = true
  try {
    let roles = []
    if (hasPerm('system:role:select')) {
      const roleRes = await fetchAllRoles()
      roles = roleRes.data || []
    }
    allRoles.value = roles || []
    const res = await getUserRoles(userId)
    const rolesData = res.data || []
    selectedRoleIds.value = Array.isArray(rolesData) ? rolesData.map(function (r) { return typeof r === 'object' ? r.id : r }) : []
  } catch (e) {
    console.error('加载角色数据失败', e)
    allRoles.value = []
    selectedRoleIds.value = []
  } finally {
    roleLoading.value = false
  }
}

const saveRoleAssign = async () => {
  roleLoading.value = true
  try {
    const res = await saveUserRoles(currentAssignUserId.value, selectedRoleIds.value)
    if (res.code === 200) {
      ElMessage.success(res.msg || '角色分配成功')
      roleDrawerShow.value = false
    } else {
      ElMessage.error(res.msg || '角色分配失败')
    }
  } catch (e) {
    console.error('角色分配失败', e)
  } finally {
    roleLoading.value = false
  }
}

onMounted(() => {
  // 页面初始化并发拉列表和地区字典（地区失败不影响列表展示）。
  getList()
  fetchRegions()
})

onBeforeUnmount(() => {
  // 页面卸载：标记不再接收异步响应，避免 setState on unmounted。
  userPageAlive = false
  listRequestSeed += 1
  regionRequestSeed += 1
})
</script>

<template>
  <div class="app-container">
    <el-form v-show="showSearch" :model="queryParams" inline label-width="68px">
      <el-form-item label="手机号">
        <el-input v-model="queryParams.phone" placeholder="请输入手机号" clearable />
      </el-form-item>
      <el-form-item label="昵称">
        <el-input v-model="queryParams.nickName" placeholder="请输入昵称" clearable />
      </el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-model="queryParams.enable">
          <el-radio :value="''">全部</el-radio>
          <el-radio :value="1">启用</el-radio>
          <el-radio :value="2">禁用</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetSearch">重置</el-button>
      </el-form-item>
    </el-form>

    <el-row :gutter="10" class="toolbar">
      <el-col v-if="hasPerm('system:user:add')" :span="4">
        <el-button type="primary" @click="openAdd">新增用户</el-button>
      </el-col>
    </el-row>

    <el-table v-loading="loading" :data="tableData" stripe style="width: 100%">
      <el-table-column prop="id" label="ID" width="90" align="center" />
      <el-table-column label="头像" width="100" align="center">
        <template #default="scope">
          <el-avatar v-if="scope.row?.avatar"
            :src="scope.row.avatar.startsWith('http') ? scope.row.avatar : `${assetBaseUrl}${scope.row.avatar}`" />
          <el-avatar v-else>无</el-avatar>
        </template>
      </el-table-column>
      <el-table-column prop="phone" label="手机号" min-width="150" />
      <el-table-column prop="nickName" label="昵称" min-width="120" />
      <el-table-column label="性别" width="90" align="center">
        <template #default="scope">
          {{ Number(scope.row.sex) === 1 ? '女' : '男' }}
        </template>
      </el-table-column>
      <el-table-column label="所属地区" min-width="220" show-overflow-tooltip>
        <template #default="scope">
          {{ formatRegionDisplay(scope.row?.location) }}
        </template>
      </el-table-column>
      <el-table-column label="用户状态" width="100" align="center">
        <template #default="scope">
          <el-tag :type="Number(scope.row.enable) === 1 ? 'success' : 'danger'">
            {{ Number(scope.row.enable) === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" min-width="170" />
      <el-table-column prop="updateTime" label="更新时间" min-width="170" />
      <el-table-column label="操作" width="220" fixed="right" align="center">
        <template #default="scope">
          <el-button v-if="hasPerm('system:user:update')" type="success" link
            @click="openRoleAssign(scope.row.id)">分配角色</el-button>
          <el-button v-if="hasPerm('system:user:update')" type="primary" link
            @click="openEdit(scope.row.id)">编辑</el-button>
          <el-button v-if="hasPerm('system:user:delete')" type="danger" link
            @click="handleDelete(scope.row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
        :page-sizes="[10, 20, 50, 100]" layout="total, sizes, prev, pager, next, jumper" :total="total"
        @size-change="handleSizeChange" @current-change="handleCurrentChange" />
    </div>

    <el-dialog v-model="showForm" :title="title" width="720px" @close="handleCancel">
      <el-form ref="ruleFormRef" :model="ruleForm" :rules="rules" label-width="96px" class="user-form">
        <div class="form-grid">
          <el-form-item label="手机号" prop="phone">
            <el-input v-model="ruleForm.phone" placeholder="请输入手机号" clearable />
          </el-form-item>

          <el-form-item :label="ruleForm.id ? '密码' : '密码*'" prop="password">
            <el-input v-model="ruleForm.password" type="password" show-password clearable
              :placeholder="ruleForm.id ? '不填写则不修改密码' : '请输入密码'" />
          </el-form-item>

          <el-form-item label="昵称">
            <el-input v-model="ruleForm.nickName" placeholder="请输入昵称，不填则自动生成" clearable />
          </el-form-item>

          <el-form-item label="性别" prop="sex">
            <el-radio-group v-model="ruleForm.sex">
              <el-radio :value="0">男</el-radio>
              <el-radio :value="1">女</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="用户状态" prop="enable">
            <el-radio-group v-model="ruleForm.enable">
              <el-radio :value="1">启用</el-radio>
              <el-radio :value="2">禁用</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item label="所属地区" prop="locationCodes" class="span-2">
            <el-cascader v-model="ruleForm.locationCodes" :options="regionOptions" :props="cascaderProps" clearable
              filterable placeholder="请选择所属地区" @change="handleLocationChange" />
          </el-form-item>

          <el-form-item label="头像" class="span-2">
            <div class="avatar-field">
              <el-upload class="avatar-uploader" :show-file-list="false" accept="image/*"
                :http-request="handleAvatarUpload">
                <img v-if="ruleForm.avatar" :src="avatarPreviewUrl" class="avatar-preview" alt="avatar" />
                <div v-else class="avatar-placeholder">
                  <div class="avatar-plus">+</div>
                  <div class="avatar-text">上传头像</div>
                </div>
              </el-upload>
            </div>
          </el-form-item>
        </div>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="handleCancel">取消</el-button>
          <el-button type="primary" :loading="dialogLoading" @click="handleSubmit">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <el-drawer v-model="roleDrawerShow" title="分配角色" direction="rtl" size="50%" @close="selectedRoleIds = []">
      <div v-loading="roleLoading" style="max-width: 600px">
        <el-checkbox-group v-model="selectedRoleIds">
          <el-checkbox v-for="role in allRoles" :key="role.id" :label="role.id" :value="role.id"
            style="display: block; margin-bottom: 12px">
            <span style="font-weight: 500; color: #303133">{{ role.name }}</span>
          </el-checkbox>
        </el-checkbox-group>
        <div v-if="allRoles.length === 0 && !roleLoading" style="text-align: center; color: #909399; padding: 40px 0">
          暂无可用角色
        </div>
      </div>
      <template #footer>
        <div style="flex: auto">
          <el-button @click="roleDrawerShow = false">取消</el-button>
          <el-button type="primary" :loading="roleLoading" @click="saveRoleAssign">确定</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped lang="scss">
.toolbar {
  margin: 0 0 16px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

.user-form {
  :deep(.el-cascader) {
    width: 100%;
  }
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 16px;
}

.span-2 {
  grid-column: span 2;
}

.avatar-field {
  display: flex;
  align-items: center;
  gap: 12px;
}

.avatar-preview,
.avatar-placeholder {
  width: 96px;
  height: 96px;
  border-radius: 16px;
}

.avatar-preview {
  display: block;
  object-fit: cover;
  border: 1px solid #dcdfe6;
}

.avatar-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border: 1px dashed #c0c4cc;
  background: #f5f7fa;
  color: #606266;
  cursor: pointer;
}

.avatar-plus {
  font-size: 28px;
  line-height: 1;
}

.avatar-text {
  margin-top: 6px;
  font-size: 12px;
}

@media (max-width: 768px) {
  .form-grid {
    grid-template-columns: repeat(1, minmax(0, 1fr));
  }

  .span-2 {
    grid-column: span 1;
  }
}
</style>
