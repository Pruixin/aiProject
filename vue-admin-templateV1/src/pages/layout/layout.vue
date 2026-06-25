<script setup>
import { RouterView } from 'vue-router';
import Header from './header.vue'
import Aside from './aside.vue';
import { useIndexStore } from '@/stores/index';
import { storeToRefs } from 'pinia';
import { computed } from 'vue';
import { useRoute } from 'vue-router';

const indexStore = useIndexStore();
const { isCollapse, refreshTick } = storeToRefs(indexStore);
const route = useRoute();

const viewKey = computed(() => `${route.fullPath}::${refreshTick.value}`)
</script>

<template>
    <el-container class="sports-layout">
        <el-header>
            <Header />
        </el-header>

        <el-container direction="horizontal">
            <el-aside :width="isCollapse ? '64px' : '250px'">
                <Aside />
            </el-aside>
            <el-main>
                <RouterView :key="viewKey" />
            </el-main>
        </el-container>
    </el-container>

</template>

<style scoped>
.sports-layout {
    background-color: #f0f2f5;
    min-height: 100vh;
    font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', SimSun, sans-serif;
}

.el-header {
    background-color: #fff;
    padding: 0;
    box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
    height: 60px !important;
}

.el-aside {
    background-color: #fff;
    box-shadow: 2px 0 6px rgba(0, 21, 41, 0.1);
    transition: all 0.3s ease;
}

.el-main {
    background-color: #f5f7fa;
    padding: 20px;
    min-height: calc(100vh - 60px);
}

/* 响应式设计 */
@media (max-width: 768px) {
    .el-aside {
        width: 64px !important;
    }
}
</style>
