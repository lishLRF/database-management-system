<template>
  <div class="format-editor-page">
    <!-- LEFT: Editor -->
    <div class="editor-panel">
      <div class="format-toolbar">
        <el-radio-group v-model="format" size="small" @change="onFormatChange">
          <el-radio-button value="json">JSON</el-radio-button>
          <el-radio-button value="xml">XML</el-radio-button>
          <el-radio-button value="yaml">YAML</el-radio-button>
        </el-radio-group>
        <el-button size="small" @click="doValidate" :icon="Check">格式审查</el-button>
        <el-button size="small" type="warning" @click="doRepair" :loading="repairing" :disabled="!editorContent.trim()">🤖 AI修复</el-button>
        <div class="status-area">
          <el-tag v-if="validationOk" type="success" size="small">✅ 格式正确</el-tag>
          <el-tag v-else-if="validationMsg" type="danger" size="small">⚠️ {{ validationMsg }}</el-tag>
        </div>
      </div>

      <div class="monaco-area">
        <SqlEditor ref="editorRef" v-model="editorContent" :schema="null" @execute="doValidate" />
      </div>
    </div>

    <!-- RIGHT: AI Chat -->
    <div class="chat-panel">
      <div class="chat-header">
        <span>🤖 AI 助手</span>
        <el-button link size="small" @click="chatMessages=[]">清空</el-button>
      </div>
      <div class="chat-messages" ref="chatRef">
        <div v-for="msg in chatMessages" :key="msg.id" :class="['msg', msg.type]">
          <div class="msg-head"><span class="msg-icon">{{ msgIcon(msg.type) }}</span><span class="msg-role">{{ msgRole(msg.type) }}</span><span class="msg-time">{{ msg.time }}</span></div>
          <div class="msg-body"><code v-if="msg.type==='ai'" class="chat-code">{{ msg.content }}</code><span v-else>{{ msg.content }}</span></div>
        </div>
      </div>
      <div class="chat-input">
        <el-input v-model="chatInput" placeholder="描述你要生成的数据格式..." @keyup.enter="sendGenerate" :disabled="chatBusy" size="small" />
        <el-button type="primary" @click="sendGenerate" :loading="chatBusy" size="small" style="margin-left:6px">发送</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Check } from '@element-plus/icons-vue'
import SqlEditor from '@/components/SqlEditor.vue'
import { aiConfigAPI } from '@/api/ai-config'
import { useFormatStore } from '@/stores/formatStore'

const format = ref<'json' | 'xml' | 'yaml'>('json')
const editorContent = ref('')
const editorRef = ref<InstanceType<typeof SqlEditor>>()
const validationOk = ref(false)
const validationMsg = ref('')
const repairing = ref(false)
const chatBusy = ref(false)
const chatInput = ref('')
const chatRef = ref<HTMLDivElement>()
const formatStore = useFormatStore()

interface ChatMsg { id: number; type: 'user' | 'ai' | 'status' | 'error'; content: string; time: string }
const chatMessages = ref<ChatMsg[]>([])
const now = () => new Date().toLocaleTimeString('zh-CN',{hour:'2-digit',minute:'2-digit',second:'2-digit'})
const msgIcon = (t:string) => ({user:'👤',ai:'🤖',status:'✅',error:'⚠️'}[t]||'')
const msgRole = (t:string) => ({user:'你',ai:'AI',status:'状态',error:'错误'}[t]||'')
function addMsg(type:ChatMsg['type'],content:string){chatMessages.value.push({id:Date.now(),type,content,time:now()});nextTick(()=>{if(chatRef.value)chatRef.value.scrollTop=chatRef.value.scrollHeight})}

function onFormatChange() { doValidate() }
function doValidate() {
  const v = editorContent.value.trim()
  if (!v) { validationOk.value=false; validationMsg.value=''; return }
  try {
    if (format.value==='json') { JSON.parse(v); validationOk.value=true; validationMsg.value=''; }
    else if (format.value==='xml') { new DOMParser().parseFromString(v,'text/xml'); const err = new DOMParser().parseFromString(v,'text/xml').querySelector('parsererror'); validationOk.value = !err; validationMsg.value = err ? err.textContent?.split('\n')[0]||'XML语法错误' : ''; }
    else { validationOk.value = v.includes(':') && (v.includes('\n')||v.includes('\r\n')); validationMsg.value = validationOk.value ? '' : 'YAML需要包含键值对(:)'; }
  } catch(e:any){validationOk.value=false;validationMsg.value=e.message?.substring(0,80)||'格式错误'}
}

