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
        <el-table-column prop="configName" label="配置名称" width="220">
          <template #default="{ row }">
            <el-input
              v-if="editingId === row.id"
              v-model="row.configName"
              size="small"
            />
            <span v-else>{{ row.configName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="configKey" label="配置键" width="260" />
        <el-table-column prop="configValue" label="配置值" width="200">
          <template #default="{ row }">
            <el-input
              v-if="editingId === row.id"
              v-model="row.configValue"
              size="small"
              style="width: 150px"
            />
            <span v-else>{{ row.configValue }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述">
          <template #default="{ row }">
            <el-input
              v-if="editingId === row.id"
              v-model="row.description"
              size="small"
            />
            <span v-else>{{ row.description }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="updateTime" label="更新时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.updateTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="editingId !== row.id"
              type="primary"
              link
              size="small"
              @click="handleEdit(row)"
            >
              编辑
            </el-button>
            <template v-else>
              <el-button
                type="success"
                link
                size="small"
                @click="handleSave(row)"
              >
                保存
              </el-button>
              <el-button
                type="info"
                link
                size="small"
                @click="handleCancel(row)"
              >
                取消
              </el-button>
            </template>
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
      title="新增参数"
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
          <el-input v-model="form.configKey" placeholder="请输入配置键（英文，如 maxSize）" />
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
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import { getSysConfigList, createSysConfig, updateSysConfig, deleteSysConfig } from '@/api/sysConfig'

const SYSTEM_CONFIG_KEYS = ['maxLoginAttempts', 'loginLockMinutes', 'loginAttemptWindowMinutes']

const loading = ref(false)
const tableData = ref([])
const editingId = ref(null)
const originalValues = ref({})
const dialogVisible = ref(false)
const submitting = ref(false)
const formRef = ref(null)

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
}

const handleAdd = () => {
  resetForm()
  dialogVisible.value = true
}

const handleEdit = (row) => {
  originalValues.value[row.id] = {
    configValue: row.configValue,
    configName: row.configName,
    description: row.description
  }
  editingId.value = row.id
}

const handleCancel = (row) => {
  const orig = originalValues.value[row.id]
  if (orig) {
    row.configValue = orig.configValue
    row.configName = orig.configName
    row.description = orig.description
  }
  editingId.value = null
}

const handleSave = async (row) => {
  if (!row.configValue || row.configValue.trim() === '') {
    ElMessage.error('配置值不能为空')
    return
  }
  try {
    await updateSysConfig(row.id, {
      configValue: row.configValue,
      configName: row.configName,
      description: row.description
    })
    ElMessage.success('更新成功')
    editingId.value = null
    fetchList()
  } catch (error) {
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      submitting.value = true
      try {
        await createSysConfig({
          configKey: form.configKey,
          configName: form.configName,
          configValue: form.configValue,
          description: form.description
        })
        ElMessage.success('创建成功')
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
      ElMessage.success('删除成功')
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
