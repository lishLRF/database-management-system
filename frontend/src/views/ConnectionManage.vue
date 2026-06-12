<template>
  <div class="connection-manage">
    <div class="header">
      <h2>数据库连接管理</h2>
      <el-button type="primary" @click="showDialog = true">新增连接</el-button>
    </div>

    <div class="connection-grid">
      <el-card v-for="conn in connections" :key="conn.id" class="connection-card">
        <template #header>
          <div class="card-header">
            <span>{{ conn.name }}</span>
            <el-tag :type="conn.isActive ? 'success' : 'info'">
              {{ conn.isActive ? '当前连接' : '未激活' }}
            </el-tag>
          </div>
        </template>
        <div class="card-body">
          <p><strong>类型:</strong> {{ conn.dbType }}</p>
          <p v-if="conn.host"><strong>主机:</strong> {{ conn.host }}:{{ conn.port }}</p>
          <p><strong>数据库:</strong> {{ conn.database }}</p>
          <p v-if="conn.username"><strong>用户:</strong> {{ conn.username }}</p>
        </div>
        <template #footer>
          <el-button size="small" @click="handleSwitch(conn.id)">切换</el-button>
          <el-button size="small" type="danger" @click="handleDelete(conn.id)">删除</el-button>
        </template>
      </el-card>
    </div>

    <el-dialog v-model="showDialog" title="新增连接" width="600px">
      <ConnectionForm @success="handleSuccess" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useConnectionStore } from '@/stores/connection'
import ConnectionForm from '@/components/ConnectionForm.vue'
import type { Connection } from '@/types'

const store = useConnectionStore()
const connections = ref<Connection[]>([])
const showDialog = ref(false)

const loadConnections = async () => {
  await store.fetchConnections()
  connections.value = store.connections
}

const handleSwitch = async (id: number) => {
  try {
    await store.switchConnection(id)
    ElMessage.success('切换成功')
    await loadConnections()
  } catch (error) {
    ElMessage.error('切换失败')
  }
}

const handleDelete = async (id: number) => {
  try {
    await ElMessageBox.confirm('确认删除该连接？', '提示')
    await store.deleteConnection(id)
    ElMessage.success('删除成功')
    await loadConnections()
  } catch (error) {
    // 用户取消
  }
}

const handleSuccess = () => {
  showDialog.value = false
  loadConnections()
}

onMounted(() => {
  loadConnections()
})
</script>

<style scoped>
.connection-manage {
  padding: 20px;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.connection-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
}

.connection-card {
  cursor: pointer;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-body p {
  margin: 8px 0;
}
</style>
