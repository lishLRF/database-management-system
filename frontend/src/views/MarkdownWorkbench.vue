<template>
  <div class="md-workbench">
    <!-- Header -->
    <div class="md-header">
      <h2>📄 Markdown 智能解析</h2>
      <el-button size="small" @click="loadDocumentList">🔄 刷新</el-button>
    </div>

    <!-- Upload + Table Selection Bar -->
    <div class="md-toolbar">
      <el-select v-model="currentDocId" placeholder="选择文档..." filterable clearable @change="onDocSelect" style="flex:1;max-width:320px" size="small">
        <el-option v-for="d in documents" :key="d.id" :label="d.fileName + ' ['+statusLabel(d.status)+']'" :value="d.id" />
      </el-select>
      <input ref="fileInputRef" type="file" accept=".md,.markdown" @change="handleUploadFile" hidden />
      <el-button size="small" type="primary" @click="fileInputRef?.click()" :loading="uploading">📤 上传</el-button>
      <el-select v-model="connectedTable" placeholder="参考表(可选)" clearable filterable size="small" style="width:160px;margin-left:8px">
        <el-option v-for="t in tables" :key="t.name" :label="t.name" :value="t.name" />
      </el-select>
      <div class="action-buttons">
        <el-button v-if="currentDoc" type="primary" size="small" @click="doSummarize" :loading="summarizing">1️⃣ 摘要</el-button>
        <el-button v-if="currentDoc" type="warning" size="small" @click="doChunk" :loading="chunking">2️⃣ 分块</el-button>
        <el-button v-if="strategy.length > 0 && !strategyConfirmed" type="success" size="small" @click="doConfirm" :loading="confirming">✅ 确认存库</el-button>
        <!-- Two separate download buttons after confirm -->
        <el-button v-if="strategyConfirmed && exportResult" size="small" type="primary" @click="downloadStructured">
          📥 结构化
        </el-button>
        <el-button v-if="strategyConfirmed && exportResult" size="small" type="warning" @click="downloadSemantic">
          📥 语义向量
        </el-button>
        <el-button v-if="currentDoc" size="small" type="danger" @click="deleteCurrentDoc">🗑</el-button>
      </div>
    </div>

    <!-- Agent Progress Panel (visible during AI operations) -->
    <div v-if="agentRunning" class="agent-progress-panel">
      <div class="agent-progress-header">
        <el-icon class="is-loading"><span>⏳</span></el-icon>
        <span class="agent-phase">{{ progressMsg }}</span>
        <span class="agent-elapsed">⏱ {{ formatElapsed(elapsedSeconds) }}</span>
      </div>
      <div v-if="progressSteps.length > 0" class="agent-steps">
        <div v-for="(step, i) in progressSteps" :key="i" class="agent-step-row">
          <span class="step-dot" :class="step.type">{{ step.type === 'round' ? '🔵' : step.type === 'result' ? '🟢' : '⏺' }}</span>
          <span class="step-text">{{ step.msg }}</span>
          <span v-if="step.elapsed" class="step-time">{{ formatElapsed(step.elapsed) }}</span>
        </div>
      </div>
    </div>

    <!-- Error alert -->
    <div v-if="progressErr && progressMsg" class="md-progress">
      <el-alert :title="progressMsg" type="error" :closable="true" show-icon @close="progressErr=false;progressMsg=''" />
    </div>

    <!-- Three-column body -->
    <div class="md-body">
      <!-- LEFT: 原文 -->
      <div class="md-col left-col">
        <div class="col-title">📜 Markdown 原文</div>
        <div v-if="!rawContent" class="col-empty">请上传并选择文档查看原文</div>
        <div v-else class="raw-content" v-text="rawContent"></div>
      </div>

      <!-- CENTER: 摘要 + 分块策略 + 人机交互 -->
      <div class="md-col center-col">
        <!-- 摘要 -->
        <div class="col-title">📝 AI 摘要</div>
        <div v-if="!summary" class="col-empty">点击 1️⃣ 生成摘要</div>
        <div v-else class="summary-box">{{ summary }}</div>

        <!-- 分块策略 -->
        <div class="col-title" style="margin-top:12px">🧠 分块策略
          <el-tag v-if="strategy.length" size="small" style="margin-left:8px">{{ strategy.length }} 块</el-tag>
          <el-tag v-if="strategyConfirmed" type="success" size="small" style="margin-left:4px">已确认</el-tag>
        </div>
        <div v-if="!strategy.length" class="col-empty">点击 2️⃣ 分块，AI 给出策略建议</div>
        <div v-else class="strategy-box">
          <div v-for="(s, i) in strategy" :key="i" class="strategy-row">
            <el-tag :type="s.type === 'structured_data' ? 'success' : 'warning'" size="small" style="margin-right:6px">
              {{ s.type === 'structured_data' ? '结构化' : '语义' }}
            </el-tag>
            <span class="strategy-title">{{ s.title }}</span>
            <span class="strategy-pos">[{{ s.start }}-{{ s.end }}]</span>
          </div>
        </div>

        <!-- 人工反馈交互框 -->
        <div v-if="strategy.length && !strategyConfirmed" class="feedback-area">
          <el-divider style="margin:8px 0" />
          <div class="feedback-title">✋ 对分块策略有意见？</div>
          <el-input v-model="feedbackText" placeholder="例如：第3块太大需要拆开，前两块应该合并..." type="textarea" :rows="2" size="small" />
          <div class="feedback-btns">
            <el-button size="small" type="primary" @click="doFeedback" :loading="feedbacking" :disabled="!feedbackText.trim()">发送反馈 → AI 调整</el-button>
          </div>
        </div>
      </div>

      <!-- RIGHT: 结构化分块 + 语义分块 预览（代码切分） -->
      <div class="md-col right-col">
        <div class="col-title">📊 结构化数据分块
          <el-tag v-if="sliced.structuredCount" type="success" size="small" style="margin-left:6px">{{ sliced.structuredCount }} 块</el-tag>
        </div>
        <div v-if="!sliced.structuredFile" class="col-empty">分块策略完成后自动生成</div>
        <div v-else class="export-preview">
          <pre class="preview-text">{{ sliced.structuredFile }}</pre>
        </div>

        <div class="col-title" style="margin-top:8px">🧠 语义向量化分块
          <el-tag v-if="sliced.semanticCount" type="warning" size="small" style="margin-left:6px">{{ sliced.semanticCount }} 块</el-tag>
        </div>
        <div v-if="!sliced.semanticFile" class="col-empty">分块策略完成后自动生成</div>
        <div v-else class="export-preview">
          <pre class="preview-text">{{ sliced.semanticFile }}</pre>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { markdownAPI, type MdDocumentInfo } from '@/api/markdown'
