<script setup>
import { addMenu, getCDOptions, getMentTreeData, getMLOptions, getMenuOneById, updateMenu, deleteMenu } from '@/api/menu';
import { ElButton, ElMessage, ElMessageBox } from 'element-plus';
import { remove, status } from 'nprogress';
import { computed, onMounted, ref, watch } from 'vue';
// 引入element-plus图标
import * as ElIcon from '@element-plus/icons-vue'
import { usePermission } from '@/composables/usePermission'

const ruleFormRef = ref(null)
const { hasPerm } = usePermission()

// 菜单管理页的核心是树形菜单维护：目录(M)、菜单(C)、按钮(F) 三种类型共用一套表单。
const data = ref([])
// 菜单树 loading
const loading = ref(false)
// 默认展开所有一级节点，便于后台直观看到树结构。
const defaultExpandedKeys = ref([])
// 新增/编辑弹窗
const showForm = ref(false)

// 目录与菜单下拉项，用于不同 menuType 的父级选择器。
const Moptions = ref([])
const Coptions = ref([])
const title = ref('新增菜单')

// 表单模型：menuType 决定 parentId 的来源以及字段含义。
const ruleForm = ref({
  id: 0,
  parentId: '0',
  path: '',
  name: '',
  component: '',
  status: 0,
  perms: '',
  menuType: '',
  orderNum: 0,
  icon: ''
})
// 菜单类型：目录、页面菜单、按钮权限。
const options = ref([
  {
    value: 'M',
    label: '目录'
  },
  {
    value: 'C',
    label: '菜单'
  },
  {
    value: 'F',
    label: '按钮'
  }
])
// 获取所有目录，供 C 类型菜单选择父级目录。
const getMoptions = () => {
  getMLOptions().then(res => {
    Moptions.value = res.data
  })
}
// 获取所有菜单，供 F 类型按钮选择所属页面菜单。
const getCoptions = () => {
  getCDOptions().then(res => {
    Coptions.value = res.data
  })
}
// 图标选择器：从 Element Plus 图标集合中过滤选择。
const iconSearch = ref('')
const iconPickerVisible = ref(false)

const iconList = ElIcon
const iconKeys = computed(() => {
  return Object.keys(iconList).filter(name => name !== 'default' && name !== '__esModule')
})

const filteredIconKeys = computed(() => {
  const keyword = iconSearch.value.trim().toLowerCase()
  if (!keyword) return iconKeys.value
  return iconKeys.value.filter(name => name.toLowerCase().includes(keyword))
})
const handleSelectIcon = (name) => {
  ruleForm.value.icon = name
  iconPickerVisible.value = false
}

// 表单校验：目录/菜单/按钮共用同一套表单，因此只校验公共核心字段。
const rules = ref({
  path: [{ required: true, message: '请输入菜单路由', trigger: 'blur' }],
  name: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  menuType: [{ required: true, message: '请选择菜单类型', trigger: 'blur' }],
  orderNum: [{ required: true, message: '请输入菜单排序', trigger: 'blur' }],
  status: [{ required: true, message: '请选择菜单状态', trigger: 'blur' }],
})

// 打开新增/编辑弹窗；编辑时先加载目录/菜单选项，再回显详情。
const openAddOrUpdateForm = (id) => {
  getMoptions()
  getCoptions()
  if (id) {
    title.value = '修改菜单'
    getMenuOneById(id).then(res => {
      if (res.code === 200) {
        ruleForm.value = {
          ...ruleForm.value,
          ...res.data,
          id: Number(res.data.id ?? 0),
          parentId: String(res.data.parentId ?? '0'),
          orderNum: Number(res.data.orderNum ?? 0),
          status: Number(res.data.status ?? 1)
        }
      }
    })
  } else {
    title.value = '新增菜单'
    handleCancel()
  }
  // 回显数据
  showForm.value = true

}

watch(
  () => ruleForm.value.menuType,
  (menuType) => {
    // 新增时根据菜单类型给出默认 parentId：
    // F 默认挂到按钮父菜单，C/M 默认先放根节点，减少手工选择成本。
    if (ruleForm.value.id) return
    if (menuType === 'F') {
      ruleForm.value.parentId = '2'
      return
    }
    if (menuType === 'C' || menuType === 'M') {
      ruleForm.value.parentId = '0'
    }
  }
)

