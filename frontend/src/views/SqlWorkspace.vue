<template>
  <div class="workspace">
    <!-- LEFT PANEL: Toolbar + Editor/Explain + Results -->
    <div class="left-panel">
      <div class="toolbar">
        <el-button type="primary" @click="handleExecute" :loading="executing" size="small">
          <el-icon><CaretRight /></el-icon> 执行
        </el-button>
        <el-button @click="handleClear" size="small">清空</el-button>
        <el-button type="warning" @click="handleFixCurrentSQL" :loading="fixing" :disabled="!sqlContent.trim()" size="small">
          🤖 AI修复
        </el-button>
        <el-divider direction="vertical" />
        <el-button
          :type="explainVisible ? 'primary' : 'default'"
          @click="handleExplainCode"
          :loading="explainLoading"
          size="small"
        >
          💡 解释代码
        </el-button>
        <el-checkbox v-model="enablePagination" size="small" style="margin-left:8px">分页</el-checkbox>
      </div>

      <div class="editor-row">
        <div class="editor-area">
          <SqlEditor ref="editorRef" v-model="sqlContent" :schema="schema" @execute="handleExecute" />
        </div>

        <!-- EXPLAIN PANEL -->
        <transition name="slide">
          <div v-if="explainVisible" class="explain-panel">
            <div class="explain-header">
              <span>💡 代码解释</span>
              <el-button link @click="explainVisible = false" size="small">✕</el-button>
            </div>
            <div class="explain-body" v-loading="explainLoading">
              <div v-if="outputEffect" class="explain-section">
                <div class="explain-label">📊 预测输出效果</div>
                <div class="explain-text">{{ outputEffect }}</div>
              </div>
              <div v-if="codeStructure" class="explain-section">
                <div class="explain-label">🔧 代码结构与函数</div>
                <div class="explain-text">{{ codeStructure }}</div>
              </div>
              <div v-if="!outputEffect && !codeStructure && !explainLoading" class="explain-empty">
                选中代码或直接点击 💡 解释代码 按钮获取AI解释
              </div>
            </div>
            <div class="explain-actions" v-if="outputEffect">
              <el-button link size="small" @click="addChatMsg('ai', '💡 代码解释\n\n📊 预测输出效果\n' + outputEffect + '\n\n🔧 代码结构与函数\n' + codeStructure)">→ 发送到对话</el-button>
            </div>
          </div>
        </transition>
      </div>

      <div class="result-area">
        <ResultTable :result="queryResult" :show-pagination="enablePagination" @page-change="handlePageChange" />
      </div>
    </div>

    <!-- RIGHT PANEL: 4-Party Chat -->
    <div class="right-panel">
      <div class="chat-header">
        <el-select v-model="currentConvId" placeholder="对话" style="flex:1" clearable filterable
          @change="onConvSelect" @clear="newConversation">
          <el-option v-for="c in conversations" :key="c.conversationId" :label="c.title" :value="c.conversationId" />
        </el-select>
        <el-button @click="newConversation" size="small" type="primary" style="margin-left:6px">+</el-button>
        <el-button v-if="currentConvId" @click="handleDeleteConv(currentConvId)" size="small" type="danger"
          :icon="Delete" circle style="margin-left:4px" />
      </div>

      <div class="chat-messages" ref="chatRef">
        <div v-for="msg in chatMessages" :key="msg.id" :class="['msg', msg.type]">
          <div class="msg-head">
            <span class="msg-icon">{{ msgIcon(msg.type) }}</span>
            <span class="msg-role">{{ msgRole(msg.type) }}</span>
            <span class="msg-time">{{ msg.time }}</span>
          </div>
          <div class="msg-body">
            <code v-if="msg.type === 'ai'" class="sql-code">{{ msg.content }}</code>
            <span v-else>{{ msg.content }}</span>
          </div>
          <div class="msg-actions">
            <el-button v-if="msg.type === 'ai'" link size="small" @click="applySQL(msg.content)">
              → 填入编辑器
            </el-button>
            <el-button v-if="msg.type === 'ai'" link size="small" @click="executeSQL(msg.content)">
              ▶ 直接执行
            </el-button>
            <el-button v-if="msg.type === 'db-error'" link size="small" type="warning"
              @click="handleFixSQL(msg.content)">
              🤖 AI修复
            </el-button>
          </div>
        </div>
      </div>

      <div class="chat-input">
        <el-input v-model="chatInput" placeholder="描述你想查什么..." @keyup.enter="sendChat"
          :disabled="chatBusy" size="small" />
        <el-button type="primary" @click="sendChat" :loading="chatBusy" size="small" style="margin-left:6px">
          发送
        </el-button>
      </div>
    </div>

    <!-- Danger confirmation dialog -->
    <el-dialog v-model="showWarning" title="⚠️ 危险操作" width="420px">
      <p style="color:#e6a23c;margin-bottom:12px">{{ warningMessage }}</p>
      <template #footer>
        <el-button @click="showWarning = false">取消</el-button>
        <el-button type="danger" @click="confirmExecute">确认执行</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CaretRight, Delete } from '@element-plus/icons-vue'
