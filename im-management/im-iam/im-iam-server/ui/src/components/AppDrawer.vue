<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { Close } from '@element-plus/icons-vue'
import { ElButton, ElDrawer } from 'element-plus'
import ConfirmDialog from './ConfirmDialog.vue'

const props = withDefaults(
  defineProps<{
    open: boolean
    title: string
    description?: string
    dirty?: boolean
    pending?: boolean
  }>(),
  { description: '', dirty: false, pending: false },
)

const emit = defineEmits<{ close: [] }>()
const panel = ref<HTMLElement>()
const discardConfirmationOpen = ref(false)
let returnFocus: HTMLElement | null = null

function focusFirstControl() {
  const focusable = panel.value?.querySelector<HTMLElement>(
    'input:not([disabled]), select:not([disabled]), textarea:not([disabled]), button:not([disabled]), a[href]',
  )
  ;(focusable ?? panel.value)?.focus()
}

async function handleOpenAutoFocus() {
  await nextTick()
  focusFirstControl()
}

function requestClose() {
  if (props.pending) return
  if (props.dirty) {
    discardConfirmationOpen.value = true
    return
  }
  emit('close')
}

function discardChanges() {
  discardConfirmationOpen.value = false
  emit('close')
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
      window.setTimeout(focusFirstControl)
      return
    }
    discardConfirmationOpen.value = false
    await nextTick()
    returnFocus?.focus()
    returnFocus = null
  },
)

onBeforeUnmount(() => returnFocus?.focus())
</script>

<template>
  <div v-if="open" data-testid="drawer-layer">
    <ElDrawer
      :model-value="open"
      :append-to-body="false"
      :close-on-press-escape="false"
      :before-close="requestClose"
      :show-close="false"
      size="min(560px, 100vw)"
      destroy-on-close
      @open-auto-focus="handleOpenAutoFocus"
      @close-auto-focus="restoreFocus"
    >
      <template #header>
        <div class="drawer-header">
          <div>
            <h2>{{ title }}</h2>
            <p v-if="description">{{ description }}</p>
          </div>
          <ElButton
            data-testid="drawer-close"
            :icon="Close"
            circle
            :disabled="pending"
            aria-label="关闭"
            @click="requestClose"
          />
        </div>
      </template>
      <div ref="panel" class="drawer-panel" data-testid="drawer-panel" tabindex="-1">
        <div class="drawer-body"><slot /></div>
      </div>
      <template v-if="$slots.footer" #footer>
        <div class="drawer-footer"><slot name="footer" :close="requestClose" /></div>
      </template>
    </ElDrawer>
    <ConfirmDialog
      :open="discardConfirmationOpen"
      title="放弃未保存的修改？"
      message="关闭后，本次填写或选择的内容将不会保存。"
      cancel-label="继续编辑"
      confirm-label="放弃修改"
      danger
      @cancel="discardConfirmationOpen = false"
      @confirm="discardChanges"
    />
  </div>
</template>
