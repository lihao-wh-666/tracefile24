<template>
  <div class="crawl-records-page">
    <div class="page-header">
      <h2 class="page-title">抓取记录</h2>
    </div>

    <el-row :gutter="20" class="mb-20">
      <el-col :span="8">
        <div class="stat-card">
          <div class="stat-label">总抓取次数</div>
          <div class="stat-value">{{ stats.totalCrawls || 0 }}</div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="stat-card">
          <div class="stat-label">成功次数</div>
          <div class="stat-value" style="color: #67c23a;">{{ stats.totalSuccess || 0 }}</div>
        </div>
      </el-col>
      <el-col :span="8">
        <div class="stat-card">
          <div class="stat-label">失败次数</div>
          <div class="stat-value" style="color: #f56c6c;">{{ stats.totalFail || 0 }}</div>
        </div>
      </el-col>
    </el-row>

    <div class="card mb-20">
      <div class="filter-bar">
        <el-tabs v-model="activeSource" @tab-change="handleSourceChange">
          <el-tab-pane label="全部" name="all" />
          <el-tab-pane label="微博" name="weibo" />
          <el-tab-pane label="知乎" name="zhihu" />
          <el-tab-pane label="百度" name="baidu" />
        </el-tabs>

        <div class="time-filter">
          <span>最近</span>
          <el-select v-model="days" size="default" style="width: 120px" @change="fetchStatistics">
            <el-option :value="1" label="1天" />
            <el-option :value="7" label="7天" />
            <el-option :value="30" label="30天" />
          </el-select>
        </div>
      </div>
    </div>

    <div class="card">
      <el-table
        :data="recordList"
        v-loading="loading"
        style="width: 100%"
        stripe
      >
        <el-table-column type="index" label="序号" width="80" align="center" />

        <el-table-column prop="source" label="数据源" width="120" align="center">
          <template #default="{ row }">
            <span :class="['source-tag', row.source]">{{ getSourceName(row.source) }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'success' ? 'success' : 'danger'" size="small">
              {{ row.status === 'success' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="eventCount" label="总数量" width="100" align="right" />

        <el-table-column prop="successCount" label="成功数" width="100" align="right">
          <template #default="{ row }">
            <span style="color: #67c23a;">{{ row.successCount || 0 }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="failCount" label="失败数" width="100" align="right">
          <template #default="{ row }">
            <span style="color: #f56c6c;">{{ row.failCount || 0 }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="costTimeMs" label="耗时" width="120" align="right">
          <template #default="{ row }">
            {{ formatCostTime(row.costTimeMs) }}
          </template>
        </el-table-column>

        <el-table-column prop="crawlTime" label="抓取时间" width="180" align="center">
          <template #default="{ row }">
            {{ formatTime(row.crawlTime) }}
          </template>
        </el-table-column>

        <el-table-column prop="errorMessage" label="错误信息" min-width="200">
          <template #default="{ row }">
            <span v-if="row.errorMessage">{{ row.errorMessage }}</span>
            <span v-else style="color: #c0c4cc;">-</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { getCrawlRecordList, getCrawlStatistics } from '@/api/crawlRecord'
import dayjs from 'dayjs'

const loading = ref(false)
const recordList = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const activeSource = ref('all')
const days = ref(7)
const stats = ref({})

const fetchRecordList = async () => {
  loading.value = true
  try {
    const params = {
      page: currentPage.value,
      size: pageSize.value
    }

    if (activeSource.value && activeSource.value !== 'all') {
      params.source = activeSource.value
    }

    const data = await getCrawlRecordList(params)
    recordList.value = data.records || []
    total.value = data.total || 0
  } catch (error) {
    console.error('获取抓取记录失败', error)
  } finally {
    loading.value = false
  }
}

const fetchStatistics = async () => {
  try {
    stats.value = await getCrawlStatistics(days.value)
  } catch (error) {
    console.error('获取统计数据失败', error)
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

const formatTime = (time) => {
  if (!time) return '-'
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
}

const formatCostTime = (ms) => {
  if (!ms) return '0ms'
  if (ms < 1000) {
    return ms + 'ms'
  }
  return (ms / 1000).toFixed(2) + 's'
}

const handleSourceChange = () => {
  currentPage.value = 1
  fetchRecordList()
}

const handleSizeChange = (size) => {
  pageSize.value = size
  currentPage.value = 1
  fetchRecordList()
}

const handleCurrentChange = (page) => {
  currentPage.value = page
  fetchRecordList()
}

onMounted(() => {
  fetchRecordList()
  fetchStatistics()
})
</script>

<style scoped lang="scss">
.crawl-records-page {
  .filter-bar {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .time-filter {
      display: flex;
      align-items: center;
      gap: 8px;
      color: #606266;
    }
  }

  .pagination-container {
    display: flex;
    justify-content: flex-end;
    margin-top: 20px;
  }
}
</style>
