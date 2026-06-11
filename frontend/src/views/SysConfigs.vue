<template>
  <div class="sys-config-management">
    <div class="page-header">
      <h2 class="page-title">系统管理</h2>
    </div>

    <div class="card">
      <el-table :data="tableData" v-loading="loading" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="configName" label="配置名称" width="220" />
        <el-table-column prop="configKey" label="配置键" width="260" />
        <el-table-column prop="configValue" label="配置值">
          <template #default="{ row }">
            <el-input
              v-if="editingId === row.id"
              v-model="row.configValue"
              type="number"
              min="1"
              size="small"
              style="width: 150px"
            />
            <span v-else>{{ row.configValue }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" />
        <el-table-column prop="updateTime" label="更新时间" width="200">
          <template #default="{ row }">
            {{ formatDate(row.updateTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
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
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import { getSysConfigList, updateSysConfig } from '@/api/sysConfig'

const loading = ref(false)
const tableData = ref([])
const editingId = ref(null)
const originalValues = ref({})

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

const handleEdit = (row) => {
  originalValues.value[row.id] = row.configValue
  editingId.value = row.id
}

const handleCancel = (row) => {
  row.configValue = originalValues.value[row.id]
  editingId.value = null
}

const handleSave = async (row) => {
  if (!row.configValue || isNaN(Number(row.configValue)) || Number(row.configValue) < 1) {
    ElMessage.error('请输入有效的正整数')
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

onMounted(() => {
  fetchList()
})
</script>

<style scoped lang="scss">
.sys-config-management {
  .card {
    background: #fff;
    border-radius: 8px;
    padding: 20px;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.04);
  }
}
</style>