async function doRepair() {
  if (!editorContent.value.trim()) return
  repairing.value = true
  let result = ''
  try {
    let repairResult = ''
    aiConfigAPI.repairFormat(format.value, editorContent.value.trim(),
      (t,d)=>{ if(t==='chunk'){repairResult+=d.chunk||''} },
      (e)=>{addMsg('error','AI修复失败: '+e.message);repairing.value=false},
      ()=>{
        editorContent.value = format.value==='json' ? beautifyJSON(repairResult) : repairResult
        if(editorContent.value)doValidate(); repairing.value=false
        addMsg('ai','✅ 已修复: '+format.value.toUpperCase())
        ElMessage.success('AI修复完成')
      }
    )
  } catch(e:any){addMsg('error','AI修复失败');repairing.value=false}
}

async function sendGenerate() {
  if (!chatInput.value.trim()) return
  const desc = chatInput.value.trim(); chatInput.value = ''; chatBusy.value = true
  addMsg('user',`生成 ${format.value.toUpperCase()}: ${desc}`)
  let result = ''
  try {
    let genResult = ''
    aiConfigAPI.generateFormat(format.value, desc,
      (t,d)=>{ if(t==='chunk'){genResult+=d.chunk||''} },
      (e)=>{addMsg('error','生成失败: '+e.message);chatBusy.value=false},
      ()=>{
        editorContent.value = format.value==='json' ? beautifyJSON(genResult) : genResult
        if(editorContent.value)doValidate(); chatBusy.value=false
        addMsg('ai','✅ 已生成 '+format.value.toUpperCase())
        ElMessage.success('已生成')
      }
    )
  } catch(e:any){addMsg('error','生成失败');chatBusy.value=false}
}

function beautifyJSON(str:string):string { try{return JSON.stringify(JSON.parse(str),null,2)}catch{return str} }

onMounted(()=>{
  const p = formatStore.takePending()
  if (p) { format.value = p.format; editorContent.value = p.data; doValidate() }
})
</script>

<style scoped>
.format-editor-page { display:flex; height:100%; }
.editor-panel { flex:1; display:flex; flex-direction:column; min-width:0; padding:12px; gap:8px; }
.format-toolbar { display:flex; align-items:center; gap:8px; flex-shrink:0; flex-wrap:wrap; }
.status-area { margin-left:auto; }
.monaco-area { flex:1; min-height:300px; border:1px solid #e4e7ed; border-radius:6px; overflow:hidden; }

.chat-panel { width:340px; display:flex; flex-direction:column; border-left:1px solid #e4e7ed; background:#fafbfc; }
.chat-header { display:flex; justify-content:space-between; align-items:center; padding:10px 12px; border-bottom:1px solid #e4e7ed; background:#fff; font-size:14px; font-weight:600; }
.chat-messages { flex:1; overflow-y:auto; padding:12px; }
.chat-input { display:flex; padding:10px 12px; border-top:1px solid #e4e7ed; background:#fff; }

.msg { margin-bottom:10px; padding:10px 12px; border-radius:8px; border-left:3px solid #c0c4cc; background:#fff; }
.msg.user { border-left-color:#409eff; background:#ecf5ff; }
.msg.ai { border-left-color:#67c23a; background:#f0f9eb; }
.msg.error { border-left-color:#f56c6c; background:#fef0f0; }
.msg-head { display:flex; align-items:center; gap:6px; margin-bottom:4px; }
.msg-icon { font-size:14px; }
.msg-role { font-size:12px; font-weight:600; color:#606266; }
.msg-time { font-size:11px; color:#c0c4cc; margin-left:auto; }
.msg-body { font-size:13px; line-height:1.5; word-break:break-word; }
.chat-code { font-family:'JetBrains Mono',monospace; font-size:12px; color:#409eff; white-space:pre-wrap; background:#f0f5ff; padding:6px 8px; border-radius:4px; display:block; }
</style>
