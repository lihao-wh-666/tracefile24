<template>
  <div class="sys-config-management">
    <div class="page-header">
      <h2 class="page-title">系统管理</h2>
    </div>

    <el-tabs v-model="activeTab" class="config-tabs">
      <el-tab-pane label="系统参数" name="system">
        <div class="card">
          <div class="tab-header">
            <el-button type="primary" @click="handleAdd">
              <el-icon><Plus /></el-icon>
              <span>新增参数</span>
            </el-button>
          </div>
          <el-table :data="tableData" v-loading="loading" stripe style="width: 100%">
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="configName" label="配置名称" width="220" />
            <el-table-column prop="configKey" label="配置键" width="260" />
            <el-table-column prop="configValue" label="配置值" width="200" />
            <el-table-column prop="description" label="描述" />
            <el-table-column prop="updateTime" label="更新时间" width="180">
              <template #default="{ row }">
                {{ formatDate(row.updateTime) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <el-button
                  type="primary"
                  link
                  size="small"
                  @click="handleEdit(row)"
                >
                  编辑
                </el-button>
                <el-button
                  type="danger"
                  link
                  size="small"
                  @click="handleDelete(row)"
                  :disabled="isSystemConfig(row.configKey)"
                >
                  删除
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <el-tab-pane label="平台配置" name="platform">
        <div class="card">
          <div class="tab-header">
            <span class="tab-desc">配置各数据采集平台的抓取开关和采集间隔</span>
          </div>
          <el-table :data="platformList" v-loading="platformLoading" stripe style="width: 100%">
            <el-table-column prop="name" label="平台名称" width="180">
              <template #default="{ row }">
                <div class="platform-name-cell">
                  <span class="platform-name">{{ row.name }}</span>
                  <el-tag size="small" :type="getPlatformTypeTag(row.type)">
                    {{ getPlatformTypeName(row.type) }}
                  </el-tag>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="code" label="平台标识" width="150" />
            <el-table-column label="采集状态" width="120">
              <template #default="{ row }">
                <el-switch
                  v-model="row.enabled"
                  :loading="row.switchLoading"
                  @change="(val) => handlePlatformToggle(row, val)"
                  active-text="启用"
                  inactive-text="禁用"
                />
              </template>
            </el-table-column>
            <el-table-column label="采集间隔(分钟)" width="180">
              <template #default="{ row }">
              <span v-if="row.editing">
                <el-input-number
                  v-model="row.editInterval"
                  :min="1"
                  :max="1440"
                  size="small"
                  style="width: 120px"
                />
                <el-button
                  type="primary"
                  link
                  size="small"
                  @click="saveInterval(row)"
                  :loading="row.saving"
                >
                  保存
                </el-button>
                <el-button
                  link
                  size="small"
                  @click="cancelEditInterval(row)"
                >
                  取消
                </el-button>
              </span>
              <span v-else>
                {{ row.intervalMinutes }} 分钟
                <el-button
                  type="primary"
                  link
                  size="small"
                  @click="startEditInterval(row)"
                >
                  修改
                </el-button>
              </span>
              </template>
            </el-table-column>
            <el-table-column prop="priority" label="优先级" width="100" />
            <el-table-column prop="maxConcurrent" label="最大并发" width="100" />
            <el-table-column label="Cron表达式" width="200">
              <template #default="{ row }">
                <span class="cron-text">{{ row.cron || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right">
              <template #default="{ row }">
                <el-button
                  type="primary"
                  link
                  size="small"
                  @click="handlePlatformEdit(row)"
                >
                  更多配置
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑参数' : '新增参数'"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="formRules"
        label-width="100px"
      >
        <el-form-item label="配置键" prop="configKey">
          <el-input v-model="form.configKey" :disabled="isEdit" placeholder="请输入配置键（英文，如 maxSize）" />
        </el-form-item>
        <el-form-item label="配置名称" prop="configName">
          <el-input v-model="form.configName" placeholder="请输入配置名称（中文描述）" />
        </el-form-item>
        <el-form-item label="配置值" prop="configValue">
          <el-input v-model="form.configValue" placeholder="请输入配置值" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="请输入配置描述"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          确定
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="platformDialogVisible"
      title="平台配置"
      width="520px"
      :close-on-click-modal="false"
    >
      <el-form
        ref="platformFormRef"
        :model="platformForm"
        :rules="platformFormRules"
        label-width="120px"
      >
        <el-form-item label="平台名称">
          <el-input v-model="platformForm.name" disabled />
        </el-form-item>
        <el-form-item label="采集开关">
          <el-switch v-model="platformForm.enabled" active-text="启用" inactive-text="禁用" />
        </el-form-item>
        <el-form-item label="采集间隔(分钟)" prop="intervalMinutes">
          <el-input-number v-model="platformForm.intervalMinutes" :min="1" :max="1440" style="width: 100%" />
        </el-form-item>
        <el-form-item label="优先级" prop="priority">
          <el-input-number v-model="platformForm.priority" :min="1" :max="10" style="width: 100%" />
        </el-form-item>
        <el-form-item label="最大并发数" prop="maxConcurrent">
          <el-input-number v-model="platformForm.maxConcurrent" :min="1" :max="10" style="width: 100%" />
        </el-form-item>
        <el-form-item label="Cron表达式">
          <el-input v-model="platformForm.cron" disabled />
          <div class="form-tip">采集间隔修改后会自动更新Cron表达式</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="platformDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="platformSubmitting" @click="handlePlatformSubmit">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import { getSysConfigList, createSysConfig, updateSysConfig, deleteSysConfig } from '@/api/sysConfig'
import {
  getPlatformConfigs,
  enablePlatform,
  disablePlatform,
  updatePlatformConfig
} from '@/api/multiCrawler'
import { useMessageConfigStore } from '@/stores/messageConfig'
import { useCrawlerConfigStore } from '@/stores/crawlerConfig'
import message from '@/utils/message'

const SYSTEM_CONFIG_KEYS = ['maxLoginAttempts', 'loginLockMinutes', 'loginAttemptWindowMinutes', 'sessionTimeoutMinutes', 'sessionWarningMinutes', 'messageDuration', 'crawlIntervalMinutes']

const messageConfigStore = useMessageConfigStore()
const crawlerConfigStore = useCrawlerConfigStore()

const activeTab = ref('system')

const loading = ref(false)
const tableData = ref([])
const dialogVisible = ref(false)
const submitting = ref(false)
const isEdit = ref(false)
const formRef = ref(null)
const editId = ref(null)

const form = reactive({
  configKey: '',
  configName: '',
  configValue: '',
  description: ''
})

const formRules = {
  configKey: [
    { required: true, message: '请输入配置键', trigger: 'blur' },
    { pattern: /^[a-zA-Z][a-zA-Z0-9_]*$/, message: '配置键只能包含英文字母、数字和下划线，且以字母开头', trigger: 'blur' }
  ],
  configName: [{ required: true, message: '请输入配置名称', trigger: 'blur' }],
  configValue: [{ required: true, message: '请输入配置值', trigger: 'blur' }]
}

const platformLoading = ref(false)
const platformList = ref([])
const platformDialogVisible = ref(false)
const platformSubmitting = ref(false)
const platformFormRef = ref(null)
const currentPlatformCode = ref('')

const platformForm = reactive({
  code: '',
  name: '',
  enabled: true,
  intervalMinutes: 30,
  priority: 5,
  maxConcurrent: 2,
  cron: ''
})

const platformFormRules = {
  intervalMinutes: [
    { required: true, message: '请输入采集间隔', trigger: 'blur' },
    { type: 'number', min: 1, max: 1440, message: '采集间隔需在1-1440分钟之间', trigger: 'blur' }
  ],
  priority: [
    { required: true, message: '请输入优先级', trigger: 'blur' }
  ],
  maxConcurrent: [
    { required: true, message: '请输入最大并发数', trigger: 'blur' }
  ]
}

const isSystemConfig = (configKey) => {
  return SYSTEM_CONFIG_KEYS.includes(configKey)
}

const formatDate = (date) => {
  return date ? dayjs(date).format('YYYY-MM-DD HH:mm:ss') : '-'
}

const getPlatformTypeTag = (type) => {
  const typeMap = {
    social_media: '',
    short_video: 'warning',
    bbs: 'info',
    government: 'success'
  }
  return typeMap[type] || 'info'
}

const getPlatformTypeName = (type) => {
  const nameMap = {
    social_media: '社交媒体',
    short_video: '短视频',
    bbs: '论坛',
    government: '政务'
  }
  return nameMap[type] || type
}

const fetchList = async () => {
  loading.value = true
  try {
    const data = await getSysConfigList()
    tableData.value = data
  } catch (error) {
  } finally {
    loading.value = false
  }
}

const fetchPlatformList = async () => {
  platformLoading.value = true
  try {
    const data = await getPlatformConfigs()
    platformList.value = data.map(item => ({
      ...item,
      switchLoading: false,
      editing: false,
      editInterval: item.intervalMinutes,
      saving: false
    }))
  } catch (error) {
  } finally {
    platformLoading.value = false
  }
}

const resetForm = () => {
  form.configKey = ''
  form.configName = ''
  form.configValue = ''
  form.description = ''
  editId.value = null
}

const handleAdd = () => {
  isEdit.value = false
  resetForm()
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  editId.value = row.id
  form.configKey = row.configKey
  form.configName = row.configName
  form.configValue = row.configValue
  form.description = row.description
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      submitting.value = true
      try {
        if (isEdit.value) {
          await updateSysConfig(editId.value, {
            configValue: form.configValue,
            configName: form.configName,
            description: form.description
          })
          if (form.configKey === 'messageDuration') {
            const duration = parseInt(form.configValue)
            if (!isNaN(duration) && duration > 0) {
              messageConfigStore.setDuration(duration)
            }
          }
          if (form.configKey === 'crawlIntervalMinutes') {
            const interval = parseInt(form.configValue)
            if (!isNaN(interval) && interval > 0) {
              crawlerConfigStore.setInterval(interval)
            }
          }
          message.success('更新成功')
        } else {
          await createSysConfig({
            configKey: form.configKey,
            configName: form.configName,
            configValue: form.configValue,
            description: form.description
          })
          message.success('创建成功')
        }
        dialogVisible.value = false
        fetchList()
      } catch (error) {
      } finally {
        submitting.value = false
      }
    }
  })
}

const handleDelete = (row) => {
  ElMessageBox.confirm(
    `确定要删除参数 "${row.configName || row.configKey}" 吗？`,
    '删除确认',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    try {
      await deleteSysConfig(row.id)
      message.success('删除成功')
      fetchList()
    } catch (error) {
    }
  }).catch(() => {})
}

