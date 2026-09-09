<script setup lang="ts">
import { ref } from 'vue'
import { RouterLink, RouterView } from 'vue-router'
import { Connection, Key, Lock, User } from '@element-plus/icons-vue'
import ToastRegion from './components/ToastRegion.vue'
import ConsoleShell from './components/ConsoleShell.vue'
import { consoleLinks } from './config/consoleLinks'
import { iamApi } from './api/iam'
import { auth, can } from './state/auth'

const signingOut = ref(false)
const remoteConsoles = consoleLinks('iam')

async function signOut() {
  if (signingOut.value) return
  signingOut.value = true
  try {
    await iamApi.signOut()
    auth.principal = undefined
    location.assign('/login?logout')
  } finally {
    signingOut.value = false
  }
}
</script>

<template>
  <ConsoleShell
    title="IM IAM"
    subtitle="统一权限中心"
    navigation-label="权限管理"
    username="管理账号"
    :account-detail="auth.principal ? `管理员 #${auth.principal.administratorId}` : undefined"
    :remote-consoles="remoteConsoles"
    :signing-out="signingOut"
    @sign-out="signOut"
  >
    <template #mark><Lock /></template>
    <template #navigation>
      <RouterLink v-if="can('iam:administrator:read')" to="/permissions/administrators">
        <User />管理账号
      </RouterLink>
      <RouterLink v-if="can('iam:application:read')" to="/permissions/applications">
        <Key />应用管理
      </RouterLink>
      <RouterLink v-if="can('iam:session:read')" to="/permissions/sessions">
        <Connection />在线会话
      </RouterLink>
    </template>
    <RouterView />
  </ConsoleShell>
  <ToastRegion />
</template>
