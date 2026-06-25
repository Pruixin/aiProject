<script setup>
import { getRoleList, getRoleOneById, addRole, updateRole, deleteRole, getRoleMenuById, saveRoleMenu } from '@/api/role'
import { getMentTreeData } from '@/api/menu'
import { ElMessage, ElMessageBox } from 'element-plus'
import { onMounted, ref, nextTick } from 'vue'
import { usePermission } from '@/composables/usePermission'

// 角色管理页：除基本 CRUD 外，最关键的是“角色-菜单树”授权回显与保存。
const ruleFormRef = ref(null)
const menuTreeRef = ref(null)
const { hasPerm } = usePermission()
const tableData = ref([])
// 菜单树用于角色授权抽屉
const menuTree = ref([])
const currentRoleId = ref(null)


// 列表 loading 状态
const Loading = ref(false)

// 授权抽屉中选中的菜单 id
const menuTreeForm = ref([])


const queryParams = ref({
    name: '',
    status: '',
    pageNum: 1,
    pageSize: 10
})
// 角色新增/编辑表单模型
const ruleForm = ref({
    name: '',
    roleKey: '',
    status: 0
})
// 一些通用 UI 状态
const showSearch = ref(true)
const size = ref('default')
const background = ref(false)
const disabled = ref(false)

// 新增/修改角色弹窗
const showForm = ref(false)
const title = ref('新增角色')
// 分配权限抽屉
const menuTreeShow = ref(false)
const checkStrictly = ref(false)

const flattenMenuIds = (nodes = []) => {
    return nodes.flatMap(item => {
        if (item == null) return []
        if (typeof item !== 'object') return [Number(item)]
        const currentId = item.id == null ? [] : [Number(item.id)]
        return [...currentId, ...flattenMenuIds(item.children || [])]
    })
}

// 总条数
const total = ref(0)
// 新增的检验规则
const rules = ref({
    name: [
        { required: true, message: "请输入角色名称", trigger: ['blur', 'change'] }
    ],
    roleKey: [
        { required: true, message: "请输入角色标识", trigger: ['blur', 'change'] }
    ],
    status: [
        { required: true, message: "请选择状态", trigger: ['blur', 'change'] }
    ]
})


// 搜索条件变化后重置到第一页重新查询
const handleSearch = () => {
    queryParams.value.pageNum = 1
    getList()
}
// 重置搜索条件
const resetSearch = () => {
    queryParams.value = {
        name: '',
        status: '',
        pageNum: 1,
        pageSize: 10
    }
    getList()
}

// 分页大小改变事件
const handleSizeChange = (val) => {
    queryParams.value.pageSize = val
    queryParams.value.pageNum = 1
    getList()
}
// 分页改变事件
const handleCurrentChange = (val) => {
    queryParams.value.pageNum = val
    getList()
}

// 打开新增/编辑弹窗；编辑时先回显角色详情。
const handleAddOrUpdate = (id) => {
    if (id) {
        title.value = '修改角色'
        getRoleOneById(id).then(res => {
            if (res.code === 200) {
                ruleForm.value = {
                    ...ruleForm.value,
                    ...res.data,
                    id: Number(res.data.id ?? 0),
                    status: Number(res.data.status ?? 0)
                }
            }
        })
    } else {
        title.value = '新增角色'
    }
    showForm.value = true
}

// 关闭弹窗并重置表单
const handleCancel = () => {
    ruleForm.value = {
        name: '',
        roleKey: '',
        status: 0
    }
    showForm.value = false
}



