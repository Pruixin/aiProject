<template>
  <div class="register-container">
    <div class="register-card">
      <div class="register-header">
        <h2 class="title">FitFlow 悦动健身</h2>
        <p class="subtitle">创建账号，开启你的科学健身旅程</p>
      </div>
      <el-form ref="registerFormRef" :model="registerForm" :rules="registerRules" label-position="top" class="register-form">
        <div class="form-grid">
          <el-form-item prop="phone" label="手机号">
            <el-input
              v-model="registerForm.phone"
              placeholder="请输入手机号"
              maxlength="11"
              clearable
            />
          </el-form-item>

          <el-form-item prop="password" label="密码">
            <el-input
              v-model="registerForm.password"
              type="password"
              placeholder="请输入密码"
              show-password
              clearable
            />
          </el-form-item>

          <el-form-item prop="sex" label="性别">
            <el-radio-group v-model="registerForm.sex">
              <el-radio :label="0">男</el-radio>
              <el-radio :label="1">女</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-form-item prop="locationCodes" label="所属地区" class="span-2">
            <el-cascader
              v-model="registerForm.locationCodes"
              :options="regionOptions"
              :props="cascaderProps"
              clearable
              filterable
              placeholder="请选择所属地区"
              @change="handleLocationChange"
            />
          </el-form-item>

          <el-form-item prop="socialGoal" label="自己的目标" class="span-2">
            <el-select v-model="registerForm.socialGoal" placeholder="请选择你自己的目标">
              <el-option v-for="item in socialGoalOptions" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>

          <el-form-item label="头像" class="span-2">
            <div class="avatar-field">
              <el-upload
                class="avatar-uploader"
                :show-file-list="false"
                accept="image/*"
                :http-request="handleAvatarUpload"
              >
                <img v-if="registerForm.avatar" :src="avatarPreviewUrl" class="avatar-preview" alt="avatar" />
                <div v-else class="avatar-placeholder">
                  <div class="avatar-plus">+</div>
                  <div class="avatar-text">上传头像</div>
                </div>
              </el-upload>
              <div class="avatar-tip">支持 jpg、png、webp，头像为可选项</div>
            </div>
          </el-form-item>
        </div>

        <div class="register-actions">
          <el-button class="cancel-button" @click="handleCancel">取消</el-button>
          <el-button type="primary" class="register-button" @click="handleRegister" :loading="loading">
            保存
          </el-button>
        </div>

        <div class="login-link">
          已有账号？ <router-link to="/login">返回登录</router-link>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'

// 门户注册页：除基础账号信息外，还会同步采集地区、头像和社交目标，
// 这些数据会在后端注册时同时写入 user 与 social_profile 的初始化信息。
const router = useRouter()
// 提交按钮 loading，避免重复注册。
const loading = ref(false)
// 地区级联选项，来源于后端地区字典树。
const regionOptions = ref([])
// 表单实例，用于统一触发 validate。
const registerFormRef = ref(null)

// 与后台用户管理页保持一致的地区级联配置。
const cascaderProps = {
  value: 'value',
  label: 'label',
  children: 'children',
  emitPath: true,
  checkStrictly: false,
}

const registerForm = reactive({
  phone: '',
  password: '',
  // 默认男，对应后端用户表里的 sex 字段。
  sex: 0,
  // avatar 存的是上传成功后返回的图片 URL。
  avatar: '',
  // location 只提交最后一级地区 code。
  location: '',
  // locationCodes 是 cascader 组件展示和校验所需的完整路径数组。
  locationCodes: [],
  // socialGoal 会在注册成功时写入社交资料初始化数据。
  socialGoal: '',
  // enable=1 表示新账号默认启用。
  enable: 1,
})

// 社交目标会直接影响注册后默认生成的社交资料。
const socialGoalOptions = [
  '增肌塑形',
  '减脂燃卡',
  '跑步进阶',
  '力量提升',
  '规律打卡',
  '饮食管理',
]

const avatarPreviewUrl = computed(() => {
  if (!registerForm.avatar) return ''
  // 当前项目上传接口直接返回可访问 URL，因此这里只做最小兼容处理。
  return registerForm.avatar.startsWith('http')
    ? registerForm.avatar
    : registerForm.avatar
})

const validateLocation = (_, value, callback) => {
  // cascader 的校验以路径数组是否为空为准。
  if (!value || value.length === 0) {
    callback(new Error('请选择所属地区'))
    return
  }
  callback()
}

const registerRules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^(13[0-9]|14[01456879]|15[0-35-9]|16[2567]|17[0-8]|18[0-9]|19[0-35-9])\d{8}$/, message: '手机号格式不正确', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { pattern: /^[a-zA-Z]\w{5,17}$/, message: '密码以字母开头，长度 6-18 位，只能包含字母、数字和下划线', trigger: 'blur' },
  ],
  sex: [{ required: true, message: '请选择性别', trigger: 'change' }],
  locationCodes: [{ validator: validateLocation, trigger: 'change' }],
  socialGoal: [{ required: true, message: '请选择自己的目标', trigger: 'change' }],
}

