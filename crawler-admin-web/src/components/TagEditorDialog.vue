<template>
  <el-dialog v-model="visible" title="编辑标签" width="480px" destroy-on-close>
    <div class="tag-editor-row">
      <el-select
        v-model="selection"
        multiple
        filterable
        allow-create
        default-first-option
        placeholder="选择或输入标签"
        class="tag-editor-select"
        :teleported="true"
        popper-class="tag-editor-popper"
      >
        <el-option
          v-for="option in tagOptions"
          :key="option.id"
          :label="option.label"
          :value="option.label"
        />
      </el-select>
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
.tag-editor-row { display: flex; align-items: center; gap: 10px; padding: 10px 0; }
.tag-editor-select { min-width: 0; flex: 1; }
.tag-editor-actions { display: flex; flex-shrink: 0; gap: 8px; }
:global(.tag-editor-popper) { z-index: 3100 !important; }
@media (max-width: 520px) {
  .tag-editor-row { align-items: stretch; flex-direction: column; }
  .tag-editor-actions { justify-content: flex-end; }
}
</style>
