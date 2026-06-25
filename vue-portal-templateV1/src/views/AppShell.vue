<template>
  <div class="app-shell">
    <header class="shell-header">
      <div class="brand" @click="go('/plan')">
        <div class="brand-mark" />
        <div class="brand-text">
          <div class="brand-name">FitFlow</div>
          <div class="brand-sub">悦动健身</div>
        </div>
      </div>

      <nav class="primary-nav">
        <RouterLink class="nav-item" to="/plan">饮食与健身计划</RouterLink>
        <RouterLink class="nav-item" to="/social">社交模块</RouterLink>
      </nav>

      <div class="user-area">
        <el-dropdown>
          <span class="user-trigger">
            <el-avatar v-if="avatarUrl" :size="34" :src="avatarUrl" />
            <el-avatar v-else :size="34" icon="User" />
            <span class="user-name">{{ nickName }}</span>
            <el-icon>
              <ArrowDown />
            </el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item @click="go('/dashboard/fitness')">数据大屏</el-dropdown-item>
              <el-dropdown-item @click="openProfile">个人信息</el-dropdown-item>
              <el-dropdown-item @click="handleLogout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <main class="shell-main">
      <RouterView />
    </main>

    <el-dialog v-model="profileVisible" title="个人信息" width="520px" class="profile-dialog" :close-on-click-modal="false">
      <div v-loading="profileLoading" class="profile-body">
        <div class="profile-avatar-row">
          <el-avatar :size="72" :src="profileForm.avatar" v-if="profileForm.avatar" />
          <el-avatar :size="72" v-else>
            <el-icon :size="36">
              <UserFilled />
            </el-icon>
          </el-avatar>
          <el-button class="avatar-upload-btn" @click="triggerAvatarUpload">
            <el-icon>
              <Camera />
            </el-icon> 更换头像
          </el-button>
          <input type="file" ref="avatarInput" accept="image/*" style="display:none" @change="handleAvatarUpload" />
        </div>

        <el-form :model="profileForm" label-position="top" class="profile-form">
          <el-form-item label="昵称">
            <el-input v-model="profileForm.nickName" placeholder="请输入昵称" maxlength="20" show-word-limit />
          </el-form-item>

          <el-form-item label="新密码">
            <el-input v-model="profileForm.password" type="password" placeholder="留空则不修改密码" show-password />
          </el-form-item>

          <el-form-item label="手机号">
            <el-input v-model="profileForm.phone" disabled placeholder="—" />
          </el-form-item>

          <el-form-item label="性别">
            <el-input v-model="sexDisplay" disabled placeholder="—" />
          </el-form-item>
        </el-form>
      </div>

      <template #footer>
        <el-button @click="profileVisible = false">取消</el-button>
        <el-button type="primary" class="profile-save-btn" @click="handleSaveProfile"
          :loading="profileSaving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowDown, UserFilled, Camera } from '@element-plus/icons-vue'
import { useRouter, RouterLink, RouterView } from 'vue-router'
import request from '../utils/request'

// 门户主壳层：负责顶部导航、用户头像昵称展示，以及 plan/social/dashboard 三块页面的切换承载。
const router = useRouter()
// 昵称直接从 localStorage 读取，登录后无需额外请求即可展示。
const nickName = computed(() => localStorage.getItem('nickName') || '我的账号')
const avatarUrl = computed(() => {
  // avatar 也是登录后预拉 `/auth/getInfo` 时写入本地缓存的。
  const avatar = localStorage.getItem('avatar')
  if (!avatar) return ''
  // 通过 avatarUpdatedAt 拼接版本参数，解决头像更新后浏览器缓存不刷新的问题。
  const resolved = avatar.startsWith('http') ? avatar : avatar
  const version = localStorage.getItem('avatarUpdatedAt')
  return version ? `${resolved}${resolved.includes('?') ? '&' : '?'}v=${version}` : resolved
})

