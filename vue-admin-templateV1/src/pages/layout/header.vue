<script setup>
import { router } from '@/router';
import { useUserTokenStore } from '@/stores/user';
import { useUserInfoStore } from '@/stores/userInfo';
import { useIndexStore } from '@/stores/index';
import { computed } from 'vue';
import { storeToRefs } from 'pinia';

// 创建 store 实例
const indexStore = useIndexStore()
// 获取用户信息仓库
const userInfoStore = useUserInfoStore()

// 使用 toRefs 解构 isCollapse 状态
const { isCollapse } = storeToRefs(indexStore)

// 用户头像
const { user } = storeToRefs(userInfoStore)

// 处理头像路径，如果是本地资源需要用 new URL 引入
const defaultAvatar = new URL('@/icons/user.svg', import.meta.url).href
const avatarValue = computed(() => {
    const avatar = user.value.avatar
    if (!avatar) return defaultAvatar
    if (avatar.startsWith('http')) return avatar
    return avatar.startsWith('/') ? avatar : `/${avatar}`
})
const nickNameValue = computed(() => user.value.nickName || '用户')


// 切换菜单折叠状态
const switchIcon = () => {
    indexStore.toggleCollapse()
}

// 退出登录
const logout = () => {
    useUserTokenStore().removeToken()
    userInfoStore.clearUserInfo()
    router.push('/login')
}
</script>

<template>
    <!-- 头部容器 -->
    <div class="header-container">
        <!-- 左侧：标题 + 图标切换按钮 -->
        <div class="header-left">
            <span class="system-title">科学健身饮食后台管理系统</span>
            <div class="icon-switch-container" @click="switchIcon">
                <div class="icon-wrapper" v-if="!isCollapse">
                    <img src="@/icons/shousuo.svg" alt="图标1" class="switch-icon">
                </div>
                <div class="icon-wrapper" v-else>
                    <img src="@/icons/zhankai.svg" alt="图标2" class="switch-icon">
                </div>
            </div>
        </div>

        <!-- 右侧：头像 + 用户信息下拉菜单 -->
        <div class="header-right">
            <el-avatar :size="50" :src="avatarValue" class="user-avatar">
                <!-- {{ userInfoStore.userInfo?.username?.[0] || 'U' }} -->
            </el-avatar>
            <el-dropdown>
                <span class="dropdown-trigger">
                    {{ nickNameValue }}
                    <i class="el-icon-arrow-down"></i>
                </span>
                <template #dropdown>
                    <el-dropdown-menu class="dropdown-menu">
                        <el-dropdown-item @click="logout" class="logout-item">退出登录</el-dropdown-item>
                    </el-dropdown-menu>
                </template>
            </el-dropdown>
        </div>
    </div>
</template>

<style scoped>
.header-container {
    display: flex;
    justify-content: space-between;
    align-items: center;
    height: 100%;
    padding: 0 20px;
    background-color: #fff;
    border-bottom: 1px solid #dcdfe6;
    box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}

.header-left {
    display: flex;
    align-items: center;
    gap: 20px;
}

.system-title {
    font-size: 1.2rem;
    font-weight: 600;
    color: #303133;
    letter-spacing: 1px;
}

.icon-switch-container {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 36px;
    height: 36px;
    border-radius: 4px;
    background-color: #f5f7fa;
    cursor: pointer;
    transition: background-color 0.3s;
    border: 1px solid #dcdfe6;
}

.icon-switch-container:hover {
    background-color: #e6f7ff;
}

.icon-wrapper {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 100%;
    height: 100%;
}

.switch-icon {
    width: 20px;
    height: 20px;
    filter: brightness(0) saturate(100%) invert(32%) sepia(2%) saturate(915%) hue-rotate(196deg) brightness(98%) contrast(92%);
    /* 简洁蓝色调 */
}

.header-right {
    display: flex;
    align-items: center;
    gap: 15px;
}

.user-avatar {
    border: 1px solid #fff;
}

.dropdown-trigger {
    color: #606266;
    font-weight: 500;
    cursor: pointer;
    display: flex;
    align-items: center;
    gap: 5px;
    padding: 5px 10px;
    border-radius: 4px;
    transition: background-color 0.3s;
}

.dropdown-trigger:hover {
    background-color: #f5f7fa;
}

.dropdown-menu {
    margin-top: 5px;
    border: 1px solid #e4e7ed;
    box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.1);
}

.logout-item {
    color: #f56c6c !important;
}
</style>
