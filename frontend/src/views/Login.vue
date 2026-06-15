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
        <template #title>{{ t('login.sessionTimeout') }}</template>
        {{ t('login.sessionTimeoutMessage') }}
      </el-alert>

      <div class="login-header">
        <el-icon :size="48" color="#409EFF">
          <TrendCharts />
        </el-icon>
        <h1 class="title">{{ t('app.title') }}</h1>
        <p class="subtitle">{{ t('login.welcomeBack') }}</p>
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
            :placeholder="t('login.usernamePlaceholder')"
            size="large"
            :prefix-icon="User"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="loginForm.password"
            type="password"
            :placeholder="t('login.passwordPlaceholder')"
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
            {{ t('login.loginButton') }}
          </el-button>
        </el-form-item>
      </el-form>
      <div class="login-tips">
        <p>{{ t('login.adminTip') }}</p>
        <p>{{ t('login.userTip') }}</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/user'
import message from '@/utils/message'

const { t } = useI18n()
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
  username: [{ required: true, message: () => t('login.usernamePlaceholder'), trigger: 'blur' }],
  password: [{ required: true, message: () => t('login.passwordPlaceholder'), trigger: 'blur' }]
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
        message.success(t('login.success'))
        clearTimeoutParam()
        router.push('/')
      } catch (error) {
        console.error(t('login.failed'), error)
        if (error.message && error.message !== 'Error') {
          message.error(error.message)
        }
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
