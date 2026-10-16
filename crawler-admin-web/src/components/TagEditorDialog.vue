<template>
  <el-dialog v-model="visible" title="编辑标签" width="480px" destroy-on-close>
    <div class="tag-editor-content">
      <el-checkbox-group v-model="selection" class="tag-editor-options">
        <el-checkbox
          v-for="tag in availableTags"
          :key="tag"
          :label="tag"
          :value="tag"
          border
        />
      </el-checkbox-group>
      <div class="tag-editor-actions">
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { searchUpdateTags } from '@/api'

const props = defineProps<{
  modelValue: boolean
  row: any
  tagOptions: any[]
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'saved', tags: string[]): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})
const selection = ref<string[]>([])
const availableTags = computed(() => {
  const options = props.tagOptions
    .map(option => String(option.label || '').trim())
    .filter(Boolean)
  return [...new Set([...options, ...selection.value])]
})

watch(
  () => [props.modelValue, props.row] as const,
  ([isVisible, row]) => {
    if (isVisible && row) selection.value = [...(row.tags || [])]
  },
  { immediate: true }
)

const save = async () => {
  if (!props.row) return
  try {
    await searchUpdateTags(props.row.id, selection.value)
    emit('saved', [...selection.value])
    ElMessage.success('标签已更新')
    visible.value = false
  } catch {
    ElMessage.error('标签更新失败')
  }
}
</script>

<style scoped>
.tag-editor-content { display: flex; flex-direction: column; gap: 20px; padding: 8px 0; }
.tag-editor-options { display: flex; flex-wrap: wrap; gap: 10px; max-height: 45vh; overflow-y: auto; }
.tag-editor-options :deep(.el-checkbox) { margin: 0; }
.tag-editor-actions { display: flex; justify-content: flex-end; gap: 8px; }

</style>