const profileVisible = ref(false)
const profileLoading = ref(false)
const profileSaving = ref(false)
const avatarInput = ref(null)
const profileForm = reactive({
  avatar: '',
  nickName: '',
  password: '',
  phone: '',
  sex: null,
  location: ''
})

const sexDisplay = computed({
  get: () => {
    if (profileForm.sex === 0) return '男'
    if (profileForm.sex === 1) return '女'
    return '未知'
  },
  set: () => { }
})

const openProfile = async () => {
  profileVisible.value = true
  profileLoading.value = true
  profileForm.password = ''
  try {
    const res = await request.get('/auth/profile')
    const data = res.data || {}
    profileForm.avatar = data.avatar || ''
    profileForm.nickName = data.nickName || ''
    profileForm.phone = data.phone || ''
    profileForm.sex = data.sex
    profileForm.location = data.location || ''
  } catch (e) {
    ElMessage.error('获取个人信息失败')
  } finally {
    profileLoading.value = false
  }
}

const triggerAvatarUpload = () => {
  avatarInput.value?.click()
}

const handleAvatarUpload = async (e) => {
  const file = e.target.files?.[0]
  if (!file) {
    e.target.value = ''
    return
  }
  const formData = new FormData()
  formData.append('file', file)
  try {
    const res = await request.post('/common/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    profileForm.avatar = res.data?.url || ''
    ElMessage.success('头像上传成功')
  } catch (err) {
    ElMessage.error('头像上传失败')
  }
  e.target.value = ''
}

const handleSaveProfile = async () => {
  profileSaving.value = true
  try {
    const body = {}
    if (profileForm.avatar) body.avatar = profileForm.avatar
    if (profileForm.nickName) body.nickName = profileForm.nickName
    if (profileForm.password) body.password = profileForm.password
    await request.put('/auth/profile', body)
    if (profileForm.nickName) {
      localStorage.setItem('nickName', profileForm.nickName)
    }
    if (profileForm.avatar) {
      localStorage.setItem('avatar', profileForm.avatar)
      localStorage.setItem('avatarUpdatedAt', String(Date.now()))
    }
    ElMessage.success('个人信息更新成功')
    profileVisible.value = false
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    profileSaving.value = false
  }
}

// 顶部品牌和下拉菜单都复用这个跳转方法，保持模板层更简洁。
const go = (path) => router.push(path)

const handleLogout = () => {
  // 退出登录时清理门户端缓存的登录态和用户展示信息。
  localStorage.removeItem('token')
  localStorage.removeItem('roleList')
  localStorage.removeItem('nickName')
  localStorage.removeItem('avatar')
  localStorage.removeItem('avatarUpdatedAt')
  ElMessage.success('已退出登录')
  // 清完缓存后回登录页，后续受保护路由会被守卫再次拦住。
  router.push('/login')
}
</script>

<style scoped lang="scss">
.app-shell {
  min-height: 100vh;
  background:
    radial-gradient(1000px 700px at 10% 0%, rgba(36, 207, 95, 0.14), rgba(10, 10, 10, 0)),
    radial-gradient(900px 700px at 90% 10%, rgba(109, 255, 154, 0.08), rgba(10, 10, 10, 0)),
    #0a0a0a;
  color: #fff;
}

.shell-header {
  position: sticky;
  top: 0;
  z-index: 200;
  height: 74px;
  padding: 0 28px;
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 16px;
  background: rgba(10, 10, 10, 0.72);
  backdrop-filter: blur(16px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.brand {
  width: fit-content;
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
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
  font-size: 12px;
  color: rgba(255, 255, 255, 0.62);
}

.primary-nav {
  justify-self: center;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
}

.nav-item {
  padding: 9px 18px;
  border-radius: 999px;
  text-decoration: none;
  color: rgba(255, 255, 255, 0.76);
  font-size: 14px;
  font-weight: 800;
}

.nav-item.router-link-active {
  color: #0a0a0a;
  background: linear-gradient(90deg, #24cf5f, #6dff9a);
}

.user-area {
  justify-self: end;
}

.user-trigger {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: rgba(255, 255, 255, 0.88);
  cursor: pointer;
  user-select: none;
}

.user-name {
  font-weight: 800;
}

.shell-main {
  padding: 22px;
}

@media (max-width: 900px) {
  .shell-header {
    height: auto;
    padding: 14px 16px;
    grid-template-columns: 1fr;
  }

  .brand,
  .primary-nav,
  .user-area {
    justify-self: center;
  }

  .primary-nav {
    width: 100%;
    justify-content: center;
    flex-wrap: wrap;
  }
}
</style>

<style lang="scss">
.el-overlay-dialog .profile-dialog {
  .el-dialog {
    background: #0d0d0d;
    border-radius: 20px;
    border: 1px solid rgba(36, 207, 95, 0.12);
    box-shadow: 0 24px 80px rgba(0, 0, 0, 0.7), 0 0 0 1px rgba(36, 207, 95, 0.06);
    overflow: hidden;
  }

  .el-dialog__header {
    padding: 24px 28px 16px;
    margin: 0;
    border-bottom: 1px solid rgba(36, 207, 95, 0.15);
    background: linear-gradient(180deg, rgba(36, 207, 95, 0.04) 0%, transparent 100%);
  }

  .el-dialog__title {
    font-size: 18px;
    font-weight: 800;
    color: #24cf5f;
    letter-spacing: 0.5px;
  }

  .el-dialog__headerbtn {
    top: 24px;
    right: 24px;

    .el-dialog__close {
      color: rgba(255, 255, 255, 0.35);
      font-size: 20px;
      transition: color 0.2s;

      &:hover {
        color: #24cf5f;
      }
    }
  }

  .el-dialog__body {
    padding: 24px 28px 16px;
  }

  .el-dialog__footer {
    padding: 0 28px 24px;
  }

  .profile-avatar-row {
    display: flex;
    align-items: center;
    gap: 20px;
    margin-bottom: 28px;
    padding-bottom: 24px;
    border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  }

  .avatar-upload-btn {
    border: 1.5px solid rgba(36, 207, 95, 0.3);
    background: transparent;
    color: #24cf5f;
    border-radius: 10px;
    padding: 8px 18px;
    font-size: 13px;
    font-weight: 600;
    transition: all 0.25s;

    &:hover {
      background: rgba(36, 207, 95, 0.08);
      border-color: #24cf5f;
      box-shadow: 0 0 16px rgba(36, 207, 95, 0.12);
    }
  }

  .profile-form {
    .el-form-item__label {
      color: rgba(255, 255, 255, 0.5);
      font-weight: 600;
      font-size: 13px;
    }

    .el-input__wrapper {
      background: #1a1a1a;
      box-shadow: none;
      border-radius: 10px;
      border: 1px solid rgba(255, 255, 255, 0.08);
      transition: border-color 0.25s;

      &:hover {
        border-color: rgba(255, 255, 255, 0.15);
      }
    }

    .el-input.is-disabled .el-input__wrapper {
      background: #111;
      border-color: rgba(255, 255, 255, 0.04);
      opacity: 0.6;

      .el-input__inner {
        color: rgba(255, 255, 255, 0.3);
        -webkit-text-fill-color: rgba(255, 255, 255, 0.3);
      }
    }

    .el-input__inner {
      color: #fff;
    }
  }

  .profile-save-btn {
    background: #24cf5f;
    border: none;
    border-radius: 10px;
    padding: 8px 28px;
    font-weight: 700;

    &:hover {
      background: #1fb954;
    }
  }

  .el-button:not(.profile-save-btn) {
    background: transparent;
    border: 1px solid rgba(255, 255, 255, 0.12);
    color: rgba(255, 255, 255, 0.6);
    border-radius: 10px;

    &:hover {
      border-color: rgba(255, 255, 255, 0.25);
      color: rgba(255, 255, 255, 0.85);
    }
  }
}
</style>
