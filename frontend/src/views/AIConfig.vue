<template>
  <div class="ai-config-page">
    <h2>AI 配置</h2>
    <el-form :model="form" label-width="120px" style="max-width: 600px; margin-top: 24px">
      <el-form-item label="API 提供商">
        <el-select v-model="form.apiProvider" placeholder="选择API提供商">
          <el-option label="OpenAI" value="openai" />
          <el-option label="DeepSeek" value="deepseek" />
          <el-option label="自定义 (OpenAI兼容)" value="custom" />
        </el-select>
      </el-form-item>

      <el-form-item label="API 地址">
        <el-input v-model="form.apiBaseUrl" placeholder="https://api.deepseek.com" />
      </el-form-item>

      <el-form-item label="API Key">
        <el-input v-model="form.apiKey" type="password" show-password placeholder="sk-..." />
      </el-form-item>

      <el-form-item label="模型名称">
        <el-input v-model="form.modelName" placeholder="deepseek-chat" />
      </el-form-item>

      <el-form-item label="Temperature">
        <el-slider v-model="form.temperature" :min="0" :max="2" :step="0.1" show-input style="width: 300px" />
      </el-form-item>

      <el-form-item label="Max Tokens">
        <el-input-number v-model="form.maxTokens" :min="100" :max="131072" :step="100" />
      </el-form-item>

      <el-form-item>
        <el-button type="primary" @click="handleSave" :loading="saving">保存配置</el-button>
        <el-button @click="handleTest" :loading="testing" style="margin-left: 12px">测试连接</el-button>
      </el-form-item>
    </el-form>

    <el-divider />

    <div v-if="currentConfig" class="current-config">
      <h3>当前配置</h3>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="提供商">{{ currentConfig.apiProvider }}</el-descriptions-item>
        <el-descriptions-item label="API 地址">{{ currentConfig.apiBaseUrl }}</el-descriptions-item>
        <el-descriptions-item label="模型">{{ currentConfig.modelName }}</el-descriptions-item>
        <el-descriptions-item label="Temperature">{{ currentConfig.temperature }}</el-descriptions-item>
        <el-descriptions-item label="Max Tokens">{{ currentConfig.maxTokens }}</el-descriptions-item>
        <el-descriptions-item label="API Key">••••••••</el-descriptions-item>
      </el-descriptions>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { aiConfigAPI } from '@/api/ai-config'
import type { AiConfig } from '@/types'

const saving = ref(false)
const testing = ref(false)
const currentConfig = ref<AiConfig | null>(null)

const form = reactive({
  apiProvider: 'deepseek',
  apiBaseUrl: 'https://api.deepseek.com',
  modelName: 'deepseek-chat',
  apiKey: '',
  temperature: 0.7,
  maxTokens: 2000
})

onMounted(async () => {
  try {
    const res = await aiConfigAPI.getConfig() as any
    if (res.config) {
      currentConfig.value = res.config
      form.apiProvider = res.config.apiProvider || 'deepseek'
      form.apiBaseUrl = res.config.apiBaseUrl || ''
      form.modelName = res.config.modelName || ''
      form.temperature = res.config.temperature ?? 0.7
      form.maxTokens = res.config.maxTokens ?? 2000
    }
  } catch (e) {
    // No config yet — fine
  }
})

async function handleSave() {
  saving.value = true
  try {
    const res = await aiConfigAPI.saveConfig(form) as any
    if (res.config) {
      currentConfig.value = res.config
      ElMessage.success('配置保存成功')
    }
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleTest() {
  testing.value = true
  try {
    const res = await aiConfigAPI.testConnection() as any
    if (res.success) {
      ElMessage.success(res.message || '连接成功')
    } else {
      ElMessage.error(res.message || '连接失败')
    }
  } catch (e: any) {
    ElMessage.error(e.response?.data?.message || '测试失败')
  } finally {
    testing.value = false
  }
}
</script>

<style scoped>
.ai-config-page {
  padding: 24px;
  max-width: 800px;
}

h2 { color: #303133; }

.current-config {
  margin-top: 16px;
}
</style>
