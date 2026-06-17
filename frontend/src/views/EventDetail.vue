<template>
  <div class="event-detail-page">
    <div class="page-header">
      <el-button @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
        <span>{{ $t('events.back') }}</span>
      </el-button>
    </div>

    <div class="card" v-loading="loading">
      <el-tabs v-model="activeTab" class="event-detail-tabs">
        <el-tab-pane :label="$t('events.detail')" name="detail">
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
        </el-tab-pane>

        <el-tab-pane :label="$t('eventLogs.changeHistory')" name="logs">
          <div class="change-history">
            <div class="history-header">
              <span class="history-count">
                {{ $t('eventLogs.page') }} ({{ changeLogs.length }})
              </span>
              <el-button @click="fetchChangeLogs">
                <el-icon><Refresh /></el-icon>
                {{ $t('common.refresh') }}
              </el-button>
            </div>
            <el-timeline v-if="changeLogs.length > 0">
              <el-timeline-item
                v-for="log in changeLogs"
                :key="log.id"
                :timestamp="formatTime(log.operationTime)"
                :type="getLogType(log.operationType)"
                size="large"
              >
                <div class="log-item">
                  <div class="log-item-header">
                    <el-tag :type="getLogType(log.operationType)" size="small">
                      {{ getOperationTypeName(log.operationType) }}
                    </el-tag>
                    <span class="log-field" v-if="log.fieldName">
                      {{ log.fieldName }}
                    </span>
                    <span class="log-operator">
                      {{ log.operatorName || 'system' }}
                    </span>
                  </div>
                  <div class="log-item-content" v-if="log.oldValue || log.newValue">
                    <div class="log-old" v-if="log.oldValue">
                      <span class="log-label">{{ $t('eventLogs.oldValue') }}:</span>
                      <span class="log-text">{{ truncateText(log.oldValue, 100) }}</span>
                    </div>
                    <div class="log-new" v-if="log.newValue">
                      <span class="log-label">{{ $t('eventLogs.newValue') }}:</span>
                      <span class="log-text">{{ truncateText(log.newValue, 100) }}</span>
                    </div>
                  </div>
                  <div class="log-item-reason" v-if="log.reason">
                    <el-icon><InfoFilled /></el-icon>
                    {{ log.reason }}
                  </div>
                </div>
              </el-timeline-item>
            </el-timeline>
            <el-empty v-else :description="$t('common.noData')" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { getHotEventById } from '@/api/event'
import { getEventLogsByEventId } from '@/api/eventLog'
import { getPlatformName } from '@/utils/platform'
import dayjs from 'dayjs'

const { t, locale } = useI18n()
const route = useRoute()
const router = useRouter()

const loading = ref(false)
const event = ref(null)
const activeTab = ref('detail')
const changeLogs = ref([])

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

const fetchChangeLogs = async () => {
  try {
    const id = route.params.id
    const data = await getEventLogsByEventId(id)
    changeLogs.value = data || []
  } catch (error) {
    console.error('Failed to fetch change logs', error)
  }
}

const getLogType = (type) => {
  const typeMap = {
    'INSERT': 'success',
    'UPDATE': 'warning',
    'DELETE': 'danger'
  }
  return typeMap[type] || 'info'
}

const getOperationTypeName = (type) => {
  const typeMap = {
    'INSERT': t('eventLogs.typeInsert'),
    'UPDATE': t('eventLogs.typeUpdate'),
    'DELETE': t('eventLogs.typeDelete')
  }
  return typeMap[type] || type
}

const truncateText = (text, maxLength) => {
  if (!text) return '-'
  if (text.length <= maxLength) return text
  return text.substring(0, maxLength) + '...'
}

onMounted(() => {
  fetchEventDetail()
})

watch(activeTab, (newTab) => {
  if (newTab === 'logs') {
    fetchChangeLogs()
  }
})

watch(locale, () => {
  fetchEventDetail()
})
</script>

<style scoped lang="scss">
.event-detail-page {
  .event-detail-tabs {
    :deep(.el-tabs__header) {
      margin-bottom: 20px;
    }
  }

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

  .change-history {
    .history-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 20px;

      .history-count {
        font-size: 14px;
        color: #606266;
        font-weight: 500;
      }
    }

    .log-item {
      .log-item-header {
        display: flex;
        align-items: center;
        gap: 10px;
        margin-bottom: 8px;

        .log-field {
          font-size: 14px;
          color: #606266;
          font-weight: 500;
        }

        .log-operator {
          font-size: 13px;
          color: #909399;
          margin-left: auto;
        }
      }

      .log-item-content {
        margin-bottom: 8px;

        .log-old,
        .log-new {
          font-size: 13px;
          line-height: 1.6;

          .log-label {
            color: #909399;
            margin-right: 6px;
          }

          .log-text {
            color: #606266;
            word-break: break-all;
          }
        }

        .log-old .log-text {
          color: #f56c6c;
          text-decoration: line-through;
        }

        .log-new .log-text {
          color: #67c23a;
        }
      }

      .log-item-reason {
        font-size: 12px;
        color: #909399;
        display: flex;
        align-items: center;
        gap: 4px;
        margin-top: 6px;
      }
    }
  }
}
</style>