import SqlEditor from '../components/SqlEditor.vue'
import ResultTable from '../components/ResultTable.vue'
import { executeSQL as execSQL } from '../api/sql'
import { aiConfigAPI } from '../api/ai-config'
import { conversationAPI } from '../api/ai-conversation'
import { useConnectionStore } from '../stores/connection'
import type { ConversationInfo } from '../api/ai-conversation'

const store = useConnectionStore()

// ── Editor ref ──
const editorRef = ref<InstanceType<typeof SqlEditor>>()

// ── Editor state ──
const sqlContent = ref('')
const queryResult = ref<any>(null)
const schema = ref<any>(null)
const executing = ref(false)
const fixing = ref(false)
const enablePagination = ref(true)
const currentPage = ref(1)
const currentPageSize = ref(100)
const showWarning = ref(false)
const warningMessage = ref('')

// ── Explain state ──
const explainVisible = ref(false)
const explainLoading = ref(false)
const outputEffect = ref('')
const codeStructure = ref('')

// ── Chat state ──
interface ChatMsg { id: number; type: 'user' | 'ai' | 'db-status' | 'db-error'; content: string; time: string }
const chatMessages = ref<ChatMsg[]>([])
const chatInput = ref('')
const chatBusy = ref(false)
const chatRef = ref<HTMLDivElement>()
const conversations = ref<ConversationInfo[]>([])
const currentConvId = ref<string | null>(null)

const now = () => new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
const msgIcon = (t: string) => ({ user: '👤', ai: '🤖', 'db-status': '🗄️', 'db-error': '⚠️' }[t] || '')
const msgRole = (t: string) => ({ user: '你', ai: 'AI', 'db-status': '数据库', 'db-error': '报错' }[t] || '')

function addChatMsg(type: ChatMsg['type'], content: string) {
  chatMessages.value.push({ id: Date.now(), type, content, time: now() })
  nextTick(() => { if (chatRef.value) chatRef.value.scrollTop = chatRef.value.scrollHeight })
}

// ── Conversation persistence ──
async function loadConversations() {
  try { const r = await conversationAPI.listConversations() as any; conversations.value = r.conversations || [] } catch {}
}

async function restoreChatHistory(convId: string) {
  try {
    const r = await conversationAPI.getMessages(convId) as any
    chatMessages.value = (r.messages || []).map((m: any) => ({
      id: m.id, time: new Date(m.createdAt).toLocaleTimeString('zh-CN', { hour:'2-digit', minute:'2-digit', second:'2-digit' }),
      type: m.messageType === 'ai' ? 'ai' : 'user' as ChatMsg['type'],
      content: m.messageContent
    }))
  } catch { chatMessages.value = [] }
}

function onConvSelect(id: string) { if (id) restoreChatHistory(id) }
function newConversation() {
  const id = 'conv_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8)
  currentConvId.value = id; chatMessages.value = []
  conversations.value.unshift({ conversationId: id, title: '新对话', messageCount: 0, createdAt: new Date().toISOString() })
}

async function handleDeleteConv(id: string) {
  try {
    await ElMessageBox.confirm('删除该对话？', '提示')
    await conversationAPI.deleteConversation(id)
    if (currentConvId.value === id) { currentConvId.value = null; chatMessages.value = [] }
    conversations.value = conversations.value.filter(c => c.conversationId !== id)
  } catch {}
}

async function saveChatMessage(type: string, content: string) {
  if (!currentConvId.value || !store.activeConnection) return
  try {
    await conversationAPI.chat(currentConvId.value, store.activeConnection.id,
      type === 'ai' ? '[AI]:' + content : type === 'db-status' ? '[DB]:' + content : type === 'db-error' ? '[ERR]:' + content : content)
  } catch {}
  const conv = conversations.value.find(c => c.conversationId === currentConvId.value)
  if (conv) { conv.messageCount = chatMessages.value.length; if (type === 'user') conv.title = content.slice(0, 30) }
}

