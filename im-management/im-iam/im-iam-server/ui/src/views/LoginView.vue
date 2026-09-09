<script setup lang="ts">
import { ref } from 'vue'
import { ElButton } from 'element-plus'
import { cookie } from '../api/http'

const parameters = new URLSearchParams(window.location.search)
const inheritedRoute =
  window.location.hash !== '#/' && window.location.hash.startsWith('#/')
    ? `/${window.location.hash}`
    : undefined
const requestedRoute = parameters.get('continue') ?? inheritedRoute
const continuePath =
  requestedRoute === '/' || requestedRoute?.startsWith('/#/') ? requestedRoute : undefined
const action = continuePath ? `/login?continue=${encodeURIComponent(continuePath)}` : '/login'
const csrfToken = decodeURIComponent(cookie('XSRF-TOKEN') ?? '')
const authenticationFailed = parameters.has('error')
const submitting = ref(false)
</script>

<template>
  <div class="login-shell">
    <section class="login-brand" aria-label="IM IAM">
      <div class="login-brand-mark" aria-hidden="true">I</div>
      <div>
        <p class="login-product">IM CHAT</p>
        <h1>Identity &amp; Access</h1>
        <p class="login-edition">Management Console</p>
      </div>
      <div class="login-brand-footer">
        <span class="login-status-dot" aria-hidden="true"></span>
        IAM authorization service
      </div>
    </section>

    <main class="login-main">
      <form class="login-form" method="post" :action="action" @submit="submitting = true">
        <header>
          <p class="login-eyebrow">管理员认证</p>
          <h2>登录 IAM</h2>
        </header>

        <p v-if="authenticationFailed" class="login-error" role="alert">邮箱或密码错误</p>

        <input type="hidden" name="_csrf" :value="csrfToken" />

        <label class="login-field">
          <span>邮箱</span>
          <input
            name="username"
            type="email"
            autocomplete="username"
            placeholder="admin@imchat.com"
            required
            autofocus
          />
        </label>

        <label class="login-field">
          <span>密码</span>
          <input
            name="password"
            type="password"
            autocomplete="current-password"
            placeholder="请输入密码"
            required
          />
        </label>

        <ElButton class="login-submit" native-type="submit" :disabled="submitting">
          {{ submitting ? '正在登录...' : '登录' }}
        </ElButton>
      </form>
    </main>
  </div>
</template>
