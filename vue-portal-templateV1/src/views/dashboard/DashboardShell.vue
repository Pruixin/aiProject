<template>
  <div class="dash">
    <header class="dash-header">
      <div class="brand" @click="go('/dashboard/fitness')">
        <div class="brand-mark" />
        <div class="brand-text">
          <div class="brand-name">FitFlow</div>
          <div class="brand-sub">健身 · 饮食 · 数据洞察</div>
        </div>
      </div>

      <nav class="nav">
        <RouterLink class="nav-item" to="/dashboard/fitness">训练</RouterLink>
        <RouterLink class="nav-item" to="/dashboard/diet">饮食</RouterLink>
        <RouterLink class="nav-item" to="/dashboard/profile">人群与推荐</RouterLink>
      </nav>

      <div class="actions">
        <el-button v-if="hasToken" class="plan-btn" @click="go('/plan')">方案生成</el-button>
        <el-button v-if="!hasToken" class="ghost" @click="go('/login')">登录</el-button>
        <el-button v-else class="ghost" @click="logout">退出</el-button>
      </div>
    </header>

    <main class="dash-main">
      <RouterView />
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { RouterLink, RouterView } from 'vue-router'

const router = useRouter()
const hasToken = computed(() => !!localStorage.getItem('token'))
const go = (path) => router.push(path)
const logout = () => {
  localStorage.removeItem('token')
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<style scoped lang="scss">
.dash {
  min-height: 100vh;
  background:
    radial-gradient(1200px 700px at 20% 0%, rgba(36, 207, 95, 0.18), rgba(10, 10, 10, 1)),
    radial-gradient(900px 600px at 80% 20%, rgba(109, 255, 154, 0.08), rgba(10, 10, 10, 0)),
    #0a0a0a;
  color: #fff;
}

.dash-header {
  height: 72px;
  padding: 0 26px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  position: sticky;
  top: 0;
  z-index: 200;
  background: rgba(10, 10, 10, 0.65);
  backdrop-filter: blur(16px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
  user-select: none;
}

.brand-mark {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  background: linear-gradient(135deg, #24cf5f, #6dff9a);
  box-shadow: 0 10px 28px rgba(36, 207, 95, 0.25);
}

.brand-name {
  font-size: 18px;
  font-weight: 900;
  letter-spacing: 1px;
}

.brand-sub {
  margin-top: 2px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.65);
}

.nav {
  display: flex;
  gap: 10px;
  padding: 6px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.04);
}

.nav-item {
  padding: 8px 14px;
  border-radius: 999px;
  color: rgba(255, 255, 255, 0.75);
  text-decoration: none;
  font-weight: 800;
  font-size: 13px;
}

.nav-item.router-link-active {
  color: #0a0a0a;
  background: linear-gradient(90deg, #24cf5f, #6dff9a);
}

.actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.actions .ghost {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.14);
  color: rgba(255, 255, 255, 0.85);
  border-radius: 14px;
  height: 40px;
  padding: 0 16px;
  font-weight: 800;
}

.actions .plan-btn {
  background: transparent;
  border: 1.5px solid rgba(36, 207, 95, 0.35);
  color: #24cf5f;
  border-radius: 14px;
  height: 40px;
  padding: 0 16px;
  font-weight: 800;
  transition: all 0.3s;

  &:hover {
    background: rgba(36, 207, 95, 0.08);
    border-color: #24cf5f;
    box-shadow: 0 0 18px rgba(36, 207, 95, 0.15);
  }
}

.dash-main {
  padding: 22px;
}
</style>
