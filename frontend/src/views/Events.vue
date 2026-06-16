<template>
  <div class="events-page">
    <div class="page-header">
      <h2 class="page-title">{{ $t('events.page') }}</h2>
      <div class="header-actions">
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

    <div class="card mb-20">
      <div class="filter-bar">
        <el-tabs v-model="activeSource" @tab-change="handleSourceChange">
          <el-tab-pane :label="$t('common.all')" name="all" />
          <el-tab-pane
            v-for="platform in enabledPlatforms"
            :key="platform.code"
            :label="platform.name"
            :name="platform.code"
          />
        </el-tabs>

        <div class="search-bar">
          <el-input
            v-model="searchKeyword"
            :placeholder="$t('events.searchPlaceholder')"
            clearable
            @keyup.enter="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button type="primary" @click="handleSearch">
              <el-icon><Search /></el-icon>
              <span>{{ $t('common.search') }}</span>
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
        <el-table-column type="index" :label="$t('common.rank')" width="80" align="center">
          <template #default="{ row, $index }">
            <div class="rank-badge" :class="'rank-' + (row.hotRank || $index + 1)">
              {{ row.hotRank || $index + 1 }}
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="title" :label="$t('common.title')" min-width="300">
          <template #default="{ row }">
            <div class="event-title" @click="goToDetail(row.id)">
            {{ row.title }}
            <el-tag v-if="row.isRising" type="danger" size="small" effect="light">
              <el-icon><TrendCharts /></el-icon>
              {{ $t('common.rising') }}
            </el-tag>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="source" :label="$t('common.source')" width="100" align="center">
          <template #default="{ row }">
            <span :class="['source-tag', row.source]">{{ getSourceName(row.source) }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="category" :label="$t('common.category')" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small">{{ row.category || $t('common.uncategorized') }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="hotValue" :label="$t('common.hotValue')" width="140" align="right">
          <template #default="{ row }">
            <span class="hot-value">{{ formatHotValue(row.hotValue) }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="crawlTime" :label="$t('common.crawlTime')" width="180" align="center">
          <template #default="{ row }">
            {{ formatTime(row.crawlTime) }}
          </template>
        </el-table-column>

        <el-table-column :label="$t('common.operation')" width="120" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="goToDetail(row.id)">{{ $t('common.view') }}</el-button>
            <el-button type="danger" link @click="handleDelete(row.id)">{{ $t('common.delete') }}</el-button>
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
import { ref, onMounted, computed, watch, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessageBox } from 'element-plus'
import { getHotEventList, deleteHotEvent, exportHotEventsExcel, exportHotEventsCsv } from '@/api/event'
import { usePlatformConfigStore } from '@/stores/platformConfig'
import { getPlatformName } from '@/utils/platform'
import dayjs from 'dayjs'
import message from '@/utils/message'

const { t, locale } = useI18n()
const router = useRouter()
const platformConfigStore = usePlatformConfigStore()

const loading = ref(false)
const rawEventList = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const activeSource = ref('all')
const searchKeyword = ref('')

const enabledPlatforms = computed(() => platformConfigStore.enabledPlatforms)
const enabledPlatformCodes = computed(() => platformConfigStore.enabledPlatformCodes)

const eventList = computed(() => {
  let list = rawEventList.value
  if (enabledPlatformCodes.value.length > 0) {
    list = list.filter(event => enabledPlatformCodes.value.includes(event.source))
  }
  return list
})

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

    if (locale.value && locale.value !== 'zh-CN') {
      params.lang = locale.value
    }

    const data = await getHotEventList(params)
    rawEventList.value = data.records || []
    total.value = data.total || 0
  } catch (error) {
    console.error(t('events.fetchFailed'), error)
  } finally {
    loading.value = false
  }
}

const validateActiveSource = () => {
  if (activeSource.value !== 'all' && !enabledPlatformCodes.value.includes(activeSource.value)) {
    activeSource.value = 'all'
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
    await ElMessageBox.confirm(t('events.deleteConfirm'), t('common.warning'), {
      confirmButtonText: t('common.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'warning'
    })

    await deleteHotEvent(id)
    message.success(t('events.deleteSuccess'))
    fetchEventList()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败', error)
    }
  }
}

const handleExport = async (command) => {
  try {
    const params = {}
    if (activeSource.value && activeSource.value !== 'all') {
      params.source = activeSource.value
    }
    if (searchKeyword.value) {
      params.keyword = searchKeyword.value
    }

    if (command === 'excel') {
      await exportHotEventsExcel(params)
      message.success(t('common.exportSuccess'))
    } else if (command === 'csv') {
      await exportHotEventsCsv(params)
      message.success(t('common.exportSuccess'))
    }
  } catch (error) {
    console.error('导出失败', error)
    message.error(t('common.exportFailed'))
  }
}

onMounted(async () => {
  platformConfigStore.loadFromCache()
  await platformConfigStore.fetchPlatformConfigs()
  validateActiveSource()
  fetchEventList()
})

const unwatchEnabledPlatforms = watch(enabledPlatformCodes, () => {
  validateActiveSource()
}, { deep: true })

const unwatchLocale = watch(locale, () => {
  fetchEventList()
})

onUnmounted(() => {
  unwatchEnabledPlatforms()
  unwatchLocale()
})
</script>

<style scoped lang="scss">
.events-page {
  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .header-actions {
      display: flex;
      gap: 12px;
    }
  }

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
