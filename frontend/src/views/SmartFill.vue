<template>
  <div class="smart-fill">
    <div class="header">
      <h3>🤖 智能填表</h3>
      <span class="desc">选择目标表 + 自然语言描述 → AI生成INSERT语句</span>
    </div>

    <div class="form-top">
      <div class="field-row">
        <el-select v-model="targetTable" placeholder="选择目标表" filterable style="width:260px" @change="onTableChange">
          <el-option v-for="t in tables" :key="t.name" :label="t.name" :value="t.name">
            <span>{{ t.name }}</span>
            <span style="color:#909399;font-size:12px;margin-left:8px">{{ t.columns?.length }} 列</span>
          </el-option>
        </el-select>

        <el-input v-model="description" placeholder="描述要插入的数据，如：插入一条机床案例，名称为CNC-001，类型为加工中心..."
          style="flex:1;margin:0 12px" />
        <el-button type="primary" @click="handleGenerate" :loading="loading" :disabled="!targetTable || !description.trim()">
          生成 INSERT
        </el-button>
      </div>
    </div>

    <div class="main-area" v-if="generatedSQL">
      <div class="sql-preview">
        <div class="section-title">生成的 SQL</div>
        <code class="insert-sql">{{ generatedSQL }}</code>
        <el-button type="success" @click="handleExecuteInsert" :loading="executing" style="margin-top:8px">
          ▶ 执行 INSERT
        </el-button>
      </div>

      <div class="columns-preview" v-if="columns.length">
        <div class="section-title">字段预览 <el-tag size="small" type="warning">🔶 = AI填充</el-tag></div>
        <el-table :data="columnRows" size="small" border stripe max-height="400">
          <el-table-column prop="name" label="字段名" width="200" />
          <el-table-column prop="type" label="类型" width="140" />
          <el-table-column label="值">
            <template #default="{ row }">
              <el-input v-model="row.value" size="small" :class="{ 'ai-filled': row.aiFilled }" />
            </template>
          </el-table-column>
          <el-table-column label="来源" width="80">
            <template #default="{ row }">
              <el-tag v-if="row.aiFilled" size="small" type="warning">AI</el-tag>
              <el-tag v-else size="small" type="info">手动</el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-button @click="rebuildSQL" size="small" style="margin-top:8px">🔄 根据修改重建SQL</el-button>
      </div>
    </div>

    <div class="empty-main" v-else-if="tables.length">
      <p>选择表并描述数据，点击"生成 INSERT"</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { aiConfigAPI } from '@/api/ai-config'
import { executeSQL } from '@/api/sql'
import { useConnectionStore } from '@/stores/connection'

const store = useConnectionStore()

const tables = ref<any[]>([])
const targetTable = ref('')
const description = ref('')
const loading = ref(false)
const executing = ref(false)
const generatedSQL = ref('')

interface ColRow { name: string; type: string; value: string; aiFilled: boolean }
const columns = ref<ColRow[]>([])
const columnRows = computed(() => columns.value)

onMounted(async () => {
  if (!store.activeConnection) {
    await store.fetchConnections()
    const active = store.connections.find(c => c.isActive)
    if (active) await store.switchConnection(active.id)
  }
  if (store.schema?.tables) tables.value = store.schema.tables
})

function onTableChange() {
  generatedSQL.value = ''
  columns.value = []
}

async function handleGenerate() {
  if (!store.activeConnection) { ElMessage.warning('请先选择数据库连接'); return }

  loading.value = true
  try {
    const res = await aiConfigAPI.generateInsert(
      store.activeConnection.id, targetTable.value, description.value.trim()
    ) as any

    generatedSQL.value = res.sql || ''

    // Build editable column rows
    const tableSchema = tables.value.find(t => t.name === targetTable.value)
    const colList = res.columns || tableSchema?.columns || []
    columns.value = colList.map((col: any) => {
      const name = typeof col === 'string' ? col : col.name
      const type = typeof col === 'string' ? '' : (col.type || '')
      return { name, type, value: '', aiFilled: false }
    })

    // Parse AI-generated values from the INSERT SQL
    parseValuesFromSQL(generatedSQL.value)

    ElMessage.success('INSERT语句已生成')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '生成失败')
  } finally { loading.value = false }
}

function parseValuesFromSQL(sql: string) {
  // Extract VALUES (...) from INSERT
  const valuesMatch = sql.match(/VALUES\s*\((.+)\)/is)
  if (!valuesMatch) return
  const valuesStr = valuesMatch[1]
  const parts = splitValues(valuesStr)
  parts.forEach((val, i) => {
    if (i < columns.value.length) {
      const trimmed = val.trim()
      if (trimmed.toUpperCase() !== 'DEFAULT' && trimmed.toUpperCase() !== 'NULL') {
        columns.value[i].value = trimmed.replace(/^'|'$/g, '')
        columns.value[i].aiFilled = true
      }
    }
  })
}

function splitValues(str: string): string[] {
  const parts: string[] = []
  let current = ''
  let inQuote = false
  for (let i = 0; i < str.length; i++) {
    const ch = str[i]
    if (ch === "'" && (i === 0 || str[i - 1] !== '\\')) { inQuote = !inQuote; current += ch }
    else if (ch === ',' && !inQuote) { parts.push(current); current = '' }
    else { current += ch }
  }
  parts.push(current)
  return parts
}

function rebuildSQL() {
  const colNames = columns.value.map(c => c.name)
  const colValues = columns.value.map(c => {
    const v = c.value.trim()
    if (!v || v.toUpperCase() === 'NULL') return 'NULL'
    // If it's a number, don't quote
    if (/^\d+(\.\d+)?$/.test(v)) return v
    if (v.toUpperCase() === 'TRUE' || v.toUpperCase() === 'FALSE') return v.toUpperCase()
    return "'" + v.replace(/'/g, "''") + "'"
  })

  generatedSQL.value = `INSERT INTO "${targetTable.value}" (${colNames.map(n => `"${n}"`).join(', ')}) VALUES (${colValues.join(', ')})`
  ElMessage.success('SQL已重建')
}

async function handleExecuteInsert() {
  if (!store.activeConnection) return
  executing.value = true
  try {
    await executeSQL({ connectionId: store.activeConnection.id, sql: generatedSQL.value, confirmed: true })
    ElMessage.success('INSERT执行成功')
  } catch (e: any) {
    ElMessage.error(e.response?.data?.error || '执行失败')
  } finally { executing.value = false }
}
</script>

<style scoped>
.smart-fill {
  padding: 20px;
  height: 100%;
  overflow-y: auto;
}

.header { margin-bottom: 20px; }
.header h3 { margin: 0 0 4px; color: #303133; }
.header .desc { font-size: 13px; color: #909399; }

.form-top { margin-bottom: 20px; }
.field-row { display: flex; align-items: center; }

.main-area { display: flex; gap: 20px; flex-wrap: wrap; }

.sql-preview {
  flex: 1;
  min-width: 300px;
  background: #f5f7fa;
  padding: 16px;
  border-radius: 8px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}

.insert-sql {
  display: block;
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 13px;
  color: #409eff;
  white-space: pre-wrap;
  word-break: break-all;
  background: #ecf5ff;
  padding: 12px;
  border-radius: 6px;
}

.columns-preview {
  flex: 1;
  min-width: 350px;
}

.ai-filled :deep(.el-input__inner) {
  background: #fdf6ec !important;
  border-color: #e6a23c !important;
}

.empty-main {
  text-align: center;
  color: #909399;
  padding: 60px 0;
}
</style>
