<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, RouterView } from 'vue-router'
import { DocumentChecked } from '@element-plus/icons-vue'
import { api } from './api/audit'
import ConsoleShell from './components/ConsoleShell.vue'
import { consoleLinks } from './config/consoleLinks'
import { auth, can } from './state/auth'

const remoteConsoles = consoleLinks('audit')
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
    title="IM Audit"
    subtitle="集中审计中心"
    :username="auth.principal?.username"
    :account-detail="auth.principal?.appKey"
    :remote-consoles="remoteConsoles"
    :signing-out="signingOut"
    @sign-out="signOut"
  >
    <template #mark><DocumentChecked /></template>
    <template #navigation>
      <RouterLink v-if="can('audit:read')" to="/audits"> <DocumentChecked />审计记录 </RouterLink>
    </template>
    <RouterView />
  </ConsoleShell>
</template>
