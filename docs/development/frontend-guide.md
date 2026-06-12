# 前端开发指南

## 技术栈

- **Vue 3.4.31** (Composition API)
- **TypeScript 5.3.3**
- **Vite 5.3.3**
- **Element Plus 2.7.6** (UI组件库)
- **Monaco Editor 0.55.1** (代码编辑器)
- **Pinia 2.1.7** (状态管理)
- **Axios 1.7.2** (HTTP客户端)

## 项目结构

```
frontend/
├── src/
│   ├── api/                 # API接口
│   │   ├── auth.ts
│   │   ├── connection.ts
│   │   ├── sql.ts
│   │   └── ai.ts
│   ├── assets/              # 静态资源
│   │   ├── styles/
│   │   └── fonts/
│   ├── components/          # 公共组件
│   │   ├── SQLEditor.vue
│   │   ├── AIAssistant.vue
│   │   ├── ResultTable.vue
│   │   └── ComplexTypeViewer.vue
│   ├── composables/         # 组合式函数
│   │   ├── useMonacoEditor.ts
│   │   ├── useConnection.ts
│   │   └── useSQLExecution.ts
│   ├── layouts/             # 布局组件
│   │   └── MainLayout.vue
│   ├── router/              # 路由配置
│   │   └── index.ts
│   ├── stores/              # Pinia状态管理
│   │   ├── connection.ts
│   │   ├── sql.ts
│   │   ├── ai.ts
│   │   └── result.ts
│   ├── types/               # TypeScript类型定义
│   │   ├── api.ts
│   │   ├── connection.ts
│   │   └── sql.ts
│   ├── utils/               # 工具函数
│   │   ├── request.ts
│   │   └── format.ts
│   ├── views/               # 页面组件
│   │   ├── SQLEditor/
│   │   ├── Connections/
│   │   ├── SmartForm/
│   │   └── Settings/
│   ├── App.vue
│   └── main.ts
├── public/
├── index.html
├── vite.config.ts
├── tsconfig.json
└── package.json
```

## 设计系统

### 颜色变量

```css
/* src/assets/styles/variables.css */
:root {
  /* 背景 */
  --bg-primary: #FFFFFF;
  --bg-secondary: #F8F9FA;
  --bg-tertiary: #F1F3F5;
  --bg-elevated: #FFFFFF;
  
  /* 文本 */
  --text-primary: #1A1D24;
  --text-secondary: #6B7280;
  --text-tertiary: #9CA3AF;
  
  /* 边框 */
  --border-subtle: #E5E7EB;
  --border-default: #D1D5DB;
  --border-strong: #9CA3AF;
  
  /* 强调色 */
  --accent-primary: #0066FF;
  --accent-success: #00C853;
  --accent-warning: #FF9500;
  --accent-danger: #FF3B30;
  --accent-info: #00A8E8;
  
  /* 代码配色 */
  --code-keyword: #D73A49;
  --code-string: #032F62;
  --code-bg: #F6F8FA;
  
  /* 阴影 */
  --shadow-sm: 0 1px 2px rgba(0, 0, 0, 0.04);
  --shadow-md: 0 2px 8px rgba(0, 0, 0, 0.08);
  --shadow-lg: 0 4px 16px rgba(0, 0, 0, 0.12);
  --shadow-xl: 0 8px 32px rgba(0, 0, 0, 0.16);
  --shadow-focus: 0 0 0 3px rgba(0, 102, 255, 0.12);
}
```

### 字体配置

```css
/* src/assets/styles/fonts.css */
@font-face {
  font-family: 'IBM Plex Sans';
  src: url('../fonts/IBMPlexSans-Regular.woff2') format('woff2');
  font-weight: 400;
  font-style: normal;
}

@font-face {
  font-family: 'JetBrains Mono';
  src: url('../fonts/JetBrainsMono-Regular.woff2') format('woff2');
  font-weight: 400;
  font-style: normal;
}

body {
  font-family: 'IBM Plex Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
}

code, pre, .monaco-editor {
  font-family: 'JetBrains Mono', 'Courier New', monospace;
}
```

## 核心组件

### 1. SQLEditor 组件

