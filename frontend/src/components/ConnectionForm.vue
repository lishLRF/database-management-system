<template>
  <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
    <el-form-item label="连接名称" prop="name">
      <el-input v-model="form.name" placeholder="我的数据库" />
    </el-form-item>

    <el-form-item label="数据库类型" prop="dbType">
      <el-radio-group v-model="form.dbType">
        <el-radio value="postgresql">PostgreSQL</el-radio>
        <el-radio value="sqlite">SQLite</el-radio>
      </el-radio-group>
    </el-form-item>

    <template v-if="form.dbType === 'postgresql'">
      <el-form-item label="主机" prop="host">
        <el-input v-model="form.host" placeholder="localhost" />
      </el-form-item>
      <el-form-item label="端口" prop="port">
        <el-input-number v-model="form.port" :min="1" :max="65535" />
      </el-form-item>
      <el-form-item label="数据库名" prop="database">
        <el-input v-model="form.database" placeholder="mydb" />
      </el-form-item>
      <el-form-item label="用户名" prop="username">
        <el-input v-model="form.username" placeholder="postgres" />
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input v-model="form.password" type="password" show-password />
      </el-form-item>
    </template>

    <template v-else>
      <el-form-item label="文件路径" prop="database">
        <el-input v-model="form.database" placeholder="/path/to/database.db" />
      </el-form-item>
    </template>

    <el-form-item>
      <el-button type="primary" @click="handleSubmit" :loading="submitting">保存</el-button>
      <el-button @click="handleTest" :loading="testing">测试连接</el-button>
    </el-form-item>
  </el-form>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { connectionAPI } from '@/api/connection'
import type { FormInstance } from 'element-plus'

const emit = defineEmits(['success'])

const formRef = ref<FormInstance>()
const form = reactive({
  name: '',
  dbType: 'postgresql' as 'postgresql' | 'sqlite',
  host: 'localhost',
  port: 5432,
  database: '',
  username: '',
  password: ''
})

const rules = {
  name: [{ required: true, message: '请输入连接名称', trigger: 'blur' }],
  database: [{ required: true, message: '请输入数据库名称', trigger: 'blur' }],
  host: [{ required: true, message: '请输入主机地址', trigger: 'blur' }],
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }]
}

const submitting = ref(false)
const testing = ref(false)

const handleSubmit = async () => {
  await formRef.value?.validate()
  submitting.value = true
  try {
    await connectionAPI.createConnection(form)
    ElMessage.success('连接创建成功')
    emit('success')
  } catch (error) {
    ElMessage.error('创建失败')
  } finally {
    submitting.value = false
  }
}

const handleTest = async () => {
  await formRef.value?.validate()
  testing.value = true
  try {
    const tempConn = await connectionAPI.createConnection({ ...form, name: '__test__' })
    const result = await connectionAPI.testConnection((tempConn as any).connection.id)
    if ((result as any).success) {
      ElMessage.success('连接测试成功')
    } else {
      ElMessage.error('连接测试失败')
    }
  } catch (error) {
    ElMessage.error('连接测试失败')
  } finally {
    testing.value = false
  }
}
</script>
