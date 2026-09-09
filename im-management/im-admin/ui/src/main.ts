import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import { router } from './router'
import { auth } from './state/auth'
import { ElMessage } from 'element-plus'
import './styles/tokens.css'
import './style.css'
import './styles/console.css'

window.addEventListener('iam:unauthorized', () => {
  auth.principal = undefined
  auth.checked = true
  location.assign('/iam/login')
})
window.addEventListener('admin:forbidden', () => ElMessage.error('没有执行该操作的权限'))
window.addEventListener('admin:conflict', () => ElMessage.warning('数据状态已变化，请刷新后重试'))

createApp(App).use(ElementPlus).use(router).mount('#app')
