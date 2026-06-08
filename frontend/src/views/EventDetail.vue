<template>
  <div class="event-detail-page">
    <div class="page-header">
      <el-button @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
        <span>返回</span>
      </el-button>
    </div>

    <div class="card" v-loading="loading">
      <div class="event-header" v-if="event">
        <div class="event-meta">
          <span :class="['source-tag', event.source]">{{ getSourceName(event.source) }}</span>
          <span class="rank">排名 #{{ event.hotRank }}</span>
          <span class="hot-value">{{ formatHotValue(event.hotValue) }} 热度</span>
          <el-tag v-if="event.isRising" type="danger" effect="light">
            <el-icon><TrendCharts /></el-icon>
            飙升
          </el-tag>
        </div>
        <h1 class="event-title">{{ event.title }}</h1>
        <div class="event-info">
          <span><el-icon><Clock /></el-icon> {{ formatTime(event.crawlTime) }}</span>
          <span v-if="event.category"><el-icon><PriceTag /></el-icon> {{ event.category }}</span>
          <span><el-icon><View /></el-icon> 首次出现: {{ formatTime(event.firstSeenTime) }}</span>
        </div>
      </div>

      <div class="event-content" v-if="event">
        <h3>事件描述</h3>
        <p>{{ event.description || '暂无描述' }}</p>

        <div class="event-actions">
          <el-button type="primary" @click="openSourceUrl">
            <el-icon><Link /></el-icon>
            <span>查看原文</span>
          </el-button>
        </div>
      </div>

      <el-empty v-if="!event && !loading" description="事件不存在" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getHotEventById } from '@/api/event'
import dayjs from 'dayjs'

const route = useRoute()
const router = useRouter()

const loading = ref(false)
const event = ref(null)

const fetchEventDetail = async () => {
  loading.value = true
  try {
    const id = route.params.id
    event.value = await getHotEventById(id)
  } catch (error) {
    console.error('获取事件详情失败', error)
  } finally {
    loading.value = false
  }
}

const getSourceName = (source) => {
  const nameMap = {
    weibo: '微博',
    zhihu: '知乎',
    baidu: '百度'
  }
  return nameMap[source] || source
}

const formatHotValue = (value) => {
  if (!value) return '0'
  if (value >= 10000) {
    return (value / 10000).toFixed(1) + '万'
  }
  return value.toString()
}

const formatTime = (time) => {
  if (!time) return '-'
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
}

const goBack = () => {
  router.back()
}

const openSourceUrl = () => {
  if (event.value && event.value.sourceUrl) {
    window.open(event.value.sourceUrl, '_blank')
  }
}

onMounted(() => {
  fetchEventDetail()
})
</script>

<style scoped lang="scss">
.event-detail-page {
  .event-header {
    margin-bottom: 24px;
    padding-bottom: 24px;
    border-bottom: 1px solid #f0f0f0;

    .event-meta {
      display: flex;
      align-items: center;
      gap: 16px;
      margin-bottom: 16px;

      .rank {
        font-weight: 600;
        color: #ff4d4f;
      }

      .hot-value {
        font-weight: 600;
        color: #f56c6c;
      }
    }

    .event-title {
      font-size: 24px;
      font-weight: 600;
      color: #303133;
      margin-bottom: 16px;
      line-height: 1.5;
    }

    .event-info {
      display: flex;
      gap: 24px;
      color: #909399;
      font-size: 14px;

      span {
        display: flex;
        align-items: center;
        gap: 4px;
      }
    }
  }

  .event-content {
    h3 {
      font-size: 16px;
      font-weight: 600;
      margin-bottom: 12px;
      color: #303133;
    }

    p {
      color: #606266;
      line-height: 1.8;
      font-size: 15px;
    }

    .event-actions {
      margin-top: 24px;
      padding-top: 24px;
      border-top: 1px solid #f0f0f0;
    }
  }
}
</style>
