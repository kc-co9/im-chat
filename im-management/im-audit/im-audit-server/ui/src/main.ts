import { createApp } from 'vue'
import ElementPlus, { ElMessage } from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import { router } from './router'
import { auth } from './state/auth'
import { loginUrlFromHash } from './navigation'
import './styles/tokens.css'
import './style.css'
import './styles/console.css'

window.addEventListener('iam:unauthorized', () => {
  auth.principal = undefined
  auth.checked = true
  location.assign(loginUrlFromHash(location.hash))
})
window.addEventListener('audit:forbidden', () => ElMessage.error('没有访问该审计能力的权限'))

createApp(App).use(ElementPlus).use(router).mount('#app')