import { useConnectionStore } from '@/stores/connection'

const store = useConnectionStore()
const fileInputRef = ref<HTMLInputElement>()
const documents = ref<MdDocumentInfo[]>([])
const currentDocId = ref<number | null>(null)
const currentDoc = ref<MdDocumentInfo | null>(null)
const connectedTable = ref('')
const tables = ref<any[]>([])
const rawContent = ref('')

const uploading = ref(false)
const summarizing = ref(false)
const chunking = ref(false)
const confirming = ref(false)
const exporting = ref(false)
const feedbacking = ref(false)
const summary = ref('')
const strategy = ref<any[]>([])
const strategyConfirmed = ref(false)
const feedbackText = ref('')
const progressMsg = ref('')
const progressErr = ref(false)
const sliced = ref({ structuredFile: '', semanticFile: '', structuredCount: 0, semanticCount: 0 })

// Agent progress tracking
const agentRunning = ref(false)
const elapsedSeconds = ref(0)
const progressSteps = ref<Array<{ type: string; msg: string; elapsed: number }>>([])
let elapsedTimer: ReturnType<typeof setInterval> | null = null
let stepStartTime = 0

// Export result (stored after export, used by two separate download buttons)
const exportResult = ref<{ structuredFile: string; semanticFile: string; structuredFileName: string; semanticFileName: string } | null>(null)

function startProgressTracker() {
  agentRunning.value = true
  elapsedSeconds.value = 0
  progressSteps.value = []
  stepStartTime = Date.now()
  elapsedTimer = setInterval(() => { elapsedSeconds.value++ }, 1000)
}

function addProgressStep(type: string, msg: string) {
  const now = Date.now()
  progressSteps.value.push({ type, msg, elapsed: Math.round((now - stepStartTime) / 1000) })
}

function stopProgressTracker() {
  agentRunning.value = false
  if (elapsedTimer) { clearInterval(elapsedTimer); elapsedTimer = null }
}

function formatElapsed(s: number): string {
  if (s < 60) return `${s}s`
  const m = Math.floor(s / 60)
  const sec = s % 60
  return `${m}m${sec}s`
}