const handlePlatformToggle = async (row, val) => {
  row.switchLoading = true
  try {
    if (val) {
      await enablePlatform(row.code)
      message.success(`已启用 ${row.name}`)
    } else {
      await disablePlatform(row.code)
      message.success(`已禁用 ${row.name}`)
    }
  } catch (error) {
    row.enabled = !val
  } finally {
    row.switchLoading = false
  }
}

const startEditInterval = (row) => {
  row.editInterval = row.intervalMinutes
  row.editing = true
}

const cancelEditInterval = (row) => {
  row.editing = false
}

const saveInterval = async (row) => {
  if (row.editInterval <= 0) {
    message.warning('采集间隔必须大于0')
    return
  }
  row.saving = true
  try {
    await updatePlatformConfig(row.code, {
      intervalMinutes: row.editInterval
    })
    row.intervalMinutes = row.editInterval
    row.cron = `0 */${row.editInterval} * * * ?`
    row.editing = false
    message.success('采集间隔更新成功')
  } catch (error) {
  } finally {
    row.saving = false
  }
}

const handlePlatformEdit = (row) => {
  currentPlatformCode.value = row.code
  platformForm.code = row.code
  platformForm.name = row.name
  platformForm.enabled = row.enabled
  platformForm.intervalMinutes = row.intervalMinutes
  platformForm.priority = row.priority
  platformForm.maxConcurrent = row.maxConcurrent
  platformForm.cron = row.cron
  platformDialogVisible.value = true
}

