<script setup lang="ts">
import { ElAlert, ElButton, ElEmpty, ElSkeleton } from 'element-plus'

withDefaults(
  defineProps<{
    loading: boolean
    error?: string
    empty: boolean
    hasData: boolean
    emptyText: string
    retryTestId?: string
  }>(),
  { error: '' },
)

defineEmits<{ retry: [] }>()
</script>

<template>
  <div v-if="loading && !hasData" class="data-placeholder" aria-busy="true">
    <ElSkeleton :rows="5" animated />
  </div>
  <div v-else-if="error && !hasData" class="data-placeholder" role="alert">
    <ElEmpty description="加载失败">
      <p>{{ error }}</p>
      <ElButton :data-testid="retryTestId || 'retry'" @click="$emit('retry')">重试</ElButton>
    </ElEmpty>
  </div>
  <ElEmpty v-else-if="empty" :description="emptyText">
    <slot name="empty-action" />
  </ElEmpty>
  <div v-else>
    <ElAlert v-if="error" :title="error" type="error" show-icon :closable="false">
      <template #default>
        <ElButton :data-testid="retryTestId || 'retry'" link @click="$emit('retry')">重试</ElButton>
      </template>
    </ElAlert>
    <slot />
  </div>
</template>
