<template>
  <div id="app">
    <router-view v-if="isLoginPage" />
    <el-container v-else class="layout-container">
      <SessionTimeoutModal
        ref="timeoutModalRef"
        @warning-shown="onWarningShown"
        @continue="onSessionContinue"
        @logout="onSessionLogout"
        @timeout="onSessionTimeout"
      />
      <el-header class="layout-header">
        <div class="header-content">
          <div class="logo">
            <el-icon :size="32" color="#409EFF">
              <TrendCharts />
            </el-icon>
            <span class="title">热点事件检测系统</span>
          </div>
          <div class="header-nav">
            <el-menu
              :default-active="activeMenu"
              mode="horizontal"
              :ellipsis="false"
              router
            >
              <el-menu-item index="/">
                <el-icon><DataAnalysis /></el-icon>
                <span>数据概览</span>
              </el-menu-item>
              <el-menu-item index="/events">
                <el-icon><List /></el-icon>
                <span>热点事件</span>
              </el-menu-item>
              <el-menu-item index="/crawl-records">
                <el-icon><Refresh /></el-icon>
                <span>抓取记录</span>
              </el-menu-item>
              <el-menu-item v-if="userStore.isAdmin" index="/users">
                <el-icon><User /></el-icon>
                <span>用户管理</span>
              </el-menu-item>
              <el-menu-item v-if="userStore.isAdmin" index="/sys-configs">
                <el-icon><Setting /></el-icon>
                <span>系统管理</span>
              </el-menu-item>
            </el-menu>
          </div>
          <div class="header-right">
            <el-button type="primary" @click="handleCrawlAll" :loading="loading">
              <el-icon><RefreshRight /></el-icon>
              <span>立即抓取</span>
            </el-button>
            <el-dropdown>
              <div class="user-info">
                <el-avatar
                  :size="32"
                  :src="userStore.user?.avatar ? getAvatarUrl(userStore.user.avatar) : ''"
                  :icon="!userStore.user?.avatar ? UserFilled : undefined"
                />
                <span class="username">{{ userStore.username }}</span>
                <el-tag v-if="userStore.isAdmin" type="danger" size="small" effect="dark">
                  管理员
                </el-tag>
                <el-icon><ArrowDown /></el-icon>
              </div>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item disabled>
                    <el-icon><User /></el-icon>
                    {{ userStore.user?.username }}
                  </el-dropdown-item>
                  <el-dropdown-item disabled>
                    <el-icon><Message /></el-icon>
                    {{ userStore.user?.email || '未设置邮箱' }}
                  </el-dropdown-item>
                  <el-dropdown-item divided @click="goToProfile">
                    <el-icon><Setting /></el-icon>
                    个人中心
                  </el-dropdown-item>
                  <el-dropdown-item @click="handleLogout">
                    <el-icon><SwitchButton /></el-icon>
                    退出登录
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </div>
      </el-header>

      <el-main class="layout-main">
        <router-view />
      </el-main>

      <el-footer class="layout-footer">
        <p>© 2024 热点事件检测系统 | 数据来源：微博、知乎、百度</p>
      </el-footer>
    </el-container>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UserFilled, ArrowDown, SwitchButton, Setting } from '@element-plus/icons-vue'
import { crawlAllSources } from '@/api/crawler'
import { useUserStore } from '@/stores/user'
import SessionTimeoutModal from '@/components/SessionTimeoutModal.vue'
import sessionTimeout from '@/utils/sessionTimeout'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeMenu = computed(() => route.path)
const isLoginPage = computed(() => route.path === '/login')

const loading = ref(false)
const timeoutModalRef = ref(null)

const onWarningShown = (data) => {
  console.log('Session warning shown:', data)
}

const onSessionContinue = () => {
  console.log('Session continued by user')
}

const onSessionLogout = () => {
  console.log('User logged out due to timeout warning')
}

const onSessionTimeout = () => {
  console.log('Session timed out')
}

const initSessionTimeout = () => {
  if (userStore.isLoggedIn && !sessionTimeout.isInitialized) {
    sessionTimeout.init({
      callbacks: {
        onWarning: (data) => {
          if (timeoutModalRef.value) {
            timeoutModalRef.value.showWarning(data)
          }
        },
        onTimeout: async () => {
          if (timeoutModalRef.value) {
            await timeoutModalRef.value.handleTimeout()
          }
        },
        onActivity: (data) => {
          console.log('User activity detected:', data)
        }
      }
    })
  }
}

const destroySessionTimeout = () => {
  if (sessionTimeout.isInitialized) {
    sessionTimeout.destroy()
  }
}

watch(
  () => userStore.isLoggedIn,
  (isLoggedIn) => {
    if (isLoggedIn) {
      initSessionTimeout()
    } else {
      destroySessionTimeout()
    }
  }
)

onMounted(() => {
  if (userStore.isLoggedIn) {
    initSessionTimeout()
  }
})

onUnmounted(() => {
  destroySessionTimeout()
})

const handleCrawlAll = async () => {
  if (loading.value) return
  loading.value = true
  try {
    await crawlAllSources()
    ElMessage.success('抓取任务已启动')
    setTimeout(() => {
      window.location.reload()
    }, 2000)
  } catch (error) {
    ElMessage.error('抓取失败: ' + (error.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

const handleLogout = async () => {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '退出确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await userStore.logout()
    ElMessage.success('已退出登录')
    router.push('/login')
  } catch (e) {
  }
}

const goToProfile = () => {
  router.push('/profile')
}

const getAvatarUrl = (avatar) => {
  if (!avatar) return ''
  if (avatar.startsWith('http://') || avatar.startsWith('https://')) {
    return avatar
  }
  return '/api' + avatar
}
</script>

<style scoped lang="scss">
.layout-container {
  min-height: 100vh;
}

.layout-header {
  background: #fff;
  border-bottom: 1px solid #e6e6e6;
  padding: 0;
  height: 64px;
  line-height: 64px;

  .header-content {
    max-width: 1400px;
    margin: 0 auto;
    padding: 0 24px;
    display: flex;
    align-items: center;
    justify-content: space-between;

    .logo {
      display: flex;
      align-items: center;
      gap: 12px;

      .title {
        font-size: 20px;
        font-weight: 600;
        color: #303133;
      }
    }

    .header-nav {
      flex: 1;
      margin: 0 32px;

      .el-menu {
        border-bottom: none;
      }
    }

    .header-right {
      display: flex;
      align-items: center;
      gap: 20px;

      .user-info {
        display: flex;
        align-items: center;
        gap: 8px;
        cursor: pointer;
        padding: 0 8px;
        line-height: 40px;

        &:hover {
          background: #f5f7fa;
          border-radius: 6px;
        }

        .username {
          font-size: 14px;
          color: #303133;
          max-width: 100px;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
      }
    }
  }
}

.layout-main {
  background: #f5f7fa;
  padding: 24px;

  :deep(.el-main) {
    padding: 0;
  }
}

.layout-footer {
  background: #fff;
  border-top: 1px solid #e6e6e6;
  text-align: center;
  color: #909399;
  font-size: 14px;
  padding: 20px 0;
}
</style>
