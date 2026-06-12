<template>
  <el-dialog
    v-model="visible"
    title="会话即将超时"
    width="420px"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :show-close="false"
    class="session-timeout-modal"
    append-to-body
  >
    <div class="modal-content">
      <div class="warning-icon">
        <el-icon :size="48" color="#e6a23c">
          <Warning />
        </el-icon>
      </div>

      <div class="warning-message">
        <p class="main-text">
          由于您长时间未进行任何操作，会话将在
          <span class="countdown">{{ formattedCountdown }}</span>
          后自动超时。
        </p>
        <p class="sub-text">
          为了保护您的账户安全，超时后将自动退出登录。
        </p>
      </div>

      <div class="progress-bar-container">
        <div class="progress-bar">
          <div
            class="progress-fill"
            :style="{ width: progressPercentage + '%' }"
            :class="{ 'progress-warning': progressPercentage > 50, 'progress-danger': progressPercentage <= 50 }"
          ></div>
        </div>
        <div class="progress-labels">
          <span>{{ formatTime(remainingTime) }}</span>
          <span>{{ formatTime(warningBefore) }}</span>
        </div>
      </div>

      <div class="tips">
        <el-icon :size="14" color="#909399">
          <InfoFilled />
        </el-icon>
        <span>点击"继续会话"可延长您的登录时间</span>
      </div>
    </div>

    <template #footer>
      <div class="modal-footer">
        <el-button
          type="danger"
          plain
          size="large"
          @click="handleLogout"
          class="logout-btn"
        >
          <el-icon><SwitchButton /></el-icon>
          安全退出
        </el-button>
        <el-button
          type="primary"
          size="large"
          @click="handleContinue"
          class="continue-btn"
        >
          <el-icon><RefreshRight /></el-icon>
          继续会话
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { ElNotification } from 'element-plus'
import { Warning, InfoFilled, SwitchButton, RefreshRight } from '@element-plus/icons-vue'
import sessionTimeout from '@/utils/sessionTimeout'

