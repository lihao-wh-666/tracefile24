<template>
  <div class="dashboard-page">
    <div class="page-header">
      <h2 class="page-title">{{ $t('dashboard.page') }}</h2>
      <div class="header-actions">
        <el-button type="primary" @click="fetchStatistics">
          <el-icon><Refresh /></el-icon>
          <span>{{ $t('common.search') }}</span>
        </el-button>
        <el-dropdown trigger="click" @command="handleExport">
          <el-button type="success">
            <el-icon><Download /></el-icon>
            <span>{{ $t('common.export') }}</span>
            <el-icon><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="excel">
                <el-icon><Document /></el-icon>
                {{ $t('common.exportExcel') }}
              </el-dropdown-item>
              <el-dropdown-item command="csv">
                <el-icon><Tickets /></el-icon>
                {{ $t('common.exportCsv') }}
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </div>

    <el-row :gutter="20" class="mb-20">
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-label">{{ $t('dashboard.totalEvents') }}</div>
          <div class="stat-value">{{ statistics.totalCount || 0 }}</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-label">{{ $t('dashboard.activeSources') }}</div>
          <div class="stat-value">{{ filteredSourceCount }}</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-label">{{ $t('dashboard.todayEvents') }}</div>
          <div class="stat-value">{{ todayCrawlCount }}</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-label">{{ $t('dashboard.hotTrends') }}</div>
          <div class="stat-value">{{ crawlerConfigStore.interval }}<span class="stat-unit">{{ $t('dashboard.last24Hours') }}</span></div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="20">
      <el-col :span="12">
        <div class="card mb-20">
          <div class="card-header">
            <h3>{{ $t('dashboard.sourceDistribution') }}</h3>
          </div>
          <div ref="sourceChartRef" class="chart-container"></div>
        </div>

        <div class="card">
          <div class="card-header">
            <h3>{{ $t('dashboard.categoryDistribution') }}</h3>
          </div>
          <div ref="categoryChartRef" class="chart-container"></div>
        </div>
      </el-col>

      <el-col :span="12">
        <div class="card">
          <div class="card-header">
            <h3>{{ $t('dashboard.topEvents') }}</h3>
          </div>
          <div class="hot-list">
            <div
              v-for="(event, index) in filteredTopEvents"
              :key="event.id"
              class="hot-item"
              @click="goToDetail(event.id)"
            >
              <div class="rank" :class="'rank-' + (index + 1)">{{ index + 1 }}</div>
              <div class="content">
                <div class="title">{{ event.title }}</div>
                <div class="meta">
                  <span :class="['source-tag', event.source]">{{ getSourceName(event.source) }}</span>
                  <span class="hot-value">{{ formatHotValue(event.hotValue) }} {{ $t('common.hotValue') }}</span>
                </div>
              </div>
            </div>
            <el-empty v-if="!filteredTopEvents || filteredTopEvents.length === 0" :description="$t('common.noData')" />
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import * as echarts from 'echarts'
import { getEventStatistics, exportStatisticsExcel, exportStatisticsCsv } from '@/api/event'
import { useCrawlerConfigStore } from '@/stores/crawlerConfig'
import { usePlatformConfigStore } from '@/stores/platformConfig'
import { getPlatformName, PLATFORM_COLORS } from '@/utils/platform'
import message from '@/utils/message'

const { t } = useI18n()
const router = useRouter()
const crawlerConfigStore = useCrawlerConfigStore()
const platformConfigStore = usePlatformConfigStore()

const statistics = ref({})
const topEvents = ref([])
const sourceChartRef = ref(null)
const categoryChartRef = ref(null)
let sourceChart = null
let categoryChart = null

const sourceCount = ref(0)
const topEventsCount = ref(0)
const todayCrawlCount = ref(0)

const enabledPlatformCodes = computed(() => platformConfigStore.enabledPlatformCodes)

const filteredSourceStats = computed(() => {
  const sourceStats = statistics.value.sourceStats || {}
  if (enabledPlatformCodes.value.length > 0) {
    const filtered = {}
    enabledPlatformCodes.value.forEach(code => {
      if (sourceStats[code] !== undefined) {
        filtered[code] = sourceStats[code]
      }
    })
    return filtered
  }
  return sourceStats
})

const filteredTopEvents = computed(() => {
  const events = topEvents.value || []
  if (enabledPlatformCodes.value.length > 0) {
    return events.filter(event => enabledPlatformCodes.value.includes(event.source))
  }
  return events
})

const filteredSourceCount = computed(() => Object.keys(filteredSourceStats.value).length)