function statusLabel(s: string): string {
  const m: Record<string,string> = { uploaded:'已上传', summarized:'已摘要', chunked:'已分块', classified:'已分类', exported:'已导出' }
  return m[s] || s
}

async function loadDocumentList() {
  try { const r = await markdownAPI.getDocuments(); documents.value = r.documents || [] } catch {}
}

async function handleUploadFile() {
  const f = fileInputRef.value?.files?.[0]; if (!f) return
  uploading.value = true
  try {
    const r = await markdownAPI.upload(f)
    documents.value.unshift(r)
    rawContent.value = await f.text()
    currentDocId.value = r.id
    ElMessage.success('上传成功')
  } catch (e: any) { ElMessage.error('上传失败: ' + (e.response?.data?.error || e.message)) }
  finally { uploading.value = false }
}

async function onDocSelect(id: number | null) {
  if (!id) { resetState(); return }
  currentDoc.value = documents.value.find(d => d.id === id) || null
  summary.value = currentDoc.value?.aiSummary || ''
  resetProgress()
  // Fetch full content for raw display
  if (currentDoc.value) {
    try {
      const doc = await markdownAPI.getDocument(id!)
      rawContent.value = doc.fullContent || ''
    } catch { rawContent.value = '' }
  }
  // Restore history: if status >= classified, reload chunks → strategy → sliced preview
  if (currentDoc.value && (currentDoc.value.status === 'classified' || currentDoc.value.status === 'exported')) {
    try {
      const r = await markdownAPI.getChunks(id!)
      const chunks = r.chunks || []
      if (chunks.length > 0) {
        // Rebuild strategy array from stored chunks
        strategy.value = chunks.map((c: any) => ({
          start: c.contentStartPos,
          end: c.contentEndPos,
          title: c.chunkTitle,
          type: c.chunkType,
          reason: c.classificationReason || ''
        }))
        strategyConfirmed.value = true
        // Regenerate sliced preview
        await doSlice()
      }
    } catch { /* no chunks yet */ }
  }
  // If status is exported, also try to reload export files
  if (currentDoc.value?.status === 'exported') {
    try {
      const r = await markdownAPI.export(id!)
      exportResult.value = r
    } catch { /* export not available yet */ }
  }
}

function resetState() {
  currentDoc.value = null; summary.value = ''; strategy.value = []; strategyConfirmed.value = false
  sliced.value = { structuredFile: '', semanticFile: '', structuredCount: 0, semanticCount: 0 }
  rawContent.value = ''; exportResult.value = null
}
function resetProgress() { progressMsg.value = ''; progressErr.value = false; progressSteps.value = [] }

async function doSummarize() {
  if (!currentDocId.value) return
  summarizing.value = true
  progressErr.value = false
  startProgressTracker()
  progressMsg.value = 'AI分析文档，生成摘要...'
  addProgressStep('step', '开始文档摘要分析')

  try {
    markdownAPI.summarize(currentDocId.value,
      (t, d) => {
        if (t === 'phase') { progressMsg.value = d.msg; addProgressStep('step', d.msg) }
        if (t === 'progress') { addProgressStep('round', d.msg) }
      },
      (e) => { progressErr.value = true; progressMsg.value = '摘要失败: ' + e.message },
      (r) => {
        summary.value = r.summary
        progressMsg.value = '摘要完成'
        addProgressStep('result', `摘要生成完成 (${r.summary?.length || 0} 字)`)
        if (currentDoc.value) { currentDoc.value.aiSummary = r.summary; currentDoc.value.status = 'summarized' }
        stopProgressTracker()
        ElMessage.success('摘要生成完成')
      }
    )
  } catch { progressErr.value = true; progressMsg.value = '摘要失败'; stopProgressTracker() }
  finally { summarizing.value = false }
}

