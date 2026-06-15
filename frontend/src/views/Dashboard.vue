<template>
  <div class="dashboard-page">
    <div class="page-header">
      <h2 class="page-title">数据概览</h2>
      <el-button type="primary" @click="fetchStatistics">
        <el-icon><Refresh /></el-icon>
        <span>刷新数据</span>
      </el-button>
    </div>

    <el-row :gutter="20" class="mb-20">
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-label">热点事件总数</div>
          <div class="stat-value">{{ statistics.totalCount || 0 }}</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-label">数据源数量</div>
          <div class="stat-value">{{ filteredSourceCount }}</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-label">今日抓取次数</div>
          <div class="stat-value">{{ todayCrawlCount }}</div>
        </div>
      </el-col>
      <el-col :span="6">
        <div class="stat-card">
          <div class="stat-label">抓取间隔</div>
          <div class="stat-value">{{ crawlerConfigStore.interval }}<span class="stat-unit">分钟</span></div>
        </div>
      </el-col>
    </el-row>

    <el-row :gutter="20">
      <el-col :span="12">
        <div class="card mb-20">
          <div class="card-header">
            <h3>各数据源分布</h3>
          </div>
          <div ref="sourceChartRef" class="chart-container"></div>
        </div>

        <div class="card">
          <div class="card-header">
            <h3>分类分布</h3>
          </div>
          <div ref="categoryChartRef" class="chart-container"></div>
        </div>
      </el-col>

      <el-col :span="12">
        <div class="card">
          <div class="card-header">
            <h3>热门事件TOP10</h3>
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
                  <span class="hot-value">{{ formatHotValue(event.hotValue) }} 热度</span>
                </div>
              </div>
            </div>
            <el-empty v-if="!filteredTopEvents || filteredTopEvents.length === 0" description="暂无数据" />
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, computed } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { getEventStatistics } from '@/api/event'
import { useCrawlerConfigStore } from '@/stores/crawlerConfig'
import { usePlatformConfigStore } from '@/stores/platformConfig'
import { getPlatformName, PLATFORM_COLORS } from '@/utils/platform'

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
    console.error('获取统计数据失败', error)
  }
}

const getSourceName = (source) => {
  return getPlatformName(source)
}

const formatHotValue = (value) => {
  if (!value) return '0'
  if (value >= 10000) {
    return (value / 10000).toFixed(1) + '万'
  }
  return value.toString()
}

const goToDetail = (id) => {
  router.push(`/events/${id}`)
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