const fetchStatistics = async () => {
  try {
    const data = await getEventStatistics()
    statistics.value = data
    topEvents.value = data.topEvents || []
    sourceCount.value = data.sourceStats ? Object.keys(data.sourceStats).length : 0
    topEventsCount.value = data.topEvents ? data.topEvents.length : 0

    await nextTick()
    renderSourceChart()
    renderCategoryChart()
  } catch (error) {
    console.error(t('events.fetchFailed'), error)
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

const goToDetail = (id) => {
  router.push(`/events/${id}`)
}

const handleExport = async (command) => {
  try {
    if (command === 'excel') {
      await exportStatisticsExcel()
      message.success(t('common.exportSuccess'))
    } else if (command === 'csv') {
      await exportStatisticsCsv()
      message.success(t('common.exportSuccess'))
    }
  } catch (error) {
    console.error('导出失败', error)
    message.error(t('common.exportFailed'))
  }
}

const renderSourceChart = () => {
  if (!sourceChartRef.value) return

  if (sourceChart) {
    sourceChart.dispose()
  }

  sourceChart = echarts.init(sourceChartRef.value)

  const data = Object.entries(filteredSourceStats.value).map(([key, value]) => ({
    name: getSourceName(key),
    value: value
  }))

  const option = {
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)'
    },
    legend: {
      orient: 'vertical',
      right: 10,
      top: 'center'
    },
    series: [
      {
        type: 'pie',
        radius: ['40%', '70%'],
        center: ['35%', '50%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 10,
          borderColor: '#fff',
          borderWidth: 2
        },
        label: {
          show: false
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 16,
            fontWeight: 'bold'
          }
        },
        data: data,
        color: PLATFORM_COLORS
      }
    ]
  }

  sourceChart.setOption(option)
}

const renderCategoryChart = () => {
  if (!categoryChartRef.value) return

  if (categoryChart) {
    categoryChart.dispose()
  }

  categoryChart = echarts.init(categoryChartRef.value)

  const categoryStats = statistics.value.categoryStats || {}
  const categories = Object.keys(categoryStats).slice(0, 10)
  const values = categories.map(cat => categoryStats[cat])

  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'shadow'
      }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'value'
    },
    yAxis: {
      type: 'category',
      data: categories
    },
    series: [
      {
        type: 'bar',
        data: values,
        itemStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: '#1890ff' },
            { offset: 1, color: '#69c0ff' }
          ])
        }
      }
    ]
  }

  categoryChart.setOption(option)
}

const handleResize = () => {
  sourceChart && sourceChart.resize()
  categoryChart && categoryChart.resize()
}

onMounted(async () => {
  platformConfigStore.loadFromCache()
  await platformConfigStore.fetchPlatformConfigs()
  fetchStatistics()
  crawlerConfigStore.fetchCrawlIntervalConfig()
  window.addEventListener('resize', handleResize)
})
</script>

<style scoped lang="scss">
.dashboard-page {
  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .header-actions {
      display: flex;
      gap: 12px;
    }
  }

  .stat-unit {
    font-size: 14px;
    font-weight: 400;
    color: #909399;
    margin-left: 4px;
  }

  .card-header {
    margin-bottom: 16px;
    padding-bottom: 12px;
    border-bottom: 1px solid #f0f0f0;

    h3 {
      margin: 0;
      font-size: 16px;
      font-weight: 600;
      color: #303133;
    }
  }

  .chart-container {
    height: 280px;
    width: 100%;
  }

  .hot-list {
    max-height: 600px;
    overflow-y: auto;

    .hot-item {
      display: flex;
      padding: 12px 0;
      border-bottom: 1px solid #f0f0f0;
      cursor: pointer;
      transition: background 0.2s;

      &:hover {
        background: #f5f7fa;
      }

      .rank {
        width: 32px;
        height: 32px;
        line-height: 32px;
        text-align: center;
        border-radius: 6px;
        font-weight: 600;
        font-size: 14px;
        background: #f0f2f5;
        color: #909399;
        margin-right: 16px;
        flex-shrink: 0;

        &.rank-1 {
          background: #ff4d4f;
          color: #fff;
        }

        &.rank-2 {
          background: #ff7a45;
          color: #fff;
        }

        &.rank-3 {
          background: #ffa940;
          color: #fff;
        }
      }

      .content {
        flex: 1;
        min-width: 0;

        .title {
          font-size: 14px;
          color: #303133;
          margin-bottom: 8px;
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }

        .meta {
          display: flex;
          align-items: center;
          gap: 12px;
          font-size: 12px;

          .hot-value {
            color: #f56c6c;
            font-weight: 500;
          }
        }
      }
    }
  }
}
</style>
