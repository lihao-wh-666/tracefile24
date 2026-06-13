<template>
  <div class="events-page">
    <div class="page-header">
      <h2 class="page-title">热点事件</h2>
    </div>

    <div class="card mb-20">
      <div class="filter-bar">
        <el-tabs v-model="activeSource" @tab-change="handleSourceChange">
          <el-tab-pane label="全部" name="all" />
          <el-tab-pane label="微博" name="weibo" />
          <el-tab-pane label="知乎" name="zhihu" />
          <el-tab-pane label="百度" name="baidu" />
        </el-tabs>

        <div class="search-bar">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索热点事件..."
            clearable
            @keyup.enter="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button type="primary" @click="handleSearch">
              <el-icon><Search /></el-icon>
              <span>搜索</span>
            </el-button>
        </div>
      </div>
    </div>

    <div class="card">
      <el-table
        :data="eventList"
        v-loading="loading"
        style="width: 100%"
        stripe
      >
        <el-table-column type="index" label="排名" width="80" align="center">
          <template #default="{ row, $index }">
            <div class="rank-badge" :class="'rank-' + (row.hotRank || $index + 1)">
              {{ row.hotRank || $index + 1 }}
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="title" label="标题" min-width="300">
          <template #default="{ row }">
            <div class="event-title" @click="goToDetail(row.id)">
            {{ row.title }}
            <el-tag v-if="row.isRising" type="danger" size="small" effect="light">
              <el-icon><TrendCharts /></el-icon>
              飙升
            </el-tag>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="source" label="来源" width="100" align="center">
          <template #default="{ row }">
            <span :class="['source-tag', row.source]">{{ getSourceName(row.source) }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="category" label="分类" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small">{{ row.category || '未分类' }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="hotValue" label="热度" width="140" align="right">
          <template #default="{ row }">
            <span class="hot-value">{{ formatHotValue(row.hotValue) }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="crawlTime" label="抓取时间" width="180" align="center">
          <template #default="{ row }">
            {{ formatTime(row.crawlTime) }}
          </template>
        </el-table-column>

        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="goToDetail(row.id)">查看</el-button>
            <el-button type="danger" link @click="handleDelete(row.id)">删除</el-button>
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
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { getHotEventList, deleteHotEvent } from '@/api/event'
import dayjs from 'dayjs'
import message from '@/utils/message'

const router = useRouter()

const loading = ref(false)
const eventList = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const activeSource = ref('all')
const searchKeyword = ref('')

const fetchEventList = async () => {
  loading.value = true
  try {
    const params = {
      page: currentPage.value,
      size: pageSize.value
    }

    if (activeSource.value && activeSource.value !== 'all') {
      params.source = activeSource.value
    }

    if (searchKeyword.value) {
      params.keyword = searchKeyword.value
    }

    const data = await getHotEventList(params)
    eventList.value = data.records || []
    total.value = data.total || 0
  } catch (error) {
    console.error('获取热点事件列表失败', error)
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

const goToDetail = (id) => {
  router.push(`/events/${id}`)
}

const handleSourceChange = () => {
  currentPage.value = 1
  fetchEventList()
}

const handleSearch = () => {
  currentPage.value = 1
  fetchEventList()
}

const handleSizeChange = (size) => {
  pageSize.value = size
  currentPage.value = 1
  fetchEventList()
}

const handleCurrentChange = (page) => {
  currentPage.value = page
  fetchEventList()
}

const handleDelete = async (id) => {
  try {
    await ElMessageBox.confirm('确定要删除这条热点事件吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    await deleteHotEvent(id)
    message.success('删除成功')
    fetchEventList()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败', error)
    }
  }
}

onMounted(() => {
  fetchEventList()
})
</script>

<style scoped lang="scss">
.events-page {
  .filter-bar {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .search-bar {
      display: flex;
      gap: 12px;
    }
  }

  .rank-badge {
    width: 32px;
    height: 32px;
    line-height: 32px;
    text-align: center;
    border-radius: 6px;
    font-weight: 600;
    font-size: 14px;
    background: #f0f2f5;
    color: #909399;
    margin: 0 auto;

    &.rank-1,
    &.rank-2,
    &.rank-3 {
      color: #fff;
    }

    &.rank-1 {
      background: #ff4d4f;
    }

    &.rank-2 {
      background: #ff7a45;
    }

    &.rank-3 {
      background: #ffa940;
    }
  }

  .event-title {
    cursor: pointer;
    display: flex;
    align-items: center;
    gap: 8px;

    &:hover {
      color: #409eff;
    }
  }

  .pagination-container {
    display: flex;
    justify-content: flex-end;
    margin-top: 20px;
  }
}
</style>
