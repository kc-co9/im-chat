
import { createApp } from 'vue'
import { createRouter, createWebHashHistory } from 'vue-router'
import App from './App.vue'
import Login from './components/Login.vue'
import Register from './components/Register.vue'
import Chat from './components/Chat.vue'

// 创建路由实例
const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/',
      redirect: '/login'
    },
    {
      path: '/login',
      component: Login
    },
    {
      path: '/register',
      component: Register
    },
    {
      path: '/chat',
      component: Chat
    }
  ]
})

// 创建Vue应用实例
const app = createApp(App)

// 使用路由
app.use(router)

// 挂载应用
app.mount('#app')