// ── SQL Execution ──
async function handleExecute() {
  if (!store.activeConnection) { ElMessage.warning('请先选择数据库连接'); return }
  if (!sqlContent.value.trim()) { ElMessage.warning('请输入SQL语句'); return }

  executing.value = true
  try {
    const params: any = { connectionId: store.activeConnection.id, sql: sqlContent.value.trim(), confirmed: false }
    if (enablePagination.value) { params.page = currentPage.value; params.pageSize = currentPageSize.value }

    const result = await execSQL(params)

    if (result.isDangerous) {
      warningMessage.value = result.warningMessage; showWarning.value = true; executing.value = false; return
    }

    queryResult.value = result
    const info = result.columns
      ? `✅ 查询成功 · ${result.total ?? result.rows?.length} 行 · ${result.executionTime}ms`
      : `✅ 执行成功 · 影响 ${result.affectedRows ?? 0} 行 · ${result.executionTime}ms`
    addChatMsg('db-status', info)
    saveChatMessage('db-status', info)
  } catch (error: any) {
    const errMsg = error.response?.data?.error || error.message || '执行失败'
    addChatMsg('db-error', errMsg)
    saveChatMessage('db-error', errMsg)
  } finally { executing.value = false }
}

async function confirmExecute() {
  showWarning.value = false; executing.value = true
  try {
    const params: any = { connectionId: store.activeConnection!.id, sql: sqlContent.value.trim(), confirmed: true }
    if (enablePagination.value) { params.page = currentPage.value; params.pageSize = currentPageSize.value }
    const result = await execSQL(params)
    queryResult.value = result
    addChatMsg('db-status', `✅ 危险操作已执行 · ${result.affectedRows ?? 0} 行影响 · ${result.executionTime}ms`)
  } catch (error: any) {
    const errMsg = error.response?.data?.error || error.message || '执行失败'
    addChatMsg('db-error', errMsg)
  } finally { executing.value = false }
}

function handlePageChange(page: number, pageSize: number) { currentPage.value = page; currentPageSize.value = pageSize; handleExecute() }
function handleClear() { sqlContent.value = ''; queryResult.value = null }

// ── AI Explain ──
async function handleExplainCode() {
  if (!store.activeConnection) { ElMessage.warning('请先选择数据库连接'); return }
  if (!sqlContent.value.trim()) { ElMessage.warning('请输入SQL语句'); return }

  // Get selected text or fall back to all code
  const selected = editorRef.value?.getSelectedText()
  const code = (selected && selected.trim()) ? selected.trim() : sqlContent.value.trim()

  explainVisible.value = true
  explainLoading.value = true
  outputEffect.value = ''
  codeStructure.value = ''

  try {
    const res = await aiConfigAPI.explainSQL(store.activeConnection.id, code) as any
    outputEffect.value = res.outputEffect || ''
    codeStructure.value = res.codeStructure || ''
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '解释失败')
  } finally {
    explainLoading.value = false
  }
}

// ── AI Chat ──
async function sendChat() {
  if (!chatInput.value.trim()) return
  if (!store.activeConnection) { ElMessage.warning('请先选择数据库连接'); return }
  if (!currentConvId.value) newConversation()

  const msg = chatInput.value.trim(); chatInput.value = ''; chatBusy.value = true
  addChatMsg('user', msg); saveChatMessage('user', msg)

  try {
    const res = await aiConfigAPI.generateSQL(store.activeConnection.id, msg) as any
    if (res.sql) {
      addChatMsg('ai', res.sql); saveChatMessage('ai', res.sql)
    }
  } catch (e: any) {
    addChatMsg('db-error', e.response?.data?.message || 'AI生成失败')
  } finally { chatBusy.value = false }
}

function applySQL(sql: string) { sqlContent.value = sql; ElMessage.success('已填入编辑器') }
async function executeSQL(sql: string) { sqlContent.value = sql; await nextTick(); handleExecute() }

// ── AI Fix ──
async function handleFixCurrentSQL() {
  if (!store.activeConnection) { ElMessage.warning('请先选择数据库连接'); return }
  if (!sqlContent.value.trim()) { ElMessage.warning('请先输入SQL'); return }
  if (!currentConvId.value) newConversation()

  fixing.value = true
  addChatMsg('user', '🤖 请检查并修复以下SQL:\n' + sqlContent.value.trim())
  try {
    const res = await aiConfigAPI.fixSQL(store.activeConnection.id, sqlContent.value.trim(),
      '请检查此SQL语句的正确性，修复语法问题和字段名错误') as any
    if (res.sql) { addChatMsg('ai', res.sql); saveChatMessage('ai', res.sql) }
  } catch (e: any) { addChatMsg('db-error', e.response?.data?.message || 'AI修复失败') }
  finally { fixing.value = false }
}

