<template>
  <div class="ai-chat-page">
    <div class="chat-sidebar">
      <el-button type="primary" @click="newConversation" style="width: 100%; margin-bottom: 12px">
        + 新建对话
      </el-button>
      <div class="conversation-list">
        <div
          v-for="conv in conversations"
          :key="conv.conversationId"
          :class="['conv-item', { active: conv.conversationId === currentConvId }]"
          @click="selectConversation(conv.conversationId)"
        >
          <div class="conv-title">{{ conv.title }}</div>
          <div class="conv-meta">{{ conv.messageCount }} 条消息</div>
          <el-button
            size="small"
            type="danger"
            :icon="Delete"
            circle
            @click.stop="handleDeleteConv(conv.conversationId)"
            class="delete-btn"
          />
        </div>
      </div>
    </div>

    <div class="chat-main">
      <div v-if="!currentConvId" class="empty-state">
        <p>选择一个对话或新建对话开始</p>
      </div>
      <template v-else>
        <div class="message-list" ref="msgListRef">
          <div
            v-for="msg in messages"
            :key="msg.id"
            :class="['message', msg.messageType]"
          >
            <div class="msg-role">{{ msg.messageType === 'user' ? '你' : '🤖 AI' }}</div>
            <div class="msg-content">
              <code v-if="msg.messageType === 'ai'" style="white-space: pre-wrap">{{ msg.messageContent }}</code>
              <span v-else>{{ msg.messageContent }}</span>
            </div>
            <el-button
              v-if="msg.messageType === 'ai'"
              size="small"
              @click="applyToEditor(msg.messageContent)"
              style="margin-top: 4px"
            >
              应用到编辑器
            </el-button>
          </div>
        </div>

        <div class="chat-input">
          <el-input
            v-model="chatInput"
            placeholder="描述你想查询的数据..."
            @keyup.enter="sendMessage"
            :disabled="sending"
          >
            <template #append>
              <el-button @click="sendMessage" :loading="sending">发送</el-button>
            </template>
          </el-input>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete } from '@element-plus/icons-vue'
import { conversationAPI, type ConversationInfo, type ChatMessage } from '@/api/ai-conversation'
import { useConnectionStore } from '@/stores/connection'

const router = useRouter()

const store = useConnectionStore()
const conversations = ref<ConversationInfo[]>([])
const currentConvId = ref<string | null>(null)
const messages = ref<ChatMessage[]>([])
const chatInput = ref('')
const sending = ref(false)
const msgListRef = ref<HTMLDivElement>()

onMounted(async () => {
  await loadConversations()
})

async function loadConversations() {
  try {
    const res = await conversationAPI.listConversations() as any
    conversations.value = res.conversations || []
  } catch (e) {
    // ignore
  }
}

async function selectConversation(convId: string) {
  currentConvId.value = convId
  try {
    const res = await conversationAPI.getMessages(convId) as any
    messages.value = res.messages || []
    await nextTick()
    scrollToBottom()
  } catch (e) {
    ElMessage.error('加载消息失败')
  }
}

function newConversation() {
  const id = 'conv_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8)
  currentConvId.value = id
  messages.value = []
  chatInput.value = ''
  conversations.value.unshift({ conversationId: id, title: '新对话', messageCount: 0, createdAt: new Date().toISOString() })
}

async function sendMessage() {
  if (!chatInput.value.trim()) return
  if (!store.activeConnection) {
    ElMessage.warning('请先选择数据库连接')
    return
  }

  sending.value = true
  const msg = chatInput.value.trim()
  chatInput.value = ''

  // Optimistic user message
  messages.value.push({
    id: Date.now(),
    conversationId: currentConvId.value!,
    userId: '',
    connectionId: store.activeConnection.id,
    messageType: 'user',
    messageContent: msg,
    createdAt: new Date().toISOString()
  })
  await nextTick()
  scrollToBottom()

  try {
    const res = await conversationAPI.chat(
      currentConvId.value!,
      store.activeConnection.id,
      msg
    ) as any

    if (res.sql) {
      messages.value.push({
        id: Date.now() + 1,
        conversationId: currentConvId.value!,
        userId: '',
        connectionId: store.activeConnection.id,
        messageType: 'ai',
        messageContent: res.sql,
        createdAt: new Date().toISOString()
      })
      // Update conversation title
      const conv = conversations.value.find(c => c.conversationId === currentConvId.value)
      if (conv) {
        conv.title = msg.length > 30 ? msg.slice(0, 30) + '...' : msg
        conv.messageCount = messages.value.length
      }
    }
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '发送失败')
  } finally {
    sending.value = false
    await nextTick()
    scrollToBottom()
  }
}

function applyToEditor(sql: string) {
  store.setPendingSql(sql)
  router.push('/sql')
  ElMessage.success('已跳转到SQL工作台')
}

async function handleDeleteConv(convId: string) {
  try {
    await ElMessageBox.confirm('确认删除该对话？', '提示')
    await conversationAPI.deleteConversation(convId)
    if (currentConvId.value === convId) {
      currentConvId.value = null
      messages.value = []
    }
    conversations.value = conversations.value.filter(c => c.conversationId !== convId)
    ElMessage.success('已删除')
  } catch (e) {
    // cancelled
  }
}

function scrollToBottom() {
  if (msgListRef.value) {
    msgListRef.value.scrollTop = msgListRef.value.scrollHeight
  }
}
</script>

<style scoped>
.ai-chat-page {
  display: flex;
  height: 100%;
}

.chat-sidebar {
  width: 260px;
  border-right: 1px solid #e4e7ed;
  padding: 16px;
  overflow-y: auto;
}

.conv-item {
  position: relative;
  padding: 10px 12px;
  margin-bottom: 4px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s;
}
.conv-item:hover { background: #f0f2f5; }
.conv-item.active { background: #ecf5ff; }

.conv-title {
  font-size: 13px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding-right: 24px;
}
.conv-meta {
  font-size: 11px;
  color: #909399;
  margin-top: 2px;
}
.delete-btn {
  position: absolute;
  top: 8px;
  right: 8px;
  opacity: 0;
  transition: opacity 0.2s;
}
.conv-item:hover .delete-btn { opacity: 1; }

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 16px;
}

.empty-state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #909399;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.message {
  margin-bottom: 16px;
  padding: 10px 14px;
  border-radius: 8px;
  max-width: 80%;
}
.message.user {
  background: #ecf5ff;
  margin-left: auto;
}
.message.ai {
  background: #f5f7fa;
}

.msg-role {
  font-size: 12px;
  font-weight: bold;
  color: #606266;
  margin-bottom: 4px;
}
.msg-content {
  font-size: 14px;
  line-height: 1.6;
}
.msg-content code {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 13px;
  color: #409eff;
}

.chat-input {
  margin-top: 12px;
  border-top: 1px solid #e4e7ed;
  padding-top: 12px;
}
</style>
