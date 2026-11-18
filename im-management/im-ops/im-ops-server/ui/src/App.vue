<script setup lang="ts">
import { ref } from "vue";
import { RouterLink, RouterView } from "vue-router";
import {
  Connection,
  DataAnalysis,
  Document,
  Operation,
  Platform,
  SetUp,
} from "@element-plus/icons-vue";
import { iamApi } from "./api/iam";
import ConsoleShell from "./components/ConsoleShell.vue";
import { consoleLinks } from "./config/consoleLinks";
import { auth, can } from "./state/auth";

const signingOut = ref(false);
const remoteConsoles = consoleLinks("ops");

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
    title="IM Ops"
    subtitle="运行监控"
    :username="auth.principal?.username"
    :account-detail="auth.principal?.appKey"
    :remote-consoles="remoteConsoles"
    :signing-out="signingOut"
    @sign-out="logout"
  >
    <template #mark><Platform /></template>
    <template #navigation>
      <RouterLink v-if="can('ops:overview:read')" to="/"
        ><DataAnalysis />集群总览</RouterLink
      >
      <RouterLink v-if="can('ops:broker:read')" to="/brokers"
        ><SetUp />Broker</RouterLink
      >
      <RouterLink v-if="can('ops:gateway:read')" to="/gateways"
        ><Operation />Gateway</RouterLink
      >
      <RouterLink v-if="can('ops:connection:read')" to="/connections"
        ><Connection />连接路由</RouterLink
      >
      <RouterLink v-if="can('ops:diagnostic:read')" to="/diagnostics"
        ><Document />诊断记录</RouterLink
      >
    </template>
    <RouterView />
  </ConsoleShell>
</template>
