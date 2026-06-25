<script setup>
import { useUserInfoStore } from '@/stores/userInfo'
import { useRouter } from 'vue-router'

const router = useRouter()
const userInfoStore = useUserInfoStore()

const goHome = () => {
    const menus = userInfoStore.menus || []
    const findFirstPath = (items = [], parentPath = '') => {
        for (const item of items) {
            if (!item || item.menuType === 'F' || item.visible !== 0 || item.status !== 0) continue
            const currentPath = `${parentPath}/${String(item.path || '').replace(/^\/+|\/+$/g, '')}`.replace(/\/+/g, '/')
            const childPath = findFirstPath(item.children || [], currentPath)
            if (childPath) return childPath
            if (item.path) return currentPath
        }
        return ''
    }
    const firstPath = findFirstPath(menus)
    router.push(firstPath || '/login')
}
</script>

<template>
    <div>
        <el-col :sm="12" :lg="6" :xl="4">
            <el-result icon="warning" title="404页面未找到" sub-title="NotFound">
                <template #extra>
                    <el-button type="primary" @click="goHome">返回首页</el-button>
                </template>
            </el-result>
        </el-col>
    </div>

</template>
