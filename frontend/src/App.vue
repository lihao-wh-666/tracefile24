<template>
  <div id="app">
    <el-container class="layout-container">
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
            </el-menu>
          </div>
          <div class="header-right">
            <el-button type="primary" @click="handleCrawlAll">
              <el-icon><RefreshRight /></el-icon>
              <span>立即抓取</span>
            </el-button>
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
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { crawlAllSources } from '@/api/crawler'

const route = useRoute()
const router = useRouter()

const activeMenu = computed(() => route.path)

const loading = ref(false)

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
      gap: 16px;
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
