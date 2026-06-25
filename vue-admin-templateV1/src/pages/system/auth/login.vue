<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { login } from '@/api/auth'
import { useUserTokenStore } from '@/stores/user'
import {router} from '@/router'

// 表单实例与数据
const ruleFormRef = ref()
const ruleForm = reactive({
  phone: '',
  password: ''
})
const loading = ref(false)

// 手机号校验规则
const validatePhone = (rule, value, callback) => {
  if (!value.trim()) {
    callback(new Error('手机号不能为空'))
  } else if (value.length < 3 || value.length > 20) {
    callback(new Error('手机号长度为3-20个字符'))
  } else if (!/^(13[0-9]|14[01456879]|15[0-35-9]|16[2567]|17[0-8]|18[0-9]|19[0-35-9])\d{8}$/.test(value)) {
    callback(new Error('请输入正确的手机号格式'))
  } else {
    // 简单放宽校验，方便测试
    callback()
  }
}

// 密码校验规则
const validatePassword = (rule, value, callback) => {
  if (!value.trim()) {
    callback(new Error('密码不能为空'))
  } else if (value.length < 6 || value.length > 20) {
    callback(new Error('密码长度为6-20个字符'))
  } else {
    callback()
  }
}

// 表单校验规则
const rules = reactive({
  phone: [{ validator: validatePhone, trigger: 'blur' }],
  password: [{ validator: validatePassword, trigger: 'blur' }]
})

// 提交登录表单
const submitForm = async (formEl) => {
  if (!formEl) return
  try {
    const valid = await formEl.validate()
    if (valid) {
      loading.value = true
      
      const res = await login(ruleForm)
      
      if (res.code === 200) {
        const tokenStore = useUserTokenStore()
        tokenStore.setToken(res.data.tokenName, res.data.tokenValue)
        ElMessage.success('登录成功')
        // 跳转到首页，用户信息获取由路由守卫(permission.js)自动处理
        router.push('/')
      } else {
        ElMessage.error(res.msg || '登录失败')
      }
    }
  } catch (err) {
    console.error('登录失败:', err)
    ElMessage.error(err.message || '登录异常')
  } finally {
    loading.value = false
  }
}

// 重置表单
const resetForm = (formEl) => {
  if (!formEl) return
  formEl.resetFields()
}
</script>

