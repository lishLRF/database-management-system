import { createRouter, createWebHistory } from 'vue-router'
import Home from '@/views/Home.vue'
import ConnectionManage from '@/views/ConnectionManage.vue'
import SqlWorkspace from '@/views/SqlWorkspace.vue'
import AIConfig from '@/views/AIConfig.vue'
import SmartFill from '@/views/SmartFill.vue'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: Home
  },
  {
    path: '/connections',
    name: 'ConnectionManage',
    component: ConnectionManage
  },
  {
    path: '/sql',
    name: 'SqlWorkspace',
    component: SqlWorkspace
  },
  {
    path: '/ai-config',
    name: 'AIConfig',
    component: AIConfig
  },
  {
    path: '/smart-fill',
    name: 'SmartFill',
    component: SmartFill
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