```vue
<!-- src/components/SQLEditor.vue -->
<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import * as monaco from 'monaco-editor'
import { useSQLStore } from '@/stores/sql'
import { useConnectionStore } from '@/stores/connection'

const props = defineProps<{
  tabId: string
}>()

const editorRef = ref<HTMLElement>()
let editor: monaco.editor.IStandaloneCodeEditor

const sqlStore = useSQLStore()
const connectionStore = useConnectionStore()

onMounted(() => {
  if (!editorRef.value) return
  
  editor = monaco.editor.create(editorRef.value, {
    value: sqlStore.getTabContent(props.tabId),
    language: 'sql',
    theme: 'vs',
    fontSize: 14,
    fontFamily: 'JetBrains Mono, monospace',
    minimap: { enabled: false },
    scrollBeyondLastLine: false,
    automaticLayout: true,
    tabSize: 2,
    wordWrap: 'on',
  })
  
  // 注册智能提示
  registerCompletionProvider()
  
  // 快捷键：Ctrl+Enter执行
  editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => {
    executeSQL()
  })
  
  // 监听内容变化
  editor.onDidChangeModelContent(() => {
    sqlStore.updateTabContent(props.tabId, editor.getValue())
  })
})

function registerCompletionProvider() {
  monaco.languages.registerCompletionItemProvider('sql', {
    provideCompletionItems: (model, position) => {
      const word = model.getWordUntilPosition(position)
      const range = {
        startLineNumber: position.lineNumber,
        endLineNumber: position.lineNumber,
        startColumn: word.startColumn,
        endColumn: word.endColumn,
      }
      
      const suggestions: monaco.languages.CompletionItem[] = []
      
      // SQL关键字
      const keywords = ['SELECT', 'FROM', 'WHERE', 'INSERT', 'UPDATE', 'DELETE', 'JOIN']
      keywords.forEach(kw => {
        suggestions.push({
          label: kw,
          kind: monaco.languages.CompletionItemKind.Keyword,
          insertText: kw,
          range,
        })
      })
      
      // 表名和字段名
      if (connectionStore.schema) {
        connectionStore.schema.tables.forEach(table => {
          suggestions.push({
            label: table.tableName,
            kind: monaco.languages.CompletionItemKind.Class,
            insertText: table.tableName,
            range,
            detail: `Table (${table.rowCount} rows)`,
          })
        })
      }
      
      return { suggestions }
    },
  })
}

function executeSQL() {
  const selection = editor.getSelection()
  const sql = selection && !selection.isEmpty()
    ? editor.getModel()?.getValueInRange(selection) || ''
    : editor.getValue()
  
  sqlStore.executeSQL(sql)
}

onBeforeUnmount(() => {
  editor?.dispose()
})
</script>

<template>
  <div ref="editorRef" class="sql-editor"></div>
</template>

<style scoped>
.sql-editor {
  width: 100%;
  height: 100%;
  border: 1px solid var(--border-default);
  border-radius: 8px;
  overflow: hidden;
}
</style>
```

### 2. ResultTable 组件

```vue
<!-- src/components/ResultTable.vue -->
<script setup lang="ts">
import { computed } from 'vue'
import { useResultStore } from '@/stores/result'
import ComplexTypeViewer from './ComplexTypeViewer.vue'

const resultStore = useResultStore()

const tableData = computed(() => {
  if (!resultStore.currentResult) return []
  return resultStore.currentResult.rows.map(row => {
    const obj: Record<string, any> = {}
    resultStore.currentResult!.columns.forEach((col, index) => {
      obj[col.name] = row[index]
    })
    return obj
  })
})

function exportCSV() {
  resultStore.exportResult('csv')
}

function exportExcel() {
  resultStore.exportResult('excel')
}
</script>

<template>
  <div class="result-table">
    <div class="toolbar">
      <span class="info">
        {{ resultStore.currentResult?.total || 0 }} 行
        ({{ resultStore.currentResult?.executionTime || 0 }}ms)
      </span>
      <el-button @click="exportCSV" size="small">导出CSV</el-button>
      <el-button @click="exportExcel" size="small">导出Excel</el-button>
    </div>
    
    <el-table
      :data="tableData"
      stripe
      border
      height="400"
      style="width: 100%"
    >
      <el-table-column
        v-for="col in resultStore.currentResult?.columns"
        :key="col.name"
        :prop="col.name"
        :label="col.name"
        :width="150"
      >
        <template #default="{ row }">
          <ComplexTypeViewer
            v-if="resultStore.isComplexType(col.name, row[col.name])"
            :value="row[col.name]"
            :type="resultStore.detectType(row[col.name])"
          />
          <span v-else>{{ row[col.name] }}</span>
        </template>
      </el-table-column>
    </el-table>
    
    <el-pagination
      v-if="resultStore.currentResult"
      layout="total, sizes, prev, pager, next"
      :total="resultStore.currentResult.total"
      :page-sizes="[10, 50, 100, 500, 1000]"
      @size-change="resultStore.changePageSize"
      @current-change="resultStore.changePage"
    />
  </div>
</template>

<style scoped>
.result-table {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 16px;
  background: var(--bg-secondary);
  border-radius: 8px;
}

.info {
  color: var(--text-secondary);
  font-size: 14px;
}
</style>
```

## 状态管理

### Connection Store

```typescript
// src/stores/connection.ts
import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as connectionApi from '@/api/connection'
import type { Connection, Schema } from '@/types/connection'

export const useConnectionStore = defineStore('connection', () => {
  const connections = ref<Connection[]>([])
  const currentConnection = ref<Connection | null>(null)
  const schema = ref<Schema | null>(null)
  
  async function fetchConnections() {
    const res = await connectionApi.getConnections()
    connections.value = res.data.connections
  }
  
  async function switchConnection(connectionId: number) {
    const res = await connectionApi.switchConnection(connectionId)
    currentConnection.value = connections.value.find(c => c.id === connectionId) || null
    schema.value = res.data.schema
  }
  
  async function addConnection(config: any) {
    const res = await connectionApi.addConnection(config)
    await fetchConnections()
    return res.data.connectionId
  }
  
  return {
    connections,
    currentConnection,
    schema,
    fetchConnections,
    switchConnection,
    addConnection,
  }
})
```

