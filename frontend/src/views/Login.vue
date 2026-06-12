<template>
  <div class="login-container">
    <div class="login-box">
      <el-alert
        v-if="showTimeoutAlert"
        type="warning"
        :closable="false"
        show-icon
        class="timeout-alert"
      >
        <template #title>会话已超时</template>
        由于长时间未操作，您的会话已安全终止。为了您的账户安全，请重新登录。
      </el-alert>

      <div class="login-header">
        <el-icon :size="48" color="#409EFF">
          <TrendCharts />
        </el-icon>
        <h1 class="title">热点事件检测系统</h1>
        <p class="subtitle">欢迎回来，请登录您的账号</p>
      </div>
      <el-form
        ref="loginFormRef"
        :model="loginForm"
        :rules="loginRules"
        class="login-form"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="username">
          <el-input
            v-model="loginForm.username"
            placeholder="请输入用户名"
            size="large"
            :prefix-icon="User"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            placeholder="请输入密码"
            size="large"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            size="large"
            class="login-btn"
            :loading="loading"
            @click="handleLogin"
          >
            登 录
          </el-button>
        </el-form-item>
      </el-form>
      <div class="login-tips">
        <p>管理员账号：admin / admin123</p>
        <p>普通用户账号：user1~user3 / user123</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const loginFormRef = ref(null)
const loading = ref(false)
const showTimeoutAlert = ref(false)

const loginForm = reactive({
  username: '',
  password: ''
})

const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const checkTimeoutRedirect = () => {
  const timeoutParam = route.query.timeout
  if (timeoutParam === '1' || timeoutParam === 1) {
    showTimeoutAlert.value = true
    setTimeout(() => {
      showTimeoutAlert.value = false
    }, 8000)
  }
}

const clearTimeoutParam = () => {
  if (route.query.timeout) {
    const { timeout, ...restQuery } = route.query
    router.replace({
      path: '/login',
      query: restQuery
    })
  }
}

const handleLogin = async () => {
  if (!loginFormRef.value) return
  await loginFormRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        await userStore.login(loginForm)
        ElMessage.success('登录成功')
        clearTimeoutParam()
        router.push('/')
      } catch (error) {
      } finally {
        loading.value = false
      }
    }
  })
}

onMounted(() => {
  checkTimeoutRedirect()
})
</script>

<style scoped lang="scss">
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
}

.timeout-alert {
  margin-bottom: 24px;
  border-radius: 8px;

  :deep(.el-alert__title) {
    font-weight: 600;
    color: #b88230;
  }

  :deep(.el-alert__content) {
    color: #a0712c;
  }
}

.login-box {
  width: 100%;
  max-width: 420px;
  background: #fff;
  border-radius: 12px;
  padding: 48px 40px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15);
}

.login-header {
  text-align: center;
  margin-bottom: 36px;

  .title {
    margin: 12px 0 8px;
    font-size: 24px;
    font-weight: 600;
    color: #303133;
  }

  .subtitle {
    margin: 0;
    font-size: 14px;
    color: #909399;
  }
}

.login-form {
  .login-btn {
    width: 100%;
  }
}

.login-tips {
  margin-top: 24px;
  padding-top: 20px;
  border-top: 1px solid #f0f0f0;
  text-align: center;
  font-size: 12px;
  color: #909399;
  line-height: 1.8;
}
</style>
