<script setup lang="ts">
import { ref } from "vue";
import { RouterLink, RouterView } from "vue-router";
import {
  Connection,
  DataAnalysis,
  Monitor,
  Operation,
  SetUp,
} from "@element-plus/icons-vue";
import { iamApi } from "./api/iam";
import ConsoleShell from "./components/ConsoleShell.vue";
import { consoleLinks } from "./config/consoleLinks";
import { auth, can } from "./state/auth";

const signingOut = ref(false);
const remoteConsoles = consoleLinks("monitor");

async function logout() {
  if (signingOut.value) return;
  signingOut.value = true;
  try {
    await iamApi.logout();
    auth.principal = undefined;
    location.assign("/iam/login");
  } finally {
    signingOut.value = false;
  }
}
</script>

<template>
  <ConsoleShell
    title="IM Monitor"
    subtitle="运行监控"
    :username="auth.principal?.username"
    :account-detail="auth.principal?.appKey"
    :remote-consoles="remoteConsoles"
    :signing-out="signingOut"
    @sign-out="logout"
  >
    <template #mark><Monitor /></template>
    <template #navigation>
      <RouterLink v-if="can('monitor:overview:read')" to="/"
        ><DataAnalysis />集群总览</RouterLink
      >
      <RouterLink v-if="can('monitor:broker:read')" to="/brokers"
        ><SetUp />Broker</RouterLink
      >
      <RouterLink v-if="can('monitor:gateway:read')" to="/gateways"
        ><Operation />Gateway</RouterLink
      >
      <RouterLink v-if="can('monitor:connection:read')" to="/connections"
        ><Connection />连接路由</RouterLink
      >
      <RouterLink v-if="can('monitor:diagnostic:read')" to="/diagnostics"
        ><Monitor />诊断记录</RouterLink
      >
    </template>
    <RouterView />
  </ConsoleShell>
</template>
