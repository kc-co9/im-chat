<script setup lang="ts">
import { ElAlert } from 'element-plus'
import { dismissToast, toasts } from '../state/feedback'
</script>

<template>
  <div class="toast-region" aria-live="polite" aria-atomic="false">
    <ElAlert
      v-for="toast in toasts"
      :key="toast.id"
      :title="toast.message"
      :type="toast.tone === 'error' ? 'error' : 'success'"
      show-icon
      @close="dismissToast(toast.id)"
    />
  </div>
</template>
