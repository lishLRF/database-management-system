<template>
  <el-dropdown trigger="click" @command="handleCommand" placement="bottom-start" max-height="400px">
    <el-button size="small" :loading="loading">
      <el-icon><Clock /></el-icon> 历史记录
    </el-button>
    <template #dropdown>
      <el-dropdown-menu class="history-dropdown">
        <div class="history-header">
          <el-input v-model="searchKeyword" placeholder="搜索表名/类型" size="small" clearable @input="handleSearch">
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
          <el-button-group size="small" style="margin-top:8px;width:100%">
            <el-button :type="filterType === '' ? 'primary' : ''" @click="handleFilter('')">全部</el-button>
            <el-button :type="filterType === 'SELECT' ? 'primary' : ''" @click="handleFilter('SELECT')">SELECT</el-button>
            <el-button :type="filterType === 'INSERT' ? 'primary' : ''" @click="handleFilter('INSERT')">INSERT</el-button>
            <el-button :type="filterType === 'UPDATE' ? 'primary' : ''" @click="handleFilter('UPDATE')">UPDATE</el-button>
            <el-button :type="filterType === 'DELETE' ? 'primary' : ''" @click="handleFilter('DELETE')">DELETE</el-button>
          </el-button-group>
        </div>

        <div v-if="filteredHistory.length === 0" class="history-empty">
          <el-empty description="暂无历史记录" :image-size="60" />
        </div>

        <el-dropdown-item v-for="item in filteredHistory" :key="item.id" :command="item" class="history-item">
          <div class="history-row">
            <div class="history-meta">
              <el-tag :type="sqlTypeColor(item.sqlType)" size="small">{{ item.sqlType }}</el-tag>
              <el-tag v-if="item.targetTable" size="small" effect="plain">{{ item.targetTable }}</el-tag>
              <el-tag :type="item.status === 'success' ? 'success' : 'danger'" size="small" effect="plain">
                {{ item.status === 'success' ? '✓' : '✗' }}
              </el-tag>
              <span class="history-time">{{ formatTime(item.executedAt) }}</span>
            </div>
            <div class="history-sql">{{ truncateSQL(item.sqlText) }}</div>
            <div class="history-actions">
              <el-button link size="small" type="primary" @click.stop="$emit('apply', item.sqlText)">
                <el-icon><Edit /></el-icon> 填入
              </el-button>
              <el-button link size="small" type="danger" @click.stop="handleDelete(item.id)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </div>
        </el-dropdown-item>

        <div class="history-footer">
          <el-button size="small" text @click="handleClearAll">
            <el-icon><Delete /></el-icon> 清空历史
          </el-button>
        </div>
      </el-dropdown-menu>
    </template>
  </el-dropdown>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Clock, Search, Edit, Delete } from '@element-plus/icons-vue'
import { getSqlHistory, deleteSqlHistory, clearSqlHistory, type SqlHistoryItem } from '../api/sql'

const emit = defineEmits<{
  apply: [sql: string]
}>()

const loading = ref(false)
const history = ref<SqlHistoryItem[]>([])
const searchKeyword = ref('')
const filterType = ref('')

const filteredHistory = computed(() => {
  let list = history.value
  if (filterType.value) {
    list = list.filter(h => h.sqlType === filterType.value)
  }
  if (searchKeyword.value.trim()) {
    const kw = searchKeyword.value.trim().toLowerCase()
    list = list.filter(h =>
      h.sqlText.toLowerCase().includes(kw) ||
      (h.targetTable && h.targetTable.toLowerCase().includes(kw))
    )
  }
  return list
})

async function loadHistory() {
  loading.value = true
  try {
    const res = await getSqlHistory({ limit: 50 }) as any
    history.value = res.data || res || []
  } catch (e: any) {
    ElMessage.error('加载历史失败: ' + (e.response?.data?.error || e.message))
  } finally {
    loading.value = false
  }
}

function handleCommand(item: SqlHistoryItem) {
  emit('apply', item.sqlText)
}

function handleFilter(type: string) {
  filterType.value = type
}

function handleSearch() {
  // 实时搜索通过 computed 实现
}

async function handleDelete(id: number) {
  try {
    await deleteSqlHistory(id)
    history.value = history.value.filter(h => h.id !== id)
    ElMessage.success('已删除')
  } catch (e: any) {
    ElMessage.error('删除失败')
  }
}

async function handleClearAll() {
  try {
    await ElMessageBox.confirm('确定清空所有历史记录吗？（不会删除数据库记录，只是标记为不可见）', '确认', {
      type: 'warning'
    })
    await clearSqlHistory()
    history.value = []
    ElMessage.success('已清空')
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error('清空失败')
  }
}

function sqlTypeColor(type: string): '' | 'success' | 'warning' | 'danger' | 'info' {
  if (type === 'SELECT') return 'info'
  if (type === 'INSERT') return 'success'
  if (type === 'UPDATE') return 'warning'
  if (type === 'DELETE') return 'danger'
  return ''
}

function truncateSQL(sql: string): string {
  return sql.length > 80 ? sql.slice(0, 80) + '...' : sql
}

function formatTime(time: string): string {
  const d = new Date(time)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  return d.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

onMounted(() => {
  loadHistory()
})

defineExpose({ loadHistory })
</script>

<style scoped>
.history-dropdown {
  min-width: 500px;
  max-width: 600px;
}

.history-header {
  padding: 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.history-empty {
  padding: 20px;
}

.history-item {
  height: auto !important;
  padding: 0 !important;
}

.history-row {
  width: 100%;
  padding: 10px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.history-row:hover {
  background: var(--el-fill-color-light);
}

.history-meta {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}

.history-time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-left: auto;
}

.history-sql {
  font-family: 'JetBrains Mono', 'Consolas', monospace;
  font-size: 12px;
  color: var(--el-text-color-regular);
  line-height: 1.4;
  word-break: break-all;
  margin-bottom: 6px;
}

.history-actions {
  display: flex;
  gap: 8px;
}

.history-footer {
  padding: 8px 12px;
  border-top: 1px solid var(--el-border-color-lighter);
  text-align: center;
}
</style>