const props = defineProps({
  autoStart: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['warning-shown', 'continue', 'logout', 'timeout'])

const visible = ref(false)
const remainingTime = ref(0)
const warningBefore = ref(5 * 60 * 1000)
const countdownInterval = ref(null)
const notificationId = ref(null)

const formattedCountdown = computed(() => {
  return formatTime(remainingTime.value)
})

const progressPercentage = computed(() => {
  if (warningBefore.value <= 0) return 0
  return Math.max(0, Math.min(100, (remainingTime.value / warningBefore.value) * 100))
})

function formatTime(ms) {
  if (ms <= 0) return '00:00'

  const totalSeconds = Math.ceil(ms / 1000)
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60

  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
}

function startCountdown() {
  stopCountdown()

  countdownInterval.value = setInterval(() => {
    remainingTime.value = sessionTimeout.getRemainingTime()

    if (remainingTime.value <= 0) {
      handleTimeout()
    }
  }, 1000)
}

function stopCountdown() {
  if (countdownInterval.value) {
    clearInterval(countdownInterval.value)
    countdownInterval.value = null
  }
}

function showWarning(data) {
  visible.value = true
  remainingTime.value = data.remainingTime
  warningBefore.value = data.warningBefore

  startCountdown()
  showNotification(data.remainingTime)
  emit('warning-shown', data)
}

function showNotification(remaining) {
  if (notificationId.value) {
    ElNotification.close(notificationId.value)
  }

  ElNotification.warning({
    title: '会话即将超时',
    message: `您的会话将在 ${formatTime(remaining)} 后超时，请及时保存您的工作。`,
    duration: 5000,
    position: 'top-right',
    onClick: () => {
      handleContinue()
    },
    onClose: () => {
      notificationId.value = null
    }
  })
}

function hideWarning() {
  visible.value = false
  stopCountdown()

  if (notificationId.value) {
    ElNotification.close(notificationId.value)
    notificationId.value = null
  }
}

async function handleContinue() {
  sessionTimeout.continueSession()
  hideWarning()

  ElNotification.success({
    title: '会话已延长',
    message: '您的会话已成功延长，请继续操作。',
    duration: 3000
  })

  emit('continue')
}

async function handleLogout() {
  hideWarning()
  await sessionTimeout.logoutSession()
  emit('logout')
}

async function handleTimeout() {
  stopCountdown()
  hideWarning()

  ElNotification.error({
    title: '会话已超时',
    message: '由于长时间未操作，您的会话已超时，请重新登录。',
    duration: 5000
  })

  emit('timeout')
}

function setupTimeoutCallbacks() {
  if (!sessionTimeout.isInitialized && props.autoStart) {
    sessionTimeout.init({
      callbacks: {
        onWarning: showWarning,
        onTimeout: handleTimeout
      }
    })
  } else if (sessionTimeout.isInitialized) {
    const originalOnWarning = sessionTimeout.callbacks.onWarning
    const originalOnTimeout = sessionTimeout.callbacks.onTimeout

    sessionTimeout.callbacks.onWarning = (data) => {
      if (originalOnWarning) originalOnWarning(data)
      showWarning(data)
    }

    sessionTimeout.callbacks.onTimeout = async () => {
      if (originalOnTimeout) await originalOnTimeout()
      handleTimeout()
    }
  }
}

onMounted(() => {
  setupTimeoutCallbacks()
})

onUnmounted(() => {
  stopCountdown()

  if (notificationId.value) {
    ElNotification.close(notificationId.value)
  }
})

watch(() => props.autoStart, (newVal) => {
  if (newVal && !sessionTimeout.isInitialized) {
    setupTimeoutCallbacks()
  }
})

defineExpose({
  showWarning,
  hideWarning,
  handleContinue,
  handleLogout,
  getRemainingTime: () => remainingTime.value
})
</script>

<style scoped lang="scss">
.session-timeout-modal {
  :deep(.el-dialog) {
    border-radius: 12px;
    overflow: hidden;
  }

  :deep(.el-dialog__header) {
    background: linear-gradient(135deg, #fff7e6 0%, #fffaeb 100%);
    padding: 20px 24px;
    margin-right: 0;

    .el-dialog__title {
      color: #e6a23c;
      font-size: 18px;
      font-weight: 600;
    }
  }

  :deep(.el-dialog__body) {
    padding: 24px;
  }

  :deep(.el-dialog__footer) {
    padding: 16px 24px;
    border-top: 1px solid #f0f0f0;
  }
}

.modal-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20px;
}

.warning-icon {
  animation: pulse 2s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% {
    transform: scale(1);
    opacity: 1;
  }
  50% {
    transform: scale(1.1);
    opacity: 0.8;
  }
}

.warning-message {
  text-align: center;

  .main-text {
    font-size: 15px;
    color: #303133;
    line-height: 1.8;
    margin: 0 0 8px 0;

    .countdown {
      color: #f56c6c;
      font-weight: 700;
      font-size: 18px;
      padding: 0 4px;
    }
  }

  .sub-text {
    font-size: 13px;
    color: #909399;
    margin: 0;
  }
}

.progress-bar-container {
  width: 100%;
  padding: 0 10px;

  .progress-bar {
    width: 100%;
    height: 8px;
    background: #f0f2f5;
    border-radius: 4px;
    overflow: hidden;
    margin-bottom: 8px;

    .progress-fill {
      height: 100%;
      transition: width 1s linear, background-color 0.3s ease;
      border-radius: 4px;

      &.progress-warning {
        background: linear-gradient(90deg, #e6a23c, #f5a623);
      }

      &.progress-danger {
        background: linear-gradient(90deg, #f56c6c, #e74c3c);
        animation: danger-pulse 1s ease-in-out infinite;
      }
    }
  }

  @keyframes danger-pulse {
    0%, 100% {
      opacity: 1;
    }
    50% {
      opacity: 0.7;
    }
  }

  .progress-labels {
    display: flex;
    justify-content: space-between;
    font-size: 12px;
    color: #909399;
  }
}

.tips {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #909399;
  background: #f5f7fa;
  padding: 10px 16px;
  border-radius: 6px;
  width: 100%;
  box-sizing: border-box;
}

.modal-footer {
  display: flex;
  gap: 12px;
  justify-content: center;

  .logout-btn,
  .continue-btn {
    min-width: 120px;
    display: flex;
    align-items: center;
    gap: 6px;
  }

  .continue-btn {
    background: linear-gradient(135deg, #409eff 0%, #66b1ff 100%);
    border: none;

    &:hover {
      background: linear-gradient(135deg, #66b1ff 0%, #409eff 100%);
    }
  }
}
</style>
