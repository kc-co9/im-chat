<script setup lang="ts">
import { SwitchButton } from "@element-plus/icons-vue";
import { ElButton, ElTooltip } from "element-plus";

defineProps<{
  title: string;
  subtitle: string;
  navigationLabel?: string;
  username?: string;
  accountDetail?: string;
  remoteConsoles: Array<{ key: string; name: string; location: string }>;
  signingOut?: boolean;
}>();

defineEmits<{ signOut: [] }>();
</script>

<template>
  <div class="console-shell">
    <aside class="console-sidebar">
      <header class="console-brand">
        <span class="console-brand__mark"><slot name="mark" /></span>
        <div>
          <strong>{{ title }}</strong
          ><small>{{ subtitle }}</small>
        </div>
      </header>
      <nav class="console-nav" aria-label="主导航">
        <span class="console-nav__label">{{
          navigationLabel || "当前控制台"
        }}</span>
        <slot name="navigation" />
        <div v-if="remoteConsoles.length" class="console-nav__remote">
          <span class="console-nav__label">其他控制台</span>
          <a
            v-for="console in remoteConsoles"
            :key="console.key"
            :href="console.location"
          >
            {{ console.name }}
          </a>
        </div>
        <div v-if="remoteConsoles.length" class="console-mobile-consoles">
          <a
            v-for="console in remoteConsoles"
            :key="`mobile-${console.key}`"
            :href="console.location"
          >
            {{ console.name }}
          </a>
        </div>
      </nav>
      <footer class="console-account">
        <div>
          <strong>{{ username || "管理账号" }}</strong
          ><small>{{ accountDetail }}</small>
        </div>
        <ElTooltip content="退出登录" placement="top">
          <ElButton
            data-testid="sign-out"
            :icon="SwitchButton"
            circle
            :loading="signingOut"
            aria-label="退出登录"
            @click="$emit('signOut')"
          />
        </ElTooltip>
      </footer>
    </aside>
    <main class="console-workspace"><slot /></main>
  </div>
</template>
