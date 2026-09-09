<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, RouterView } from 'vue-router'
import { ChatDotRound, User } from '@element-plus/icons-vue'
import { api } from './api/admin'
import ConsoleShell from './components/ConsoleShell.vue'
import { consoleLinks } from './config/consoleLinks'
import { auth, can } from './state/auth'

const remoteConsoles = consoleLinks('admin')
const signingOut = ref(false)

async function signOut() {
  if (signingOut.value) return
  signingOut.value = true
  try {
    await api.signOut()
    auth.principal = undefined
    location.assign('/iam/login')
  } finally {
    signingOut.value = false
  }
}
</script>

<template>
  <ConsoleShell
    title="IM Admin"
    subtitle="用户管理"
    :username="auth.principal?.username"
    :account-detail="auth.principal ? `#${auth.principal.administratorId}` : undefined"
    :remote-consoles="remoteConsoles"
    :signing-out="signingOut"
    @sign-out="signOut"
  >
    <template #mark><ChatDotRound /></template>
    <template #navigation>
      <RouterLink v-if="can('user:read')" to="/users"><User />用户管理</RouterLink>
    </template>
    <RouterView />
  </ConsoleShell>
</template>
