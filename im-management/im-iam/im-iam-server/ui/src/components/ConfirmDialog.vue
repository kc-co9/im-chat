<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { ElButton, ElDialog } from 'element-plus'

const props = withDefaults(
  defineProps<{
    open: boolean
    title: string
    message: string
    confirmLabel?: string
    cancelLabel?: string
    danger?: boolean
    pending?: boolean
  }>(),
  { confirmLabel: '确认', cancelLabel: '取消', danger: false, pending: false },
)

const emit = defineEmits<{ confirm: []; cancel: [] }>()
const cancelButton = ref<InstanceType<typeof ElButton>>()
let returnFocus: HTMLElement | null = null

function cancel() {
  if (!props.pending) emit('cancel')
}

async function focusCancel() {
  await nextTick()
  cancelButton.value?.$el?.focus()
}

function restoreFocus() {
  returnFocus?.focus()
  returnFocus = null
}

watch(
  () => props.open,
  async (open) => {
    if (open) {
      returnFocus = document.activeElement instanceof HTMLElement ? document.activeElement : null
      await nextTick()
      await nextTick()
      window.setTimeout(() => cancelButton.value?.$el?.focus())
    } else {
      window.setTimeout(restoreFocus)
    }
  },
)
</script>

<template>
  <ElDialog
    v-if="open"
    :model-value="open"
    :append-to-body="false"
    :show-close="false"
    :close-on-click-modal="!pending"
    :close-on-press-escape="!pending"
    width="min(420px, calc(100vw - 28px))"
    @close="cancel"
    @close-auto-focus="restoreFocus"
    @open-auto-focus="focusCancel"
  >
    <section role="alertdialog" aria-modal="true" :aria-label="title" class="confirm-dialog">
      <h2>{{ title }}</h2>
      <p>{{ message }}</p>
    </section>
    <template #footer>
      <ElButton
        ref="cancelButton"
        data-testid="confirm-cancel"
        :disabled="pending"
        @click="cancel"
        >{{ cancelLabel }}</ElButton
      >
      <ElButton
        data-testid="confirm-action"
        :class="{ danger }"
        :type="danger ? 'danger' : 'primary'"
        :loading="pending"
        @click="emit('confirm')"
        >{{ confirmLabel }}</ElButton
      >
    </template>
  </ElDialog>
</template>