<template>
  <!-- 外层容器：撑满视口，居中对齐 -->
  <div class="layout-container">
    <el-row :gutter="24" class="content-row">
      <!-- 左侧品牌展示区 -->
      <el-col :xs="24" :md="12" :lg="16" :xl="17">
        <div class="left-layout">
          <div class="brand-box">
            <h1 class="brand-title">科学健身饮食后台管理系统</h1>
            <p class="brand-desc">专业的健身饮食管理平台，助力健康生活</p>
          </div>
        </div>
      </el-col>

      <!-- 右侧登录表单区 -->
      <el-col :xs="24" :md="12" :lg="8" :xl="7">
        <div class="right-layout">
          <div class="login-box">
            <h2 class="login-title">管理员登录</h2>
            <p class="login-subtitle">请输入账号密码登录系统</p>

            <!-- 登录表单 -->
            <el-form ref="ruleFormRef" :model="ruleForm" :rules="rules" status-icon label-width="60px"
              class="login-form">
              <el-form-item label="用户" prop="phone">
                <el-input v-model="ruleForm.phone" type="text" autocomplete="phone" placeholder="请输入手机号" size="large"
                  prefix-icon="User">
                  <el-icon>
                    <user />
                  </el-icon>
                </el-input>
              </el-form-item>

              <el-form-item label="密码" prop="password">
                <el-input v-model="ruleForm.password" type="password" autocomplete="current-password"
                  placeholder="请输入密码" size="large" prefix-icon="Lock" show-password>
                  <el-icon>
                    <lock />
                  </el-icon>
                </el-input>
              </el-form-item>

              <el-form-item class="login-btn-group">
                <el-button type="primary" @click="submitForm(ruleFormRef)" size="large" class="login-btn"
                  :loading="loading">
                  立即登录
                </el-button>
                <el-button @click="resetForm(ruleFormRef)" size="large" class="reset-btn">
                  重置表单
                </el-button>
              </el-form-item>
            </el-form>
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
/* 全局容器：撑满视口，消除默认边距，居中对齐 */
.layout-container {
  width: 100vw;
  height: 100vh;
  padding: 24px;
  box-sizing: border-box;
  background: linear-gradient(135deg, #f5f7fa 0%, #e4eaf5 100%);
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 内容行：适配容器宽度，限制最大宽度 */
.content-row {
  width: 100%;
  max-width: 1400px;
  height: 680px;
  margin: 0 auto;
}

/* 左侧品牌展示区：核心美化 */
.left-layout {
  width: 100%;
  height: 100%;
  background: linear-gradient(135deg, #4158d0 0%, #667eea 50%, #764ba2 100%);
  border-radius: 20px;
  padding: 60px 40px;
  box-sizing: border-box;
  box-shadow: 0 12px 32px rgba(65, 88, 208, 0.2);
  transition: all 0.4s ease;
  color: #fff;
  display: flex;
  align-items: center;
  position: relative;
  overflow: hidden;
}

/* 左侧装饰圆点 */
.left-layout::before {
  content: '';
  position: absolute;
  width: 300px;
  height: 300px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 50%;
  top: -100px;
  right: -100px;
}

.left-layout::after {
  content: '';
  position: absolute;
  width: 200px;
  height: 200px;
  background: rgba(255, 255, 255, 0.08);
  border-radius: 50%;
  bottom: -80px;
  left: -80px;
}

/* 品牌内容容器 */
.brand-box {
  z-index: 1;
  max-width: 500px;
}

.brand-title {
  font-size: 36px;
  font-weight: 700;
  margin: 0 0 24px 0;
  line-height: 1.3;
  letter-spacing: 2px;
}

.brand-desc {
  font-size: 18px;
  line-height: 1.8;
  margin: 0 0 32px 0;
  opacity: 0.9;
}

.brand-feature {
  display: flex;
  flex-direction: column;
  gap: 12px;
  font-size: 16px;
  opacity: 0.95;
}

/* 右侧登录表单区：核心美化 */
.right-layout {
  width: 100%;
  height: 100%;
  background: #ffffff;
  border-radius: 20px;
  padding: 40px;
  box-sizing: border-box;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.08);
  transition: all 0.4s ease;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 登录表单容器 */
.login-box {
  width: 100%;
  max-width: 380px;
}

.login-title {
  font-size: 28px;
  font-weight: 600;
  color: #333;
  margin: 0 0 8px 0;
  text-align: center;
}

.login-subtitle {
  font-size: 14px;
  color: #666;
  margin: 0 0 40px 0;
  text-align: center;
}

/* 登录表单样式 */
.login-form {
  width: 100%;
}

/* 表单项目间距 */
:deep(.el-form-item) {
  margin-bottom: 24px;
}

/* 输入框美化 */
:deep(.el-input__wrapper) {
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
  transition: all 0.3s ease;
}

:deep(.el-input__wrapper:hover) {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 2px rgba(65, 88, 208, 0.2);
}

/* 按钮组样式 */
.login-btn-group {
  margin-top: 32px;
  display: flex;
  gap: 16px;
}

.login-btn {
  flex: 1;
  border-radius: 12px;
  font-weight: 500;
  background: #4158d0;
  border-color: #4158d0;
}

.reset-btn {
  flex: 1;
  border-radius: 12px;
  font-weight: 500;
}

/* Hover交互：卡片上浮效果 */
.left-layout:hover,
.right-layout:hover {
  transform: translateY(-6px);
  box-shadow: 0 18px 40px rgba(65, 88, 208, 0.25);
}

/* 响应式优化：适配小屏幕 */
@media (max-width: 1024px) {
  .content-row {
    height: auto;
  }

  .left-layout,
  .right-layout {
    height: 500px;
  }

  .brand-title {
    font-size: 28px;
  }
}

@media (max-width: 768px) {
  .layout-container {
    padding: 16px;
  }

  .content-row {
    height: auto;
  }

  .left-layout,
  .right-layout {
    height: auto;
    min-height: 400px;
    padding: 32px 24px;
    border-radius: 16px;
  }

  .brand-title {
    font-size: 24px;
  }

  .brand-desc {
    font-size: 16px;
  }

  .login-title {
    font-size: 24px;
  }
}
</style>
