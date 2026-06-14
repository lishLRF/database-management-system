<template>
  <div class="snippet-panel">
    <div class="snippet-header">
      <span>📑 SQL 片段</span>
      <span class="snippet-hint">保存常用SQL，一键复用</span>
    </div>

    <div class="snippet-body">
      <div class="search-bar">
        <el-input v-model="searchText" placeholder="搜索片段..." size="small" clearable @input="handleSearch">
          <template #prefix>🔍</template>
        </el-input>
      </div>

      <div class="snippet-list" v-loading="loading">
        <div v-if="loadError" class="load-error">
          ⚠️ 加载失败：{{ loadError }}
          <el-button link size="small" @click="loadSnippets">重试</el-button>
        </div>

        <div v-for="item in snippets" :key="item.id" class="snippet-item" @click="toggleExpand(item.id)">
          <div class="item-header">
            <span class="snippet-title">{{ item.title }}</span>
            <div class="item-meta">
              <el-tag v-for="tag in parseTags(item.tags)" :key="tag" size="small" type="info" class="tag">{{ tag }}</el-tag>
            </div>
          </div>
          <div v-if="item.description" class="item-desc">{{ item.description }}</div>
          <div v-if="expandedId === item.id" class="item-sql">
            <code>{{ item.sqlContent }}</code>
          </div>
          <div class="item-actions">
            <el-button link size="small" @click.stop="fillSQL(item.sqlContent)">→ 填入编辑器</el-button>
            <el-button link size="small" @click.stop="editSnippet(item)">✏️</el-button>
            <el-button link size="small" type="danger" @click.stop="deleteSnippet(item.id)">🗑</el-button>
          </div>
        </div>
        <div v-if="snippets.length === 0 && !loading && !loadError" class="empty">
          {{ searchText ? '未找到匹配的片段' : '暂无保存的片段' }}
          <div class="empty-hint">点击工具栏「💾 保存片段」保存当前SQL，或在此搜索已保存的片段 → 一键填入编辑器</div>
        </div>
      </div>
    </div>

    <!-- Edit/Create Dialog -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑片段' : '保存片段'" width="520px" :close-on-click-modal="false">
      <el-form label-position="top" @submit.prevent="saveSnippet">
        <el-form-item label="标题">
          <el-input v-model="form.title" placeholder="片段名称，如：用户订单查询" maxlength="200" />
        </el-form-item>
        <el-form-item label="SQL">
          <el-input v-model="form.sqlContent" type="textarea" :rows="6" placeholder="SQL语句" />
        </el-form-item>
        <el-form-item label="描述（可选）">
          <el-input v-model="form.description" placeholder="简短说明此SQL的用途" maxlength="500" />
        </el-form-item>
        <el-form-item label="标签（可选）">
          <el-input v-model="form.tags" placeholder="逗号分隔，如：查询,报表,用户" maxlength="500" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="saveSnippet" :loading="saving">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getSnippets, createSnippet, updateSnippet, deleteSnippet as delSnippet, type SqlSnippet } from '../api/sql'

const emit = defineEmits<{
  fillSql: [sql: string]
}>()

const loading = ref(false)
const saving = ref(false)
const loadError = ref('')
const searchText = ref('')
const snippets = ref<SqlSnippet[]>([])
const expandedId = ref<number | null>(null)

// Dialog state
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const form = ref({ title: '', sqlContent: '', description: '', tags: '' })

function parseTags(tags?: string): string[] {
  if (!tags) return []
  return tags.split(',').map(t => t.trim()).filter(Boolean)
}

function toggleExpand(id: number) {
  expandedId.value = expandedId.value === id ? null : id
}

function fillSQL(sql: string) {
  emit('fillSql', sql)
  ElMessage.success('已填入编辑器')
}

async function loadSnippets() {
  loading.value = true
  loadError.value = ''
  try {
    snippets.value = await getSnippets(searchText.value || undefined) as any
  } catch (e: any) {
    loadError.value = e.response?.data?.error || e.message || '未知错误'
  } finally { loading.value = false }
}

let searchTimer: any
function handleSearch() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(loadSnippets, 300)
}

async function deleteSnippet(id: number) {
  try {
    await ElMessageBox.confirm('确定删除此片段？', '确认', { type: 'warning' })
    await delSnippet(id)
    ElMessage.success('已删除')
    loadSnippets()
  } catch { /* cancelled */ }
}

function editSnippet(item: SqlSnippet) {
  editingId.value = item.id
  form.value = { title: item.title, sqlContent: item.sqlContent, description: item.description || '', tags: item.tags || '' }
  dialogVisible.value = true
}

function openCreateDialog(initialSql?: string) {
  editingId.value = null
  form.value = { title: '', sqlContent: initialSql || '', description: '', tags: '' }
  dialogVisible.value = true
}

async function saveSnippet() {
  if (!form.value.title.trim()) { ElMessage.warning('请输入标题'); return }
  if (!form.value.sqlContent.trim()) { ElMessage.warning('请输入SQL'); return }
  saving.value = true
  try {
    if (editingId.value) {
      await updateSnippet(editingId.value, form.value)
      ElMessage.success('已更新')
    } else {
      await createSnippet(form.value)
      ElMessage.success('已保存')
    }
    dialogVisible.value = false
    loadSnippets()
  } catch (e: any) {
    ElMessage.error(e.response?.data?.error || '保存失败')
  } finally { saving.value = false }
}

onMounted(loadSnippets)

defineExpose({ loadSnippets, openCreateDialog })
</script>

<style scoped>
.snippet-panel {
  font-size: 13px;
}
.snippet-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  border-bottom: 1px solid #ebeef5;
}
.snippet-header > span:first-child {
  font-weight: 600;
}
.snippet-hint {
  font-size: 11px;
  color: #909399;
}
.snippet-body {
  padding: 8px 12px;
}
.search-bar {
  margin-bottom: 8px;
}
.snippet-list {
  max-height: 360px;
  overflow-y: auto;
}
.load-error {
  text-align: center;
  color: #e6a23c;
  padding: 16px 0;
  font-size: 13px;
}
.snippet-item {
  padding: 8px;
  margin-bottom: 6px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  cursor: pointer;
  transition: border-color .2s;
}
.snippet-item:hover {
  border-color: #409eff;
}
.item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.snippet-title {
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.item-meta {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}
.tag {
  font-size: 10px;
}
.item-desc {
  color: #909399;
  font-size: 12px;
  margin-top: 4px;
}
.item-sql {
  margin-top: 6px;
  padding: 8px;
  background: #f5f7fa;
  border-radius: 4px;
  font-size: 12px;
  overflow-x: auto;
  max-height: 150px;
  overflow-y: auto;
}
.item-sql code {
  white-space: pre;
  font-family: 'Fira Code', 'Cascadia Code', monospace;
}
.item-actions {
  margin-top: 6px;
  display: flex;
  gap: 4px;
  opacity: 0;
  transition: opacity .2s;
}
.snippet-item:hover .item-actions {
  opacity: 1;
}
.empty {
  text-align: center;
  color: #909399;
  padding: 24px 0;
  font-size: 13px;
}
.empty-hint {
  margin-top: 8px;
  font-size: 11px;
  color: #c0c4cc;
  line-height: 1.5;
}
</style>
