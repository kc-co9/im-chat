<script setup lang="ts">
import { computed } from 'vue'
import { ElTag } from 'element-plus'

const props = defineProps<{ status: string }>()
type TagTone = 'success' | 'warning' | 'danger' | 'info'

const presentation = computed<{ text: string; tone: TagTone }>(() => {
  if (['ACTIVE', 'UP', 'SUCCESS'].includes(props.status)) return { text: '正常', tone: 'success' }
  if (props.status === 'DISABLED') return { text: '已停用', tone: 'warning' }
  if (['REVOKED', 'FAILURE', 'DOWN'].includes(props.status))
    return { text: '已失效', tone: 'danger' }
  return { text: props.status, tone: 'info' }
})
</script>

<template>
  <ElTag :type="presentation.tone" effect="light">
    {{ presentation.text }}
  </ElTag>
</template>
