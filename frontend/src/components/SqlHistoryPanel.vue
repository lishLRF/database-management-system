<template>
  <div class="history-panel">
    <div class="history-header">
      <span>📜 历史记录</span>
      <el-button link @click="collapsed = !collapsed" size="small">
        {{ collapsed ? '▼' : '▲' }}
      </el-button>
    </div>

    <div v-if="!collapsed" class="history-body">
      <!-- Search & Filter -->
      <div class="search-bar">
        <el-input
          v-model="searchText"
          placeholder="搜索 SQL..."
          size="small"
          clearable
          @input="handleSearch"
        >
          <template #prefix>🔍</template>
        </el-input>
      </div>

      <div class="filter-bar">
        <el-select v-model="filterType" placeholder="类型" size="small" clearable @change="loadHistory" :teleported="false" popper-class="history-inner-select">
          <el-option label="查询" value="SELECT" />
          <el-option label="插入" value="INSERT" />
          <el-option label="更新" value="UPDATE" />
          <el-option label="删除" value="DELETE" />
        </el-select>
        <el-select v-model="filterTable" placeholder="表" size="small" clearable @change="loadHistory" :teleported="false" popper-class="history-inner-select">
          <el-option v-for="t in tables" :key="t" :label="t" :value="t" />
        </el-select>
      </div>

      <!-- History List -->
      <div class="history-list" v-loading="loading">
        <div
          v-for="item in displayHistory"
          :key="item.id"
          class="history-item"
          :class="{'success': item.status === 'success', 'error': item.status === 'error'}"
        >
          <div class="item-header">
            <el-tag :type="typeColor(item.sqlType)" size="small">{{ item.sqlType || 'SQL' }}</el-tag>
            <span v-if="item.targetTable" class="table-tag">{{ item.targetTable }}</span>
            <span class="time">{{ formatTime(item.executedAt) }}</span>
          </div>

          <div class="sql-preview" @click="toggleExpand(item.id)">
            {{ item.expanded ? item.sqlText : truncate(item.sqlText, 80) }}
          </div>

          <div class="item-actions">
            <el-button link size="small" @click="fillSQL(item.sqlText)">→ 回填</el-button>
            <el-button link size="small" type="danger" @click="deleteItem(item.id)">删除</el-button>
          </div>
        </div>

        <div v-if="displayHistory.length === 0" class="empty">
          {{ searchText ? '未找到匹配的历史记录' : '暂无历史记录' }}
        </div>
      </div>

      <!-- Pagination -->
      <div class="pagination" v-if="total > pageSize">
        <el-pagination
          small
          layout="prev, pager, next"
          :total="total"
          :page-size="pageSize"
          :current-page="currentPage"
          @current-change="handlePageChange"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { sqlHistoryAPI } from '@/api/sql-history'

interface HistoryItem {
  id: number
  sqlText: string
  sqlType: string
  targetTable: string
  status: 'success' | 'error'
  executionTime: number
  executedAt: string
  expanded?: boolean
}

const emit = defineEmits<{
  fillSql: [sql: string]
}>()

const collapsed = ref(false)
const loading = ref(false)
const searchText = ref('')
const filterType = ref('')
const filterTable = ref('')
const history = ref<HistoryItem[]>([])
const tables = ref<string[]>([])
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)

const displayHistory = computed(() => history.value)

async function loadHistory() {
  loading.value = true
  try {
    const params: any = { page: currentPage.value, pageSize: pageSize.value }
    if (searchText.value) params.search = searchText.value
    if (filterType.value) params.sqlType = filterType.value
    if (filterTable.value) params.targetTable = filterTable.value

    const res = await sqlHistoryAPI.getHistory(params) as any
    history.value = (res.history || []).map((item: any) => ({
      ...item,
      expanded: false
    }))
    total.value = res.total || 0

    // Extract unique tables
    const tableset = new Set<string>()
    history.value.forEach(h => { if (h.targetTable) tableset.add(h.targetTable) })
    tables.value = Array.from(tableset).sort()
  } catch (err: any) {
    ElMessage.error('加载历史记录失败: ' + (err.response?.data?.message || err.message))
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  currentPage.value = 1
  loadHistory()
}

function handlePageChange(page: number) {
  currentPage.value = page
  loadHistory()
}

function toggleExpand(id: number) {
  const item = history.value.find(h => h.id === id)
  if (item) item.expanded = !item.expanded
}

function fillSQL(sql: string) {
  emit('fillSql', sql)
  ElMessage.success('已回填到编辑器')
}

async function deleteItem(id: number) {
  try {
    await ElMessageBox.confirm('确认删除此条历史记录？', '提示', { type: 'warning' })
    await sqlHistoryAPI.deleteHistory(id)
    ElMessage.success('已删除')
    loadHistory()
  } catch {}
}

function typeColor(type: string): 'success' | 'warning' | 'danger' | 'info' {
  if (!type) return 'info'
  const t = type.toUpperCase()
  if (t === 'SELECT') return 'info'
  if (t === 'INSERT') return 'success'
  if (t === 'UPDATE') return 'warning'
  if (t === 'DELETE') return 'danger'
  return 'info'
}

function formatTime(isoStr: string): string {
  const d = new Date(isoStr)
  const now = new Date()
  const diff = now.getTime() - d.getTime()

  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'

  return d.toLocaleString('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

function truncate(text: string, len: number): string {
  if (text.length <= len) return text
  return text.substring(0, len) + '...'
}

onMounted(() => {
  loadHistory()
})

defineExpose({ loadHistory })
</script>

<style scoped>
.history-panel {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  overflow: hidden;
  background: #fff;
  margin-bottom: 8px;
}

.history-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  cursor: pointer;
  user-select: none;
}

.history-body {
  max-height: 400px;
  display: flex;
  flex-direction: column;
}

.search-bar {
  padding: 8px 12px;
  border-bottom: 1px solid #f0f2f5;
}

.filter-bar {
  display: flex;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid #f0f2f5;
}

.history-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 12px;
}

.history-item {
  padding: 8px;
  margin-bottom: 8px;
  border-radius: 4px;
  border-left: 3px solid #c0c4cc;
  background: #fafbfc;
  transition: all 0.2s;
}

.history-item:hover {
  background: #f5f7fa;
  box-shadow: 0 2px 4px rgba(0,0,0,0.06);
}

.history-item.success {
  border-left-color: #67c23a;
}

.history-item.error {
  border-left-color: #f56c6c;
}

.item-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
  font-size: 11px;
}

.table-tag {
  padding: 2px 6px;
  background: #e4e7ed;
  border-radius: 3px;
  font-size: 11px;
  color: #606266;
}

.time {
  margin-left: auto;
  font-size: 11px;
  color: #c0c4cc;
}

.sql-preview {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12px;
  color: #409eff;
  background: #f0f5ff;
  padding: 6px 8px;
  border-radius: 4px;
  white-space: pre-wrap;
  word-break: break-all;
  cursor: pointer;
  margin-bottom: 6px;
}

.item-actions {
  display: flex;
  gap: 8px;
}

.empty {
  text-align: center;
  padding: 32px 16px;
  color: #c0c4cc;
  font-size: 13px;
}

.pagination {
  padding: 8px 12px;
  border-top: 1px solid #f0f2f5;
  display: flex;
  justify-content: center;
}
</style>
