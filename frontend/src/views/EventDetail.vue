<template>
  <div class="event-detail-page">
    <div class="page-header">
      <el-button @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
        <span>{{ $t('events.back') }}</span>
      </el-button>
    </div>

    <div class="card" v-loading="loading">
      <div class="event-header" v-if="event">
        <div class="event-meta">
          <span :class="['source-tag', event.source]">{{ getSourceName(event.source) }}</span>
          <span class="rank">{{ $t('common.rank') }} #{{ event.hotRank }}</span>
          <span class="hot-value">{{ formatHotValue(event.hotValue) }} {{ $t('common.hotValue') }}</span>
          <el-tag v-if="event.isRising" type="danger" effect="light">
            <el-icon><TrendCharts /></el-icon>
            {{ $t('common.rising') }}
          </el-tag>
        </div>
        <h1 class="event-title">{{ event.title }}</h1>
        <div class="event-info">
          <span><el-icon><Clock /></el-icon> {{ formatTime(event.crawlTime) }}</span>
          <span v-if="event.category"><el-icon><PriceTag /></el-icon> {{ event.category }}</span>
          <span><el-icon><View /></el-icon> {{ $t('events.firstSeen') }}: {{ formatTime(event.firstSeenTime) }}</span>
        </div>
      </div>

      <div class="event-content" v-if="event">
        <h3>{{ $t('events.description') }}</h3>
        <p>{{ event.description || $t('events.noDescription') }}</p>

        <div class="event-actions">
          <el-button type="primary" @click="openSourceUrl">
            <el-icon><Link /></el-icon>
            <span>{{ $t('events.viewSource') }}</span>
          </el-button>
        </div>
      </div>

      <el-empty v-if="!event && !loading" :description="$t('events.notFound')" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { getHotEventById } from '@/api/event'
import { getPlatformName } from '@/utils/platform'
import dayjs from 'dayjs'

const { t, locale } = useI18n()
const route = useRoute()
const router = useRouter()

const loading = ref(false)
const event = ref(null)

const fetchEventDetail = async () => {
  loading.value = true
  try {
    const id = route.params.id
    event.value = await getHotEventById(id, locale.value)
  } catch (error) {
    console.error(t('events.fetchDetailFailed'), error)
  } finally {
    loading.value = false
  }
}

const getSourceName = (source) => {
  return getPlatformName(source)
}

const formatHotValue = (value) => {
  if (!value) return '0'
  if (value >= 10000) {
    return (value / 10000).toFixed(1) + t('events.tenThousand')
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

watch(locale, () => {
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