const handleLocationChange = (values) => {
  // 只保存最后一级地区 code 给后端。
  registerForm.location = Array.isArray(values) && values.length ? values[values.length - 1] : ''
}

const fetchRegions = async () => {
  // 注册页加载地区字典，供用户选择所在地。
  const res = await request.get('/dict/region/options')
  // 后端已返回树形结构，这里直接赋给 cascader。
  regionOptions.value = Array.isArray(res.data) ? res.data : []
}

const handleAvatarUpload = async ({ file }) => {
  // 头像先通过公共上传接口换取 URL，再随注册表单一起提交。
  const formData = new FormData()
  // Element Plus 自定义上传时，需要手动把 file 塞进 FormData。
  formData.append('file', file)
  const res = await request.post('/common/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  // 后续注册接口只认图片 URL，因此上传完成后立刻写回表单。
  registerForm.avatar = res.data?.url || ''
  ElMessage.success('头像上传成功')
}

const handleRegister = async () => {
  // 表单实例还没准备好时不继续执行。
  if (!registerFormRef.value) return

  // validate 成功返回 true，失败会抛异常，这里统一 catch 成 false。
  const valid = await registerFormRef.value.validate().catch(() => false)
  if (!valid) return

  // 注册成功后不自动登录，而是回到登录页，让用户走正常登录流程获取 token。
  loading.value = true
  try {
    await request.post('/auth/register', {
      // 只提交后端注册接口真正需要的字段。
      phone: registerForm.phone,
      password: registerForm.password,
      sex: registerForm.sex,
      avatar: registerForm.avatar,
      location: registerForm.location,
      socialGoal: registerForm.socialGoal,
      enable: registerForm.enable,
    })
    ElMessage.success('注册成功，请登录')
    // 注册页不保存任何本地登录态，成功后直接回登录页。
    router.push('/login')
  } catch (error) {
    console.error('Register error:', error)
  } finally {
    // 请求结束后恢复按钮状态。
    loading.value = false
  }
}

const handleCancel = () => {
  // 取消注册直接回登录页。
  router.push('/login')
}

// 页面初始化时加载地区数据。
onMounted(fetchRegions)
</script>

<style scoped lang="scss">
.register-container {
  display: flex;
  justify-content: center;
  align-items: center;
  height: 100vh;
  background-color: #111;
  background-image: linear-gradient(rgba(0,0,0,0.6), rgba(0,0,0,0.6)), url('https://images.unsplash.com/photo-1534438327276-14e5300c3a48?ixlib=rb-1.2.1&auto=format&fit=crop&w=1350&q=80');
  background-size: cover;
  background-position: center;
}

.register-card {
  width: min(720px, calc(100vw - 32px));
  padding: 36px;
  background: rgba(255, 255, 255, 0.95);
  border-radius: 20px;
  box-shadow: 0 10px 30px rgba(0,0,0,0.3);
  backdrop-filter: blur(10px);
}

.register-header {
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

.register-form {
  :deep(.el-form-item__label) {
    font-weight: 700;
    color: #333;
    padding-bottom: 8px;
  }

  :deep(.el-input__wrapper) {
    background: #f5f5f5;
    box-shadow: none;
    border-radius: 10px;
    padding: 8px 15px;

    &.is-focus {
      box-shadow: 0 0 0 1px #24cf5f inset;
    }
  }

  :deep(.el-cascader) {
    width: 100%;
  }
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 4px 16px;
}

.span-2 {
  grid-column: span 2;
}

.avatar-field {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.avatar-uploader {
  width: 100%;
}

.avatar-preview,
.avatar-placeholder {
  width: 100px;
  height: 100px;
  border-radius: 18px;
}

.avatar-preview {
  display: block;
  object-fit: cover;
  border: 1px solid #dcdfe6;
}

.avatar-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
  border: 1px dashed #c0c4cc;
  color: #606266;
  cursor: pointer;
}

.avatar-plus {
  font-size: 28px;
  line-height: 1;
}

.avatar-text,
.avatar-tip {
  font-size: 12px;
}

.avatar-tip {
  color: #909399;
}

.register-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 12px;
}

.cancel-button,
.register-button {
  min-width: 120px;
  height: 45px;
  border-radius: 10px;
  font-size: 16px;
  font-weight: 600;
}

.register-button {
  background-color: #24cf5f;
  border-color: #24cf5f;

  &:hover {
    background-color: #1fb954;
    border-color: #1fb954;
  }
}

.cancel-button {
  border-color: #dcdfe6;
}

.login-link {
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

@media (max-width: 640px) {
  .register-card {
    padding: 24px;
  }

  .form-grid {
    grid-template-columns: repeat(1, minmax(0, 1fr));
  }

  .span-2 {
    grid-column: span 1;
  }

  .register-actions {
    justify-content: stretch;
  }

  .cancel-button,
  .register-button {
    flex: 1;
  }
}
</style>