async function doChunk() {
  if (!currentDocId.value) return
  chunking.value = true
  progressErr.value = false
  strategy.value = []
  sliced.value = { structuredFile: '', semanticFile: '', structuredCount: 0, semanticCount: 0 }

  startProgressTracker()
  progressMsg.value = '智能分块分析启动...'
  addProgressStep('step', '初始化分块Agent')

  try {
    markdownAPI.chunk(currentDocId.value, connectedTable.value || null,
      (t, d) => {
        if (t === 'phase') { progressMsg.value = d.msg; addProgressStep('step', d.msg) }
        else if (t === 'step') { addProgressStep('step', d.msg) }
        else if (t === 'tool_call') {
          const paramsShort = typeof d.params === 'object' ? JSON.stringify(d.params).substring(0, 60) : String(d.params || '').substring(0, 60)
          addProgressStep('tool', `🔧 ${d.tool} ${paramsShort}`)
          progressMsg.value = `Phase 2: ${d.tool} (第${d.step || '?'}次工具调用)`
        }
        else if (t === 'progress') {
          addProgressStep('round', `第${d.round}轮: ${d.msg} (共${d.total}轮)`)
          progressMsg.value = `第${d.round}/${d.total}轮分块分析...`
        }
      },
      (e) => { progressErr.value = true; progressMsg.value = '分块失败: ' + e.message; stopProgressTracker() },
      (r) => {
        strategy.value = r.strategy || []
        progressMsg.value = ''
        addProgressStep('result', `分块完成: ${strategy.value.length} 块`)
        stopProgressTracker()
        ElMessage.success(`分块完成: ${strategy.value.length} 块`)
        if (strategy.value.length > 0) doSlice()
      }
    )
  } catch { progressErr.value = true; progressMsg.value = '分块失败'; stopProgressTracker() }
  finally { chunking.value = false }
}

async function doSlice() {
  if (!currentDocId.value || !strategy.value.length) return
  try {
    const r = await markdownAPI.slice(currentDocId.value, strategy.value)
    sliced.value = r
  } catch (e: any) { ElMessage.error('切片预览失败: ' + (e.response?.data?.error || e.message)) }
}

async function doConfirm() {
  if (!currentDocId.value || !strategy.value.length) return
  confirming.value = true
  try {
    await markdownAPI.confirm(currentDocId.value, strategy.value)
    strategyConfirmed.value = true
    if (currentDoc.value) currentDoc.value.status = 'classified'
    // Auto-export after confirm so download buttons appear
    await doExport()
    ElMessage.success('已确认并存入数据库')
  } catch (e: any) { ElMessage.error('确认失败: ' + (e.response?.data?.error || e.message)) }
  finally { confirming.value = false }
}

async function doFeedback() {
  if (!currentDocId.value || !feedbackText.value.trim() || !strategy.value.length) return
  feedbacking.value = true
  progressErr.value = false

  startProgressTracker()
  progressMsg.value = 'AI根据反馈调整策略...'
  addProgressStep('step', `收到反馈: ${feedbackText.value.trim().substring(0, 50)}...`)

  try {
    markdownAPI.feedback(currentDocId.value, connectedTable.value || null, strategy.value, feedbackText.value.trim(),
      (t, d) => {
        if (t === 'phase') { progressMsg.value = d.msg; addProgressStep('step', d.msg) }
        else if (t === 'step') { addProgressStep('step', d.msg) }
        else if (t === 'tool_call') {
          const paramsShort = typeof d.params === 'object' ? JSON.stringify(d.params).substring(0, 60) : String(d.params || '').substring(0, 60)
          addProgressStep('tool', `🔧 ${d.tool} ${paramsShort}`)
          progressMsg.value = `${d.tool} (第${d.step || '?'}次工具调用)`
        }
        else if (t === 'progress') { addProgressStep('round', d.msg) }
      },
      (e) => { progressErr.value = true; progressMsg.value = '反馈处理失败: ' + e.message; stopProgressTracker() },
      (r) => {
        strategy.value = r.strategy || []
        feedbackText.value = ''
        progressMsg.value = ''
        addProgressStep('result', `策略已调整: ${strategy.value.length} 块`)
        stopProgressTracker()
        ElMessage.success(`策略已调整: ${strategy.value.length} 块`)
        if (strategy.value.length > 0) doSlice()
      }
    )
  } catch { progressErr.value = true; progressMsg.value = '反馈处理失败'; stopProgressTracker() }
  finally { feedbacking.value = false }
}

async function doExport() {
  if (!currentDocId.value) return
  exporting.value = true
  try {
    const r = await markdownAPI.export(currentDocId.value)
    exportResult.value = r
  } catch (e: any) { ElMessage.error('导出失败: ' + (e.response?.data?.error || e.message)) }
  finally { exporting.value = false }
}

function downloadStructured() {
  if (!exportResult.value) return
  const r = exportResult.value
  downloadFile(r.structuredFile, r.structuredFileName)
  ElMessage.success('结构化数据文件已下载')
}

function downloadSemantic() {
  if (!exportResult.value) return
  const r = exportResult.value
  downloadFile(r.semanticFile, r.semanticFileName)
  ElMessage.success('语义向量化文件已下载')
}

