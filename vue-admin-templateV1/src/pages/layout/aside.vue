<script setup>
import { useUserInfoStore } from '@/stores/userInfo';
import { storeToRefs } from 'pinia'
import { useIndexStore } from '@/stores/index';
import {router} from '@/router';
import { computed } from 'vue';
import { useRoute } from 'vue-router';

const route = useRoute();
const userInfoStore = useUserInfoStore()
const indexStore = useIndexStore()

// 折叠状态
const { isCollapse } = storeToRefs(indexStore)

// 默认选择
const activeMenu = computed(() => route.path)

// 菜单数据
const { menus } = storeToRefs(userInfoStore)

const isDisplayableMenu = (menu) => {
    if (!menu) return false
    return menu.menuType !== 'F' && menu.visible === 0 && menu.status === 0
}

const normalizeMenus = (items = []) => {
    return items
        .filter(isDisplayableMenu)
        .map(item => {
            const children = normalizeMenus(item.children || [])
            return {
                ...item,
                children
            }
        })
        .filter(item => item.menuType !== 'M' || item.children.length > 0)
}

const sidebarMenus = computed(() => normalizeMenus(menus.value))

// 路径处理工具函数
const resolvePath = (basePath, routePath) => {
    // 如果是绝对路径，直接返回
    if (routePath.startsWith('/')) {
        return routePath
    }
    // 拼接路径
    const base = basePath.startsWith('/') ? basePath : `/${basePath}` 
    const path = routePath.startsWith('/') ? routePath : `/${routePath}`
    // 如果 base 是 /，则直接返回 path
    if (base === '/') return path
    return `${base}${path}`
}

// 处理菜单选中
const handleSelect = (index)=>{
    if (index === route.path) {
        indexStore.bumpRefresh()
        return
    }
    router.push(index)
}

</script>

<template>
    <div class="sports-sidebar" :class="{ 'is-collapse': isCollapse }">
        <el-menu 
            :default-active="activeMenu"
            @select="handleSelect" 
            :collapse="isCollapse" 
            :collapse-transition="true" 
            class="sports-menu"
            unique-opened
        >
            <template v-for="item in sidebarMenus" :key="item.id">
                <!-- 目录类型且有子菜单 -->
                <el-sub-menu 
                    v-if="item.children && item.children.length > 0" 
                    :index="resolvePath('', item.path)"
                >
                    <template #title>
                        <el-icon v-if="item.icon">
                            <component :is="item.icon"></component>
                        </el-icon>
                        <span>{{ item.name }}</span>
                    </template>
                    
                    <!-- 递归子菜单 (这里只处理了两级，如果需要多级可以封装为组件) -->
                        <el-menu-item 
                            v-for="child in item.children" 
                            :key="child.id" 
                            :index="resolvePath(item.path, child.path)"
                        >
                            <el-icon v-if="child.icon">
                                <component :is="child.icon"></component>
                            </el-icon>
                            <span>{{ child.name }}</span>
                        </el-menu-item>
                </el-sub-menu>

                <!-- 菜单类型 (没有子菜单的顶级菜单) -->
                <el-menu-item v-else :index="resolvePath('', item.path)">
                    <el-icon v-if="item.icon">
                        <component :is="item.icon"></component>
                    </el-icon>
                    <span>{{ item.name }}</span>
                </el-menu-item>
            </template>
        </el-menu>
    </div>
</template>

<style scoped>
/* 保持原有样式不变 */
.sports-sidebar :deep(.el-menu) {
    border: none;
    background-color: #fff;
    height: 100%;
    width: 100%;
    transition: all 0.3s cubic-bezier(0.645, 0.045, 0.355, 1);
}

.sports-sidebar :deep(.el-menu:not(.el-menu--collapse)) {
    width: 100%;
    transition: all 0.3s cubic-bezier(0.645, 0.045, 0.355, 1);
}

.sports-sidebar :deep(.el-menu-item),
.sports-sidebar :deep(.el-sub-menu__title) {
    background-color: #fff !important;
    margin: 4px 8px !important;
    border-radius: 6px !important;
    transition: all 0.2s ease !important;
    color: #303133 !important;
    height: 42px !important;
    line-height: 42px !important;
    display: flex !important;
    align-items: center !important;
    padding: 0 16px !important;
    border: 1px solid transparent !important;
}

.sports-sidebar :deep(.el-menu-item:hover),
.sports-sidebar :deep(.el-sub-menu__title:hover) {
    background-color: #ecf5ff !important;
    color: #409EFF !important;
    border-color: #d1e9ff !important;
}

.sports-sidebar :deep(.el-sub-menu .el-menu-item) {
    background-color: #fafafa !important;
    margin: 2px 12px !important;
    height: 40px !important;
    line-height: 40px !important;
    padding-left: 24px !important;
}

.sports-sidebar :deep(.el-sub-menu .el-menu-item:hover) {
    background-color: #ecf5ff !important;
    color: #409EFF !important;
    border-color: #d1e9ff !important;
}

.sports-sidebar :deep(.el-menu-item.is-active) {
    background-color: #409EFF !important;
    color: #fff !important;
    box-shadow: 0 2px 8px rgba(64, 158, 255, 0.2) !important;
}

/* 折叠状态菜单样式 */
.sports-sidebar :deep(.el-menu--collapse) {
    width: 100% !important;
}

.sports-sidebar :deep(.el-menu--collapse .el-sub-menu__title),
.sports-sidebar :deep(.el-menu--collapse .el-menu-item) {
    text-align: center !important;
    padding: 0 !important;
    justify-content: center !important;
    margin: 4px auto !important;
    width: 48px !important;
}

/* 折叠状态下隐藏文本 */
.sports-sidebar :deep(.el-menu--collapse .el-sub-menu__title span),
.sports-sidebar :deep(.el-menu--collapse .el-menu-item span) {
    display: none;
}

.sports-sidebar {
    background-color: #fff;
    border-right: 1px solid #e5e6eb;
    width: 250px;
    transition: width 0.3s cubic-bezier(0.645, 0.045, 0.355, 1);
    height: 100%;
    overflow: hidden;
    box-sizing: border-box;
}

.sports-sidebar.is-collapse {
    width: 64px;
}

.sports-sidebar :deep(.el-sub-menu .el-menu) {
    background-color: transparent !important;
}

.sports-sidebar :deep(.el-sub-menu__icon-arrow) {
    color: #909399 !important;
    margin-left: auto !important;
}

.sports-sidebar :deep(.el-sub-menu__title:hover .el-sub-menu__icon-arrow) {
    color: #409EFF !important;
}
</style>
