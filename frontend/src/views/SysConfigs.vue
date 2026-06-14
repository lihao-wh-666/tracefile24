<template>
  <div class="sys-config-management">
    <div class="page-header">
      <h2 class="page-title">系统管理</h2>
      <el-button type="primary" @click="handleAdd">
        <el-icon><Plus /></el-icon>
        <span>新增参数</span>
      </el-button>
    </div>

    <div class="card">
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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import { getSysConfigList, createSysConfig, updateSysConfig, deleteSysConfig } from '@/api/sysConfig'
import { useMessageConfigStore } from '@/stores/messageConfig'
import { useCrawlerConfigStore } from '@/stores/crawlerConfig'
import message from '@/utils/message'

const SYSTEM_CONFIG_KEYS = ['maxLoginAttempts', 'loginLockMinutes', 'loginAttemptWindowMinutes', 'sessionTimeoutMinutes', 'sessionWarningMinutes', 'messageDuration', 'crawlIntervalMinutes']

const messageConfigStore = useMessageConfigStore()
const crawlerConfigStore = useCrawlerConfigStore()

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

const isSystemConfig = (configKey) => {
  return SYSTEM_CONFIG_KEYS.includes(configKey)
}

const formatDate = (date) => {
  return date ? dayjs(date).format('YYYY-MM-DD HH:mm:ss') : '-'
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

onMounted(() => {
  fetchList()
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

  .card {
    background: #fff;
    border-radius: 8px;
    padding: 20px;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  }
}
</style>