function downloadFile(content: string, name: string) {
  const blob = new Blob([content], { type: 'text/markdown;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url; a.download = name; a.click()
  // Delay revoke to allow browser to start download
  setTimeout(() => URL.revokeObjectURL(url), 100)
}

async function deleteCurrentDoc() {
  if (!currentDocId.value) return
  try {
    await ElMessageBox.confirm('确认删除？', '提示', { type: 'warning' })
    await markdownAPI.deleteDocument(currentDocId.value)
    resetState()
    await loadDocumentList()
  } catch {}
}

watch(() => store.activeConnection, (conn) => {
  if (conn && store.schema?.tables) tables.value = store.schema.tables
})

onMounted(async () => {
  await loadDocumentList()
  if (!store.activeConnection) {
    await store.fetchConnections()
    const a = store.connections.find(c => c.isActive)
    if (a) await store.switchConnection(a.id)
  }
  if (store.schema?.tables) tables.value = store.schema.tables
})

onUnmounted(() => {
  if (elapsedTimer) clearInterval(elapsedTimer)
})
</script>

<style scoped>
.md-workbench { display:flex; flex-direction:column; height:100%; padding:12px; gap:10px; overflow:hidden; }
.md-header { display:flex; justify-content:space-between; align-items:center; flex-shrink:0; }
.md-header h2 { margin:0; font-size:18px; }
.md-toolbar { display:flex; align-items:center; gap:8px; flex-shrink:0; }
.action-buttons { margin-left:auto; display:flex; gap:4px; }

/* ── Agent Progress Panel ── */
.agent-progress-panel {
  flex-shrink: 0;
  background: #f0f9ff;
  border: 1px solid #b3d8ff;
  border-radius: 8px;
  padding: 8px 14px;
  max-height: 160px;
  overflow-y: auto;
}
.agent-progress-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #409eff;
}
.agent-phase { flex: 1; }
.agent-elapsed {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  color: #909399;
  background: #fff;
  padding: 2px 8px;
  border-radius: 4px;
}
.agent-steps { margin-top: 6px; }
.agent-step-row {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #606266;
  padding: 2px 0;
}
.step-dot { flex-shrink: 0; font-size: 10px; }
.step-text { flex: 1; }
.step-time {
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  color: #c0c4cc;
}

.md-progress { flex-shrink:0; }

.md-body { flex:1; display:flex; gap:10px; overflow:hidden; min-height:0; }
.md-col { flex:1; border:1px solid #e4e7ed; border-radius:8px; background:#fff; display:flex; flex-direction:column; overflow:hidden; min-width:0; }
.left-col { flex:2; }
.center-col { flex:2; }
.right-col { flex:2; }

.col-title { font-weight:600; font-size:13px; color:#303133; padding:10px 14px; border-bottom:1px solid #e4e7ed; background:#fafbfc; flex-shrink:0; display:flex; align-items:center; }
.col-empty { padding:20px; text-align:center; color:#c0c4cc; font-size:13px; flex:1; display:flex; align-items:center; justify-content:center; }

.raw-content { flex:1; overflow-y:auto; padding:12px 16px; font-family:'JetBrains Mono',monospace; font-size:12px; line-height:1.6; white-space:pre-wrap; word-break:break-word; color:#303133; }
.summary-box { overflow-y:auto; padding:12px 16px; font-size:13px; line-height:1.7; color:#303133; white-space:pre-wrap; max-height:150px; flex-shrink:0; }

.strategy-box { flex:1; overflow-y:auto; padding:0; min-height:0; }
.strategy-row { padding:6px 14px; border-bottom:1px solid #f0f2f5; font-size:12px; display:flex; align-items:center; }
.strategy-title { flex:1; }
.strategy-pos { color:#909399; font-family:'JetBrains Mono',monospace; font-size:11px; }

.feedback-area { padding:8px 14px; background:#fff9e6; border-top:1px solid #f0f2f5; flex-shrink:0; }
.feedback-title { font-weight:600; font-size:12px; color:#e6a23c; margin-bottom:6px; }
.feedback-btns { margin-top:6px; display:flex; justify-content:flex-end; }

.export-preview { display:flex; flex-direction:column; flex:1; overflow:hidden; }
.preview-text { flex:1; overflow-y:auto; padding:10px 14px; font-family:'JetBrains Mono',monospace; font-size:11px; line-height:1.4; white-space:pre-wrap; word-break:break-all; color:#606266; background:#fafbfc; margin:0; }
</style>
