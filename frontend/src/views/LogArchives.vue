<template>
  <div class="log-archives-page">
    <div class="page-header">
      <h2 class="page-title">{{ $t('logArchives.page') }}</h2>
      <div class="header-actions">
        <el-button type="primary" @click="showCreateDialog = true">
          <el-icon><FolderAdd /></el-icon>
          <span>{{ $t('logArchives.createArchive') }}</span>
        </el-button>
        <el-button @click="handleAutoArchive" :loading="autoArchiving">
          <el-icon><Timer /></el-icon>
          <span>{{ $t('logArchives.autoArchive') }}</span>
        </el-button>
        <el-button @click="fetchData">
          <el-icon><Refresh /></el-icon>
          <span>{{ $t('common.refresh') }}</span>
        </el-button>
      </div>
    </div>

    <div class="stats-row mb-20">
      <div class="stat-card">
        <div class="stat-value">{{ statistics.totalCount || 0 }}</div>
        <div class="stat-label">{{ $t('logArchives.totalArchives') }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ statistics.completedCount || 0 }}</div>
        <div class="stat-label">{{ $t('logArchives.completedArchives') }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ formatFileSize(statistics.totalOriginalSize) }}</div>
        <div class="stat-label">{{ $t('logArchives.originalSize') }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ formatFileSize(statistics.totalArchivedSize) }}</div>
        <div class="stat-label">{{ $t('logArchives.archivedSize') }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ statistics.compressionRatio || 0 }}%</div>
        <div class="stat-label">{{ $t('logArchives.compressionRatio') }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ statistics.totalLogCount || 0 }}</div>
        <div class="stat-label">{{ $t('logArchives.totalLogCount') }}</div>
      </div>
    </div>

    <div class="stats-row mb-20" v-if="statistics.typeStats">
      <div class="stat-card stat-card-mini">
        <div class="stat-value-sm">{{ getTypeStat('DATABASE_LOG', 'count') || 0 }}</div>
        <div class="stat-label">{{ $t('logArchives.typeDatabase') }}</div>
        <div class="stat-desc">{{ formatFileSize(getTypeStat('DATABASE_LOG', 'originalSize')) }} / {{ getTypeStat('DATABASE_LOG', 'logCount') }} {{ $t('logArchives.entries') }}</div>
      </div>
      <div class="stat-card stat-card-mini">
        <div class="stat-value-sm">{{ getTypeStat('BACKEND_LOG', 'count') || 0 }}</div>
        <div class="stat-label">{{ $t('logArchives.typeBackend') }}</div>
        <div class="stat-desc">{{ formatFileSize(getTypeStat('BACKEND_LOG', 'originalSize')) }} / {{ getTypeStat('BACKEND_LOG', 'logCount') }} {{ $t('logArchives.files') }}</div>
      </div>
      <div class="stat-card stat-card-mini">
        <div class="stat-value-sm">{{ getTypeStat('FRONTEND_LOG', 'count') || 0 }}</div>
        <div class="stat-label">{{ $t('logArchives.typeFrontend') }}</div>
        <div class="stat-desc">{{ formatFileSize(getTypeStat('FRONTEND_LOG', 'originalSize')) }} / {{ getTypeStat('FRONTEND_LOG', 'logCount') }} {{ $t('logArchives.entries') }}</div>
      </div>
    </div>

    <div class="card mb-20">
      <div class="filter-bar">
        <div class="filter-row">
          <div class="filter-item">
            <label class="filter-label">{{ $t('logArchives.logType') }}</label>
            <el-select
              v-model="searchLogType"
              :placeholder="$t('common.selectAll')"
              clearable
              class="filter-select"
              @change="handleSearch"
            >
              <el-option :label="$t('logArchives.typeDatabase')" value="DATABASE_LOG" />
              <el-option :label="$t('logArchives.typeBackend')" value="BACKEND_LOG" />
              <el-option :label="$t('logArchives.typeFrontend')" value="FRONTEND_LOG" />
            </el-select>
          </div>
          <div class="filter-item">
            <label class="filter-label">{{ $t('logArchives.status') }}</label>
            <el-select
              v-model="searchStatus"
              :placeholder="$t('common.selectAll')"
              clearable
              class="filter-select"
              @change="handleSearch"
            >
              <el-option :label="$t('logArchives.statusPending')" value="PENDING" />
              <el-option :label="$t('logArchives.statusArchiving')" value="ARCHIVING" />
              <el-option :label="$t('logArchives.statusCompleted')" value="COMPLETED" />
              <el-option :label="$t('logArchives.statusFailed')" value="FAILED" />
            </el-select>
          </div>
        </div>
      </div>
    </div>

    <div class="card">
      <el-table
        :data="archiveList"
        v-loading="loading"
        style="width: 100%"
        stripe
      >
        <el-table-column prop="archiveName" :label="$t('logArchives.archiveName')" min-width="280">
          <template #default="{ row }">
            <span class="archive-name">{{ row.archiveName }}</span>
          </template>
        </el-table-column>

        <el-table-column :label="$t('logArchives.timeRange')" min-width="260">
          <template #default="{ row }">
            <div class="time-range">
              <span>{{ formatTime(row.startTime) }}</span>
              <span class="time-separator">~</span>
              <span>{{ formatTime(row.endTime) }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="logCount" :label="$t('logArchives.logCount')" width="100" align="center">
          <template #default="{ row }">
            {{ row.logCount ?? '-' }}
          </template>
        </el-table-column>

        <el-table-column prop="logType" :label="$t('logArchives.logType')" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="getLogTypeTagType(row.logType)" size="small">
              {{ getLogTypeName(row.logType) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column :label="$t('logArchives.originalSize')" width="120" align="center">
          <template #default="{ row }">
            {{ row.originalSizeBytes ? formatFileSize(row.originalSizeBytes) : '-' }}
          </template>
        </el-table-column>

        <el-table-column :label="$t('logArchives.archivedSize')" width="120" align="center">
          <template #default="{ row }">
            {{ row.archivedSizeBytes ? formatFileSize(row.archivedSizeBytes) : '-' }}
          </template>
        </el-table-column>

        <el-table-column prop="status" :label="$t('logArchives.status')" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.status)" size="small">
              {{ getStatusName(row.status) }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="remark" :label="$t('logArchives.remark')" min-width="180">
          <template #default="{ row }">
            {{ row.remark || '-' }}
          </template>
        </el-table-column>

        <el-table-column prop="createTime" :label="$t('logArchives.createTime')" width="180" align="center">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>

        <el-table-column :label="$t('common.operation')" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'COMPLETED'"
              type="primary"
              link
              size="small"
              @click="handleDownload(row)"
            >
              {{ $t('logArchives.download') }}
            </el-button>
            <el-button
              type="danger"
              link
              size="small"
              @click="handleDelete(row)"
              :disabled="row.status === 'ARCHIVING'"
            >
              {{ $t('common.delete') }}
            </el-button>
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
      v-model="showCreateDialog"
      :title="$t('logArchives.createArchive')"
      width="520px"
      @close="resetCreateForm"
    >
      <el-form :model="createForm" label-width="120px">
        <el-form-item :label="$t('logArchives.logType')">
          <el-select v-model="createForm.logType" style="width: 100%">
            <el-option :label="$t('logArchives.typeDatabase')" value="DATABASE_LOG" />
            <el-option :label="$t('logArchives.typeBackend')" value="BACKEND_LOG" />
            <el-option :label="$t('logArchives.typeFrontend')" value="FRONTEND_LOG" />
          </el-select>
        </el-form-item>
        <el-form-item :label="$t('logArchives.startTime')">
          <el-date-picker
            v-model="createForm.startTime"
            type="datetime"
            :placeholder="$t('common.startTime')"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="$t('logArchives.endTime')">
          <el-date-picker
            v-model="createForm.endTime"
            type="datetime"
            :placeholder="$t('common.endTime')"
            value-format="YYYY-MM-DDTHH:mm:ss"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item :label="$t('logArchives.remark')">
          <el-input
            v-model="createForm.remark"
            type="textarea"
            :rows="3"
            :placeholder="$t('logArchives.remarkPlaceholder')"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">{{ $t('common.cancel') }}</el-button>
        <el-button type="primary" @click="handleCreateArchive" :loading="creating">
          {{ $t('common.confirm') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessageBox } from 'element-plus'
import {
  getLogArchiveList,
  createLogArchive,
  deleteLogArchive,
  downloadLogArchive,
  getLogArchiveStatistics,
  triggerAutoArchive
} from '@/api/logArchive'
import dayjs from 'dayjs'
import message from '@/utils/message'

const { t } = useI18n()

const loading = ref(false)
const archiveList = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(20)
const searchStatus = ref('')
const searchLogType = ref('')
const statistics = ref({})
const autoArchiving = ref(false)

const showCreateDialog = ref(false)
const creating = ref(false)
const createForm = ref({
  logType: 'DATABASE_LOG',
  startTime: '',
  endTime: '',
  remark: ''
})

const fetchArchiveList = async () => {
  loading.value = true
  try {
    const params = {
      page: currentPage.value,
      size: pageSize.value
    }
    if (searchStatus.value) {
      params.status = searchStatus.value
    }
    if (searchLogType.value) {
      params.logType = searchLogType.value
    }
    const data = await getLogArchiveList(params)
    archiveList.value = data.records || []
    total.value = data.total || 0
  } catch (error) {
    console.error('Failed to fetch archives', error)
  } finally {
    loading.value = false
  }
}

const fetchStatistics = async () => {
  try {
    const data = await getLogArchiveStatistics()
    statistics.value = data || {}
  } catch (error) {
    console.error('Failed to fetch statistics', error)
  }
}

const fetchData = () => {
  fetchArchiveList()
  fetchStatistics()
}

const formatTime = (time) => {
  if (!time) return '-'
  return dayjs(time).format('YYYY-MM-DD HH:mm:ss')
}

const formatFileSize = (bytes) => {
  if (!bytes || bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const k = 1024
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return (bytes / Math.pow(k, i)).toFixed(2) + ' ' + units[i]
}

const getStatusName = (status) => {
  const map = {
    'PENDING': t('logArchives.statusPending'),
    'ARCHIVING': t('logArchives.statusArchiving'),
    'COMPLETED': t('logArchives.statusCompleted'),
    'FAILED': t('logArchives.statusFailed')
  }
  return map[status] || status
}

const getStatusTagType = (status) => {
  const map = {
    'PENDING': 'info',
    'ARCHIVING': 'warning',
    'COMPLETED': 'success',
    'FAILED': 'danger'
  }
  return map[status] || 'info'
}

const getLogTypeName = (logType) => {
  const map = {
    'DATABASE_LOG': t('logArchives.typeDatabase'),
    'BACKEND_LOG': t('logArchives.typeBackend'),
    'FRONTEND_LOG': t('logArchives.typeFrontend')
  }
  return map[logType] || logType
}

const getLogTypeTagType = (logType) => {
  const map = {
    'DATABASE_LOG': 'primary',
    'BACKEND_LOG': 'success',
    'FRONTEND_LOG': 'warning'
  }
  return map[logType] || 'info'
}

const getTypeStat = (type, field) => {
  if (!statistics.value.typeStats || !statistics.value.typeStats[type]) {
    return 0
  }
  return statistics.value.typeStats[type][field]
}

const handleSearch = () => {
  currentPage.value = 1
  fetchArchiveList()
}

const handleSizeChange = (size) => {
  pageSize.value = size
  currentPage.value = 1
  fetchArchiveList()
}

const handleCurrentChange = (page) => {
  currentPage.value = page
  fetchArchiveList()
}

const handleCreateArchive = async () => {
  if (!createForm.value.startTime || !createForm.value.endTime) {
    message.warning(t('logArchives.timeRequired'))
    return
  }
  if (createForm.value.startTime >= createForm.value.endTime) {
    message.warning(t('logArchives.timeRangeInvalid'))
    return
  }
  creating.value = true
  try {
    await createLogArchive(createForm.value.startTime, createForm.value.endTime, createForm.value.logType, createForm.value.remark)
    message.success(t('logArchives.createSuccess'))
    showCreateDialog.value = false
    resetCreateForm()
    fetchData()
  } catch (error) {
    message.error(t('logArchives.createFailed'))
  } finally {
    creating.value = false
  }
}

const handleAutoArchive = async () => {
  autoArchiving.value = true
  try {
    await triggerAutoArchive()
    message.success(t('logArchives.autoArchiveTriggered'))
    setTimeout(() => fetchData(), 2000)
  } catch (error) {
    message.error(t('logArchives.autoArchiveFailed'))
  } finally {
    autoArchiving.value = false
  }
}

const handleDownload = async (row) => {
  try {
    const response = await downloadLogArchive(row.id)
    const blob = new Blob([response.data])
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = row.archiveName + '.zip'
    link.click()
    window.URL.revokeObjectURL(url)
  } catch (error) {
    message.error(t('logArchives.downloadFailed') || 'Download failed')
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      t('logArchives.deleteConfirm'),
      t('common.warning'),
      { confirmButtonText: t('common.confirm'), cancelButtonText: t('common.cancel'), type: 'warning' }
    )
    await deleteLogArchive(row.id)
    message.success(t('common.success'))
    fetchData()
  } catch (e) {
    if (e !== 'cancel') {
      message.error(t('common.error'))
    }
  }
}

const resetCreateForm = () => {
  createForm.value = { logType: 'DATABASE_LOG', startTime: '', endTime: '', remark: '' }
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped lang="scss">
.log-archives-page {
  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .header-actions {
      display: flex;
      gap: 12px;
    }
  }

  .stats-row {
    display: flex;
    gap: 16px;
    flex-wrap: wrap;

    .stat-card {
      flex: 1;
      min-width: 140px;
      background: #fff;
      border-radius: 8px;
      padding: 16px 20px;
      box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
      text-align: center;

      .stat-value {
        font-size: 22px;
        font-weight: 600;
        color: #409eff;
        margin-bottom: 4px;
      }

      .stat-value-sm {
        font-size: 18px;
        font-weight: 600;
        color: #409eff;
        margin-bottom: 4px;
      }

      .stat-label {
        font-size: 13px;
        color: #909399;
      }

      .stat-desc {
        font-size: 12px;
        color: #c0c4cc;
        margin-top: 4px;
      }
    }

    .stat-card-mini {
      min-width: 200px;
      padding: 12px 16px;
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
    }

    .filter-label {
      font-size: 14px;
      color: #606266;
      font-weight: 500;
    }

    .filter-select {
      width: 160px;
    }
  }

  .archive-name {
    font-weight: 500;
    color: #303133;
  }

  .time-range {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;

    .time-separator {
      color: #c0c4cc;
    }
  }

  .pagination-container {
    display: flex;
    justify-content: flex-end;
    margin-top: 20px;
  }
}
</style>