const loadMenuTree = async () => {
  // 菜单管理页直接展示树形结构，而不是分页表格。
  loading.value = true
  try {
    const res = await getMentTreeData()
    const list = res?.data || []
    data.value = list
    defaultExpandedKeys.value = list.map(item => item.id)
  } finally {
    loading.value = false
  }
}

// 提交菜单表单：统一把数字字段转为 Number，再按是否有 id 区分新增/更新。
const handleSubmit = async () => {
  if (!ruleFormRef.value) return
  try {
    const valid = await ruleFormRef.value.validate()
    if (!valid) return

    const payload = {
      ...ruleForm.value,
      parentId: Number(ruleForm.value.parentId),
      orderNum: Number(ruleForm.value.orderNum),
      status: Number(ruleForm.value.status)
    }
    if (ruleForm.value.id) {
      const res = await updateMenu(payload)
      if (res.code === 200) {
        ElMessage.success(res.msg || res.message || '修改成功')
        handleCancel()
        await loadMenuTree()
        return
      }
      ElMessage.error(res.msg || res.message || '修改失败')
      return
    } else {
      const res = await addMenu(payload)
      if (res.code === 200) {
        ElMessage.success(res.msg || res.message || '提交成功')
        handleCancel()
        await loadMenuTree()
        return
      }
      ElMessage.error(res.msg || res.message || '提交失败')
    }


  } catch (err) {
    ElMessage.error(err?.message || '提交异常')
  }
}

// 删除前先确认；后端还会二次校验是否存在子菜单。
const removeData = async (node, data) => {
  try {
    await ElMessageBox.confirm(
      `是否确认删除${data.id}数据`,
      '提示',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )

    const res = await deleteMenu(data.id)
    if (res.code === 200) {
      ElMessage.success(res.msg || res.message || '删除成功')
      await loadMenuTree()
      return
    }
    ElMessage.error(res.msg || res.message || '删除失败')
  } catch (err) {
    if (err === 'cancel' || err === 'close') return
    ElMessage.error(err?.message || '删除异常')
  }
}



// 关闭弹窗并恢复默认表单。
const handleCancel = () => {
  showForm.value = false
  ruleForm.value = {
    id: 0,
    parentId: '0',
    path: '',
    name: '',
    component: '',
    status: 0,
    perms: '',
    menuType: '',
    orderNum: 0,
    icon: ''
  }
}



onMounted(() => {
  loadMenuTree()
})



</script>