async function handleFixSQL(errMsg: string) {
  if (!store.activeConnection) return
  if (!currentConvId.value) newConversation()

  fixing.value = true
  addChatMsg('user', '🤖 请修复报错SQL:\n' + (sqlContent.value.trim() || 'SELECT') + '\n\n报错: ' + errMsg)
  try {
    const res = await aiConfigAPI.fixSQL(store.activeConnection.id, sqlContent.value.trim() || 'SELECT', errMsg) as any
    if (res.sql) { addChatMsg('ai', res.sql); saveChatMessage('ai', res.sql) }
  } catch (e: any) { addChatMsg('db-error', e.response?.data?.message || 'AI修复失败') }
  finally { fixing.value = false }
}

// ── Init ──
onMounted(async () => {
  if (!store.activeConnection) {
    await store.fetchConnections()
    const active = store.connections.find(c => c.isActive)
    if (active) await store.switchConnection(active.id)
  }
  if (store.activeConnection) schema.value = store.schema
  loadConversations()
  const pending = store.takePendingSql()
  if (pending) { sqlContent.value = pending; ElMessage.success('已加载AI生成的SQL') }
})
</script>

<style scoped>
.workspace {
  display: flex;
  height: 100%;
  gap: 0;
}

/* ── Left Panel ── */
.left-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 12px;
  gap: 8px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.editor-row {
  flex: 1;
  display: flex;
  gap: 0;
  min-height: 250px;
}

.editor-area {
  flex: 1;
  min-width: 0;
}

/* ── Explain Panel ── */
.explain-panel {
  width: 280px;
  min-width: 220px;
  display: flex;
  flex-direction: column;
  border-left: 1px solid #e4e7ed;
  margin-left: 8px;
  background: #fafbfc;
  border-radius: 0 4px 4px 0;
}

.explain-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 600;
  color: #303133;
  border-bottom: 1px solid #e4e7ed;
  background: #fff;
}

.explain-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.explain-section {
  margin-bottom: 14px;
}

.explain-label {
  font-size: 12px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 6px;
}

.explain-text {
  font-size: 13px;
  line-height: 1.7;
  color: #303133;
  white-space: pre-wrap;
  word-break: break-word;
}

.explain-empty {
  font-size: 13px;
  color: #c0c4cc;
  text-align: center;
  padding: 24px 0;
}

.explain-actions {
  padding: 8px 12px;
  border-top: 1px solid #e4e7ed;
  background: #fff;
}

/* Slide transition */
.slide-enter-active, .slide-leave-active {
  transition: width 0.25s ease, opacity 0.25s ease;
}
.slide-enter-from, .slide-leave-to {
  width: 0 !important;
  opacity: 0;
  overflow: hidden;
}

/* ── Result area ── */
.result-area {
  flex: 1;
  overflow: auto;
  min-height: 150px;
}

/* ── Right Panel ── */
.right-panel {
  width: 380px;
  min-width: 320px;
  display: flex;
  flex-direction: column;
  border-left: 1px solid #e4e7ed;
  background: #fafbfc;
}

.chat-header {
  display: flex;
  align-items: center;
  padding: 10px 12px;
  border-bottom: 1px solid #e4e7ed;
  background: #fff;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

/* ── Message Blocks ── */
.msg {
  margin-bottom: 10px;
  padding: 10px 12px;
  border-radius: 8px;
  border-left: 3px solid #c0c4cc;
  background: #fff;
}
.msg.user  { border-left-color: #409eff; background: #ecf5ff; }
.msg.ai    { border-left-color: #67c23a; background: #f0f9eb; }
.msg.db-status { border-left-color: #909399; background: #f5f7fa; }
.msg.db-error  { border-left-color: #f56c6c; background: #fef0f0; }

.msg-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}
.msg-icon { font-size: 14px; }
.msg-role { font-size: 12px; font-weight: 600; color: #606266; }
.msg-time { font-size: 11px; color: #c0c4cc; margin-left: auto; }

.msg-body { font-size: 13px; line-height: 1.5; color: #303133; word-break: break-word; }
.sql-code {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12px;
  color: #409eff;
  white-space: pre-wrap;
  background: #f0f5ff;
  padding: 6px 8px;
  border-radius: 4px;
  display: block;
}

.msg-actions {
  margin-top: 6px;
  display: flex;
  gap: 8px;
}

/* ── Chat Input ── */
.chat-input {
  display: flex;
  padding: 10px 12px;
  border-top: 1px solid #e4e7ed;
  background: #fff;
}
</style>
