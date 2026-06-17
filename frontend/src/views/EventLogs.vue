<template>
  <div class="event-logs-page">
    <div class="page-header">
      <h2 class="page-title">{{ $t('eventLogs.page') }}</h2>
      <div class="header-actions">
        <el-button @click="handleRefresh">
          <el-icon><Refresh /></el-icon>
          <span>{{ $t('common.refresh') }}</span>
        </el-button>
      </div>
    </div>

    <div class="card mb-20">
      <div class="filter-bar">
        <div class="filter-row">
          <div class="filter-item">
            <label class="filter-label">{{ $t('common.timeRange') }}</label>
            <el-date-picker
              v-model="dateRange"
              type="datetimerange"
              range-separator="-"
              :start-placeholder="$t('common.startTime')"
              :end-placeholder="$t('common.endTime')"
              value-format="YYYY-MM-DDTHH:mm:ss"
              class="filter-date"
            />
          </div>
          <div class="filter-item">
            <label class="filter-label">{{ $t('common.source') }}</label>
            <el-select
              v-model="searchSource"
              :placeholder="$t('common.selectSource')"
              clearable
              class="filter-select"
            >
              <el-option
                v-for="src in sourceList"
                :key="src"
                :label="getSourceName(src)"
                :value="src"
              />
            </el-select>
          </div>
          <div class="filter-item">
            <label class="filter-label">{{ $t('eventLogs.operator') }}</label>
            <el-input
              v-model="searchOperator"
              :placeholder="$t('eventLogs.operatorPlaceholder')"
              clearable
              class="filter-input"
            />
          </div>
          <div class="filter-item">
            <label class="filter-label">{{ $t('eventLogs.operationType') }}</label>
            <el-select
              v-model="searchOperationType"
              :placeholder="$t('common.selectAll')"
              clearable
              class="filter-select"
            >
              <el-option :label="$t('eventLogs.typeInsert')" value="INSERT" />
              <el-option :label="$t('eventLogs.typeUpdate')" value="UPDATE" />
              <el-option :label="$t('eventLogs.typeDelete')" value="DELETE" />
            </el-select>
          </div>
          <div class="filter-item filter-actions">
            <el-button type="primary" @click="handleSearch">
              <el-icon><Search /></el-icon>
              <span>{{ $t('common.search') }}</span>
            </el-button>
            <el-button @click="handleReset">
              <el-icon><RefreshRight /></el-icon>
              <span>{{ $t('common.reset') }}</span>
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <div class="card">
      <el-table
        :data="logList"
        v-loading="loading"
        style="width: 100%"
        stripe
      >
        <el-table-column prop="operationTime" :label="$t('eventLogs.operationTime')" width="180" align="center">
          <template #default="{ row }">
            {{ formatTime(row.operationTime) }}
          </template>
        </el-table-column>

        <el-table-column prop="operationType" :label="$t('eventLogs.operationType')" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getOperationTypeTag(row.operationType)" size="small">
              {{ getOperationTypeName(row.operationType) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="eventTitle" :label="$t('eventLogs.eventTitle')" min-width="250">
          <template #default="{ row }">
            <div class="event-title" @click="goToEvent(row.eventId)">
              {{ row.eventTitle || '-' }}
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="source" :label="$t('common.source')" width="100" align="center">
          <template #default="{ row }">
            <span v-if="row.source" :class="['source-tag', row.source]">{{ getSourceName(row.source) }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>

        <el-table-column prop="fieldName" :label="$t('eventLogs.fieldName')" width="140" align="center">
          <template #default="{ row }">
            {{ row.fieldName || '-' }}
          </template>
        </el-table-column>

        <el-table-column :label="$t('eventLogs.valueChange')" min-width="300">
          <template #default="{ row }">
            <div class="value-change">
              <div class="old-value" v-if="row.oldValue">
                <span class="value-label">{{ $t('eventLogs.oldValue') }}:</span>
                <span class="value-text">{{ truncateText(row.oldValue, 80) }}</span>
              </div>
              <div class="new-value" v-if="row.newValue">
                <span class="value-label">{{ $t('eventLogs.newValue') }}:</span>
                <span class="value-text">{{ truncateText(row.newValue, 80) }}</span>
              </div>
              <el-button v-if="row.oldValue || row.newValue" type="primary" link size="small" @click="showDetail(row)">
                {{ $t('common.view') }}
              </el-button>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="operatorName" :label="$t('eventLogs.operator')" width="120" align="center">
          <template #default="{ row }">
            {{ row.operatorName || '-' }}
          </template>
        </el-table-column>

        <el-table-column prop="reason" :label="$t('eventLogs.reason')" width="150" align="center">
          <template #default="{ row }">
            {{ row.reason || '-' }}
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

    <el-dialog
      v-model="detailDialogVisible"
      :title="$t('eventLogs.logDetail')"
      width="600px"
    >
      <div v-if="currentLog" class="log-detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item :label="$t('eventLogs.operationTime')">
            {{ formatTime(currentLog.operationTime) }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('eventLogs.operationType')">
            <el-tag :type="getOperationTypeTag(currentLog.operationType)" size="small">
              {{ getOperationTypeName(currentLog.operationType) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('eventLogs.eventTitle')" :span="2">
            {{ currentLog.eventTitle || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('common.source')">
            <span v-if="currentLog.source" :class="['source-tag', currentLog.source]">
              {{ getSourceName(currentLog.source) }}
            </span>
            <span v-else>-</span>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('eventLogs.fieldName')">
            {{ currentLog.fieldName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('eventLogs.operator')">
            {{ currentLog.operatorName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('eventLogs.reason')">
            {{ currentLog.reason || '-' }}
          </el-descriptions-item>
          <el-descriptions-item :label="$t('eventLogs.oldValue')" :span="2">
            <div class="detail-value old">{{ currentLog.oldValue || '-' }}</div>
          </el-descriptions-item>
          <el-descriptions-item :label="$t('eventLogs.newValue')" :span="2">
            <div class="detail-value new">{{ currentLog.newValue || '-' }}</div>
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="detailDialogVisible = false">{{ $t('common.close') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { getEventLogList, getEventLogSources } from '@/api/eventLog'
import { getPlatformName } from '@/utils/platform'
import dayjs from 'dayjs'

const { t } = useI18n()
const router = useRouter()

const loading = ref(false)
const logList = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)

const dateRange = ref([])
const searchSource = ref('')
const searchOperator = ref('')
const searchOperationType = ref('')
const sourceList = ref([])

const detailDialogVisible = ref(false)
const currentLog = ref(null)

const fetchLogList = async () => {
  loading.value = true
  try {
    const params = {
      page: currentPage.value,
      size: pageSize.value
    }

    if (dateRange.value && dateRange.value.length === 2) {
      params.startTime = dateRange.value[0]
      params.endTime = dateRange.value[1]
    }

    if (searchSource.value) {
      params.source = searchSource.value
    }

    if (searchOperator.value) {
      params.operator = searchOperator.value
    }

    if (searchOperationType.value) {
      params.operationType = searchOperationType.value
    }

    const data = await getEventLogList(params)
    logList.value = data.records || []
    total.value = data.total || 0
  } catch (error) {
    console.error(t('eventLogs.fetchFailed'), error)
  } finally {
    loading.value = false
  }
}

const fetchSources = async () => {
  try {
    const data = await getEventLogSources()
    sourceList.value = data || []
  } catch (error) {
    console.error('Failed to fetch sources', error)
  }
}

const getSourceName = (source) => {
  return getPlatformName(source)
}

const getOperationTypeName = (type) => {
  const typeMap = {
    'INSERT': t('eventLogs.typeInsert'),
    'UPDATE': t('eventLogs.typeUpdate'),
    'DELETE': t('eventLogs.typeDelete')
  }
  return typeMap[type] || type
}

const getOperationTypeTag = (type) => {
  const tagMap = {
    'INSERT': 'success',
    'UPDATE': 'warning',
    'DELETE': 'danger'
  }
  return tagMap[type] || 'info'
}

const formatTime = (time) => {
  if (!time) return '-'
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
}

const truncateText = (text, maxLength) => {
  if (!text) return '-'
  if (text.length <= maxLength) return text
  return text.substring(0, maxLength) + '...'
}

const handleSearch = () => {
  currentPage.value = 1
  fetchLogList()
}

const handleReset = () => {
  dateRange.value = []
  searchSource.value = ''
  searchOperator.value = ''
  searchOperationType.value = ''
  currentPage.value = 1
  fetchLogList()
}

const handleRefresh = () => {
  fetchLogList()
  fetchSources()
}

const handleSizeChange = (size) => {
  pageSize.value = size
  currentPage.value = 1
  fetchLogList()
}

const handleCurrentChange = (page) => {
  currentPage.value = page
  fetchLogList()
}

const goToEvent = (eventId) => {
  if (eventId) {
    router.push(`/events/${eventId}`)
  }
}

const showDetail = (row) => {
  currentLog.value = row
  detailDialogVisible.value = true
}

onMounted(() => {
  fetchSources()
  fetchLogList()
})
</script>

<style scoped lang="scss">
.event-logs-page {
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
    .filter-row {
      display: flex;
      flex-wrap: wrap;
      gap: 20px;
      align-items: flex-end;
    }

    .filter-item {
      display: flex;
      flex-direction: column;
      gap: 6px;

      &.filter-actions {
        flex-direction: row;
        align-items: center;
        gap: 12px;
      }
    }

    .filter-label {
      font-size: 14px;
      color: #606266;
      font-weight: 500;
    }

    .filter-select {
      width: 160px;
    }

    .filter-input {
      width: 160px;
    }

    .filter-date {
      width: 380px;
    }
  }

  .event-title {
    cursor: pointer;
    color: #303133;

    &:hover {
      color: #409eff;
    }
  }

  .value-change {
    display: flex;
    flex-direction: column;
    gap: 4px;

    .value-label {
      color: #909399;
      font-size: 12px;
      margin-right: 4px;
    }

    .value-text {
      color: #606266;
      font-size: 13px;
    }

    .old-value .value-text {
      color: #f56c6c;
    }

    .new-value .value-text {
      color: #67c23a;
    }
  }

  .pagination-container {
    display: flex;
    justify-content: flex-end;
    margin-top: 20px;
  }

  .log-detail {
    .detail-value {
      padding: 8px 12px;
      border-radius: 4px;
      font-family: monospace;
      font-size: 13px;
      word-break: break-all;
      white-space: pre-wrap;
      max-height: 200px;
      overflow-y: auto;

      &.old {
        background: #fef0f0;
        color: #f56c6c;
      }

      &.new {
        background: #f0f9eb;
        color: #67c23a;
      }
    }
  }
}
</style>