<template>
  <el-button v-if="hasPerm('system:menu:add')" type="primary" @click="openAddOrUpdateForm()">
    新增
  </el-button>
  <el-card shadow="never" class="border-0">
    <el-tree style="max-width: 600px" :data="data" :props="{ label: 'name', children: 'children' }" node-key="id"
      :default-expanded-keys="defaultExpandedKeys" v-loading="loading">
      <template #default="{ node, data }">
        <div class="custom-tree-node">
          <span>{{ node.label }}</span>
        </div>
        <!-- 获取当前节点id，作为新增菜单的父级id -->
        <el-button v-if="hasPerm('system:menu:update')" type="primary" link @click="openAddOrUpdateForm(data.id)">
          修改
        </el-button>
        <el-button v-if="hasPerm('system:menu:delete')" type="danger" style="margin-left: 4px" link
          @click="removeData(node, data)">
          删除
        </el-button>
      </template>
    </el-tree>

  </el-card>

  <el-dialog :title="title" v-model="showForm" width="60%" @close="handleCancel">
    <el-form ref="ruleFormRef" style="max-width: 600px;" :model="ruleForm" :rules="rules" label-width="120px">
      <el-form-item label="菜单类型:" prop="menuType">
        <el-select v-model="ruleForm.menuType" placeholder="请选择菜单类型" style="width: 240px">
          <el-option v-for="item in options" :key="item.value" :label="item.label"
            :value="item.value == '' ? 'M' : item.value" />
        </el-select>
      </el-form-item>
      <!-- 根据菜单类型选择父级菜单 -->
      <el-form-item label="父级菜单:" v-if="ruleForm.menuType == 'C'">
        <el-select v-model="ruleForm.parentId" placeholder="请选择父类菜单" style="width: 240px">
          <el-option label="根目录" :value="'0'" />
          <el-option v-for="item in Moptions" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </el-form-item>

      <el-form-item label="父级菜单:" v-if="ruleForm.menuType == 'F'">
        <el-select v-model="ruleForm.parentId" placeholder="请选择父类菜单" style="width: 240px">
          <el-option v-for="item in Coptions" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </el-form-item>

      <el-form-item label="菜单路由:" prop="path">
        <el-input v-model="ruleForm.path" placeholder="请输入菜单路由" />
      </el-form-item>

      <el-form-item label="菜单组件路径:" prop="component">
        <el-input v-model="ruleForm.component" placeholder="请输入菜单组件路径" />
      </el-form-item>

      <el-form-item label="菜单名称:" prop="name">
        <el-input v-model="ruleForm.name" placeholder="请输入菜单名称" />
      </el-form-item>

      <el-form-item label="权限标识:" prop="perms">
        <el-input v-model="ruleForm.perms" placeholder="请输入权限标识" />
      </el-form-item>

      <el-form-item label="顺序号:" prop="orderNum">
        <el-input v-model="ruleForm.orderNum" placeholder="请输入顺序号" />
      </el-form-item>

      <el-form-item label="菜单状态:" prop="status">
        <el-radio-group v-model="ruleForm.status">
          <el-radio :label="0">正常</el-radio>
          <el-radio :label="1">停用</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item label="选择图标:" prop="icon">
        <el-popover placement="bottom-start" :width="360" trigger="click" v-model:visible="iconPickerVisible">
          <div class="icon-picker">
            <el-input v-model="iconSearch" placeholder="搜索图标" clearable size="small" />
            <div class="icon-grid">
              <button v-for="iconKey in filteredIconKeys" :key="iconKey" type="button" class="icon-item"
                :class="{ active: ruleForm.icon === iconKey }" @click="handleSelectIcon(iconKey)">
                <component :is="iconList[iconKey]" class="icon-svg"></component>
              </button>
            </div>
            <div v-if="filteredIconKeys.length === 0" class="icon-empty">
              无匹配图标
            </div>
          </div>
          <template #reference>
            <el-input v-model="ruleForm.icon" placeholder="点击选择图标" readonly>
              <template #prefix>
                <component v-if="ruleForm.icon && iconList[ruleForm.icon]" :is="iconList[ruleForm.icon]"
                  class="icon-preview"></component>
              </template>
            </el-input>
          </template>
        </el-popover>
      </el-form-item>

    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <el-button type="primary" @click="handleSubmit">确定</el-button>
        <el-button @click="handleCancel">取消</el-button>
      </div>
    </template>
  </el-dialog>


</template>

<style scoped>
.icon-picker {
  width: 100%;
}

.icon-grid {
  margin-top: 10px;
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 8px;
  max-height: 288px;
  overflow: auto;
  padding-right: 2px;
}

.icon-item {
  height: 40px;
  width: 40px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  background: var(--el-fill-color-blank);
  color: var(--el-text-color-primary);
  cursor: pointer;
  padding: 0;
}

.icon-item :deep(.el-icon) {
  font-size: 20px;
}

.icon-item :deep(svg) {
  width: 1em;
  height: 1em;
  display: block;
}

.icon-svg {
  width: 22px;
  height: 22px;
  display: block;
  color: inherit;
}

.icon-svg :deep(svg) {
  width: 100%;
  height: 100%;
  display: block;
}

.icon-preview {
  width: 18px;
  height: 18px;
  display: block;
  color: var(--el-text-color-primary);
}

.icon-preview :deep(svg) {
  width: 100%;
  height: 100%;
  display: block;
}

.icon-item:hover {
  border-color: var(--el-color-primary);
}

.icon-item.active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.icon-empty {
  margin-top: 10px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
