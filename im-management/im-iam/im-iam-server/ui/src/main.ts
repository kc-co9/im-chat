import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import LoginView from './views/LoginView.vue'
import { router } from './router'
import { auth } from './state/auth'
import { notify } from './state/feedback'
import { loginUrl } from './navigation'
import './styles/tokens.css'
import './style.css'
import './styles/console.css'

window.addEventListener('iam:unauthorized', () => {
  auth.principal = undefined
  auth.checked = true
  const path = location.hash.startsWith('#/') ? location.hash.substring(1) : '/'
  location.assign(loginUrl(path))
})
window.addEventListener('iam:forbidden', () => {
  notify('没有执行该 IAM 管理操作的权限', 'error')
})

if (window.location.pathname === '/login') {
  createApp(LoginView).use(ElementPlus).mount('#app')
} else {
  createApp(App).use(ElementPlus).use(router).mount('#app')
}