const handlePlatformSubmit = async () => {
  if (!platformFormRef.value) return
  await platformFormRef.value.validate(async (valid) => {
    if (valid) {
      platformSubmitting.value = true
      try {
        if (platformForm.enabled) {
          await enablePlatform(currentPlatformCode.value)
        } else {
          await disablePlatform(currentPlatformCode.value)
        }
        await updatePlatformConfig(currentPlatformCode.value, {
          intervalMinutes: platformForm.intervalMinutes,
          priority: platformForm.priority,
          maxConcurrent: platformForm.maxConcurrent
        })
        message.success('平台配置更新成功')
        platformDialogVisible.value = false
        fetchPlatformList()
      } catch (error) {
      } finally {
        platformSubmitting.value = false
      }
    }
  })
}

onMounted(() => {
  fetchList()
  fetchPlatformList()
})
</script>

<style scoped lang="scss">
.sys-config-management {
  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;
  }

  .config-tabs {
    :deep(.el-tabs__header) {
      margin-bottom: 16px;
    }
  }

  .card {
    background: #fff;
    border-radius: 8px;
    padding: 20px;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  }

  .tab-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 16px;

    .tab-desc {
      color: #909399;
      font-size: 14px;
    }
  }

  .platform-name-cell {
    display: flex;
    align-items: center;
    gap: 8px;

    .platform-name {
      font-weight: 500;
    }
  }

  .cron-text {
    font-family: 'Courier New', monospace;
    font-size: 13px;
    color: #606266;
    background: #f5f7fa;
    padding: 2px 6px;
    border-radius: 4px;
  }

  .form-tip {
    font-size: 12px;
    color: #909399;
    margin-top: 4px;
  }
}
</style>