// 获取角色分页列表
const getList = () => {
    Loading.value = true
    getRoleList(queryParams.value).then(res => {
        tableData.value = res.data.data
        total.value = res.data.total
        Loading.value = false
    })
}
// 提交角色表单：根据是否有 id 分别走新增或更新。
const handleSubmit = async () => {
    if (!ruleFormRef.value) return
    try {
        const valid = await ruleFormRef.value.validate()
        if (valid) {
            const payload = {
                ...ruleForm.value,
                status: Number(ruleForm.value.status)
            }
            if (ruleForm.value.id) {
                const res = await updateRole(payload)
                if (res.code === 200) {
                    ElMessage.success('修改成功')
                    handleCancel()
                    getList()
                } else {
                    ElMessage.error(res.msg || '修改失败')
                }
            } else {
                const res = await addRole(payload)
                if (res.code === 200) {
                    ElMessage.success('新增成功')
                    handleCancel()
                    getList()
                } else {
                    ElMessage.error(res.msg || '新增失败')
                }
            }
        }
    } catch (error) {
        console.error('表单校验失败', error)
    }
}
// 删除角色前先确认，避免误删。
const handleDelete = async (id) => {
    try {
        await ElMessageBox.confirm('是否确认删除' + id + '该角色？', '提示', {
            type: 'warning'
        })
        const res = await deleteRole(id)
        if (res.code === 200) {
            ElMessage.success('删除成功')
            getList()
        } else {
            ElMessage.error(res.msg || '删除失败')
        }
    } catch (error) {
        if (error !== 'cancel') {
            console.error('删除异常', error)
        }
    }
}
// 权限分配回显是这里最容易出错的部分：
// 先开启严格模式回显勾选结果，避免树组件父子联动把数据库里“只选子节点”的状态污染掉；
// 回显完成后再关闭严格模式，恢复用户交互时的联动体验。
const handlePower = async (id) => {
    currentRoleId.value = Number(id)
    checkStrictly.value = true 
    menuTreeShow.value = true 

    try {
        const [menuTreeRes, roleMenuRes] = await Promise.all([
            getMentTreeData(),
            getRoleMenuById(id)
        ])

        if (menuTreeRes.code === 200) menuTree.value = menuTreeRes.data

        if (roleMenuRes.code === 200) {
            const allIds = [...new Set(flattenMenuIds(roleMenuRes.data || []))]
            menuTreeForm.value = allIds
            
            await nextTick()
            if (menuTreeRef.value) {
                menuTreeRef.value.setCheckedKeys([])
                menuTreeRef.value.setCheckedKeys(allIds)
                
                await nextTick()
                checkStrictly.value = false
            }
        }
    } catch (err) {
        console.error('权限回显失败:', err)
    }
}

// 提交权限分配时同时提交全选和半选节点，才能完整表达树形授权结果。
const handleSavePower = async () => {
    const checkedKeys = menuTreeRef.value.getCheckedKeys()
    const halfCheckedKeys = menuTreeRef.value.getHalfCheckedKeys()
    const menuIds = [...new Set([...checkedKeys, ...halfCheckedKeys])]
    if (menuIds.length === 0) {
        try {
            await ElMessageBox.confirm('当前未选择任何菜单，保存后该角色将不再拥有菜单权限，是否继续？', '空授权确认', {
                type: 'warning'
            })
        } catch (error) {
            return
        }
    }
    
    const res = await saveRoleMenu(currentRoleId.value, menuIds)
    if (res.code === 200) {
        ElMessage.success('分配成功')
        menuTreeShow.value = false
    } else {
        ElMessage.error(res.msg || '分配失败')
    }
}

const defaultProps={
    children:'children',
    label:'name'
}



onMounted(() => {
    getList()
})

</script>