### SQL Store

```typescript
// src/stores/sql.ts
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { nanoid } from 'nanoid'
import * as sqlApi from '@/api/sql'
import { useConnectionStore } from './connection'
import type { SQLTab } from '@/types/sql'

export const useSQLStore = defineStore('sql', () => {
  const tabs = ref<SQLTab[]>([])
  const activeTabId = ref<string | null>(null)
  
  function addTab(content = '') {
    const tab: SQLTab = {
      id: nanoid(),
      name: `query-${tabs.value.length + 1}.sql`,
      content,
      isDirty: false,
    }
    tabs.value.push(tab)
    activeTabId.value = tab.id
  }
  
  function updateTabContent(tabId: string, content: string) {
    const tab = tabs.value.find(t => t.id === tabId)
    if (tab) {
      tab.content = content
      tab.isDirty = true
    }
  }
  
  function getTabContent(tabId: string): string {
    return tabs.value.find(t => t.id === tabId)?.content || ''
  }
  
  async function executeSQL(sql: string) {
    const connectionStore = useConnectionStore()
    if (!connectionStore.currentConnection) {
      throw new Error('请先选择数据库连接')
    }
    
    const res = await sqlApi.executeSQL({
      connectionId: connectionStore.currentConnection.id,
      sqlText: sql,
      confirmed: false,
    })
    
    if (res.data.isDangerous) {
      const confirmed = await showDangerousConfirm(res.data.warningMessage)
      if (confirmed) {
        await sqlApi.executeSQL({
          connectionId: connectionStore.currentConnection.id,
          sqlText: sql,
          confirmed: true,
        })
      }
    }
  }
  
  return {
    tabs,
    activeTabId,
    addTab,
    updateTabContent,
    getTabContent,
    executeSQL,
  }
})
```

## API封装

```typescript
// src/utils/request.ts
import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

// 请求拦截器
request.interceptors.request.use(
  config => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  error => Promise.reject(error)
)

// 响应拦截器
request.interceptors.response.use(
  response => {
    const { success, data, message } = response.data
    if (!success) {
      ElMessage.error(message || '请求失败')
      return Promise.reject(new Error(message))
    }
    return response.data
  },
  error => {
    ElMessage.error(error.message || '网络错误')
    return Promise.reject(error)
  }
)

export default request
```

```typescript
// src/api/connection.ts
import request from '@/utils/request'
import type { Connection } from '@/types/connection'

export function getConnections() {
  return request.get('/connections')
}

export function addConnection(data: any) {
  return request.post('/connections', data)
}

export function switchConnection(id: number) {
  return request.post(`/connections/${id}/switch`)
}

export function testConnection(id: number) {
  return request.post(`/connections/${id}/test`)
}
```

## 编码规范

### 1. 组件命名
- 使用 PascalCase: `SQLEditor.vue`, `ResultTable.vue`
- 多单词组件名: `ComplexTypeViewer.vue`

### 2. Composition API
```typescript
// ✅ 推荐
<script setup lang="ts">
import { ref, computed } from 'vue'

const count = ref(0)
const doubled = computed(() => count.value * 2)
</script>

// ❌ 避免
<script lang="ts">
export default {
  data() {
    return { count: 0 }
  }
}
</script>
```

### 3. TypeScript类型
```typescript
// src/types/connection.ts
export interface Connection {
  id: number
  name: string
  dbType: 'postgresql' | 'sqlite'
  host?: string
  port?: number
  database: string
  isActive: boolean
}

export interface Schema {
  tables: Table[]
}

export interface Table {
  tableName: string
  rowCount: number
  columns?: Column[]
}
```

### 4. 样式规范
```vue
<style scoped>
/* 使用CSS变量 */
.container {
  background: var(--bg-primary);
  border: 1px solid var(--border-default);
  border-radius: 8px;
  padding: 16px;
}

/* 使用语义化类名 */
.editor-toolbar { }
.result-panel { }
.ai-assistant { }
</style>
```

## 性能优化

1. **路由懒加载**
```typescript
const routes = [
  {
    path: '/editor',
    component: () => import('@/views/SQLEditor/index.vue')
  }
]
```

2. **Monaco Editor懒加载**
```typescript
import { defineAsyncComponent } from 'vue'

const SQLEditor = defineAsyncComponent(
  () => import('@/components/SQLEditor.vue')
)
```

3. **虚拟滚动**
```vue
<el-table
  :data="tableData"
  height="400"
  virtual-scroll
/>
```

## 开发调试

```bash
# 启动开发服务器
npm run dev

# 构建生产版本
npm run build

# 类型检查
npm run type-check

# Lint检查
npm run lint
```
