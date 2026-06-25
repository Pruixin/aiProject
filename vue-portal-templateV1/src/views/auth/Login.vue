<template>
  <div class="login-container">
    <div class="login-card">
      <div class="login-header">
        <h2 class="title">FitFlow 悦动健身</h2>
        <p class="subtitle">让运动成为一种习惯</p>
      </div>
      <el-form :model="loginForm" :rules="loginRules" ref="loginFormRef" class="login-form">
        <el-form-item prop="phone">
          <el-input v-model="loginForm.phone" placeholder="手机号" prefix-icon="Iphone" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="loginForm.password" type="password" placeholder="密码" prefix-icon="Lock" show-password />
        </el-form-item>
        <div class="login-options">
          <el-button type="primary" class="login-button" @click="handleLogin" :loading="loading">
            登录
          </el-button>
          <div class="register-link">
            还没有账号？ <router-link to="/register">立即注册</router-link>
          </div>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import request from '../../utils/request';

// 门户登录页：登录成功后不仅保存 token，还会预拉一次 `/auth/getInfo`
// 把昵称和头像写入 localStorage，供顶栏壳层直接展示。
const router = useRouter();
// 按钮 loading，防止用户重复点击触发多次登录请求。
const loading = ref(false);
// Element Plus 表单实例，用于主动触发表单校验。
const loginFormRef = ref(null);

const persistPortalUser = (user = {}) => {
  if (user.nickName) {
    localStorage.setItem('nickName', user.nickName)
  } else {
    localStorage.removeItem('nickName')
  }
  if (user.avatar) {
    localStorage.setItem('avatar', user.avatar)
  } else {
    localStorage.removeItem('avatar')
  }
  localStorage.setItem('avatarUpdatedAt', String(Date.now()))
}

const clearPortalSession = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('roleList')
  localStorage.removeItem('nickName')
  localStorage.removeItem('avatar')
  localStorage.removeItem('avatarUpdatedAt')
}

const loginForm = reactive({
  phone: '',
  password: ''
});

const loginRules = {
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
};

const handleLogin = async () => {
  // 表单尚未挂载完成时直接退出。
  if (!loginFormRef.value) return;

  await loginFormRef.value.validate(async (valid) => {
    if (valid) {
      // 校验通过后进入提交态。
      loading.value = true;
      try {
        // 登录接口只提交手机号和密码。
        const res = await request.post('/auth/login', loginForm);
        // 后端返回 Sa-Token 信息，这里只持久化前端需要的 tokenValue。
        localStorage.setItem('token', res.data.tokenValue);
        try {
          // 继续请求当前用户信息，避免进入首页后还要额外等待头像和昵称加载。
          const infoRes = await request.get('/auth/getInfo')
          const roleList = Array.isArray(infoRes.data?.roleList) ? infoRes.data.roleList : []
          if (!roleList.includes('users')) {
            clearPortalSession()
            ElMessage.error('当前账号没有门户访问权限，请使用普通用户账号登录')
            return
          }
          localStorage.setItem('roleList', JSON.stringify(roleList))
          persistPortalUser(infoRes.data?.user || {})
        } catch (error) {
          clearPortalSession()
          ElMessage.error(error?.message || '登录后权限校验失败')
          return
        }
        ElMessage.success('登录成功');
        router.push('/plan');
      } catch (error) {
        console.error('Login error:', error);
      } finally {
        // 无论成功失败都关闭按钮 loading。
        loading.value = false;
      }
    }
  });
};
</script>

<style scoped lang="scss">
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background-color: #111;
  background-image: linear-gradient(rgba(0, 0, 0, 0.6), rgba(0, 0, 0, 0.6)), url('https://images.unsplash.com/photo-1534438327276-14e5300c3a48?ixlib=rb-1.2.1&auto=format&fit=crop&w=1350&q=80');
  background-size: cover;
  background-position: center;
}

.login-card {
  width: 400px;
  padding: 40px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 20px;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.3);
  backdrop-filter: blur(10px);
}

.login-header {
  text-align: center;
  margin-bottom: 30px;

  .title {
    font-size: 28px;
    color: #333;
    margin-bottom: 10px;
    font-weight: 700;
  }

  .subtitle {
    color: #666;
    font-size: 14px;
  }
}

.login-form {
  :deep(.el-input__wrapper) {
    background: #f5f5f5;
    box-shadow: none;
    border-radius: 10px;
    padding: 8px 15px;

    &.is-focus {
      box-shadow: 0 0 0 1px #24cf5f inset;
    }
  }
}

.login-button {
  width: 100%;
  height: 45px;
  border-radius: 10px;
  background-color: #24cf5f;
  border-color: #24cf5f;
  font-size: 16px;
  font-weight: 600;
  margin-top: 10px;

  &:hover {
    background-color: #1fb954;
    border-color: #1fb954;
  }
}

.register-link {
  text-align: center;
  margin-top: 20px;
  font-size: 14px;
  color: #666;

  a {
    color: #24cf5f;
    text-decoration: none;
    font-weight: 600;

    &:hover {
      text-decoration: underline;
    }
  }
}
</style>