<template>
    <div class="app-container">
        <el-form :model="queryParams" ref="queryForm" size="small" :inline="true" v-show="showSearch"
            label-width="68px">
            <el-form-item label="角色名称" prop="name">
                <el-input v-model="queryParams.name" placeholder="请输入角色名称" clearable />
            </el-form-item>
            <el-form-item label="状态" prop="status">
                <el-radio-group v-model="queryParams.status">
                    <el-radio label="">全部</el-radio>
                    <el-radio :label="0">正常</el-radio>
                    <el-radio :label="1">停用</el-radio>
                </el-radio-group>
            </el-form-item>
            <el-form-item>
                <el-button type="primary" icon="el-icon-search" size="small" @click="handleSearch">查询</el-button>
                <el-button type="reset" icon="el-icon-refresh" size="small" @click="resetSearch">重置</el-button>
            </el-form-item>
        </el-form>

        <el-row :gutter="10" class="toolbar">
            <el-col v-if="hasPerm('system:role:add')" :span="4">
                <el-button type="primary" @click="handleAddOrUpdate()">新增</el-button>
            </el-col>
        </el-row>

        <el-table v-loading="Loading" :data="tableData" stripe style="width: 100%">
            <el-table-column prop="id" label="ID" align="center" min-width="100" />
            <el-table-column prop="name" label="名称" align="center" min-width="140" />
            <el-table-column prop="roleKey" label="角色标识" align="center" min-width="140" />
            <!-- 0正常 1停用 -->
            <el-table-column prop="status" label="状态" align="center" min-width="100">
                <template #default="scope">
                    <el-tag :type="scope.row.status === 0 ? 'success' : 'danger'">
                        {{ scope.row.status === 0 ? '正常' : '停用' }}
                    </el-tag>
                </template>
            </el-table-column>
            <el-table-column prop="createTime" label="创建时间" align="center" min-width="170" />
            <el-table-column prop="updateTime" label="更新时间" align="center" min-width="170" />
            <el-table-column prop="createBy" label="创建人" align="center" min-width="110" />
            <el-table-column prop="updateBy" label="更新人" align="center" min-width="110" />
            <el-table-column label="操作" width="220" fixed="right" align="center">
                <template #default="scope">
                    <el-button v-if="hasPerm('system:role:update')" type="primary" link
                        @click="handleAddOrUpdate(scope.row.id)">编辑</el-button>
                    <el-button v-if="hasPerm('system:role:update')" type="primary" link
                        @click="handlePower(scope.row.id)">分配权限</el-button>
                    <el-button v-if="hasPerm('system:role:delete')" type="danger" link
                        @click="handleDelete(scope.row.id)">删除</el-button>
                </template>
            </el-table-column>
        </el-table>
        <div class="pagination-wrap">
            <el-pagination v-model:current-page="queryParams.pageNum" v-model:page-size="queryParams.pageSize"
                :page-sizes="[20, 50, 100, 150]" :size="size" :disabled="disabled" :background="background"
                layout="total, sizes, prev, pager, next, jumper" :total="total" @size-change="handleSizeChange"
                @current-change="handleCurrentChange" />
        </div>

        <!-- 新增/修改角色弹窗 -->
        <el-dialog :title="title" v-model="showForm" width="60%" @close="handleCancel">
            <el-form ref="ruleFormRef" style="max-width: 600px;" :model="ruleForm" :rules="rules" label-width="96px">
                <el-form-item label="名称" prop="name">
                    <el-input v-model="ruleForm.name" placeholder="请输入角色名称" clearable />
                </el-form-item>
                <el-form-item label="角色标识" prop="roleKey">
                    <el-input v-model="ruleForm.roleKey" placeholder="请输入角色标识" clearable />
                </el-form-item>
                <el-form-item label="状态" prop="status">
                    <el-radio-group v-model="ruleForm.status">
                        <el-radio :label="0">正常</el-radio>
                        <el-radio :label="1">停用</el-radio>
                    </el-radio-group>
                </el-form-item>

            </el-form>

            <template #footer>
                <div class="dialog-footer">
                    <el-button type="primary" @click="handleSubmit">确定</el-button>
                    <el-button @click="handleCancel">取消</el-button>
                </div>
            </template>
        </el-dialog>

        <!-- 分配权限抽屉弹框 -->
        <el-drawer v-model="menuTreeShow" title="权限分配" direction="rtl" size="50%">
            <el-tree 
            ref="menuTreeRef"
             :check-strictly="checkStrictly"
            style="max-width: 600px" 
            :data="menuTree" 
            show-checkbox 
            node-key="id" 
            default-expand-all
            :props="defaultProps" />
            
            <template #footer>
                <div style="flex: auto">
                    <el-button @click="menuTreeShow = false">取消</el-button>
                    <el-button type="primary" @click="handleSavePower">确定</el-button>
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
</style>
