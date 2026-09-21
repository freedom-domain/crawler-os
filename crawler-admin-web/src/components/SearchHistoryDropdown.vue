<template>
  <el-popover
    v-model:visible="visible"
    placement="bottom-start"
    :width="280"
    trigger="manual"
    @after-leave="emit('close')"
  >
    <div class="history-panel">
      <div class="history-title">搜索历史</div>
      <button v-for="item in history" :key="item.id" class="history-item" type="button" @click="select(item.keyword)">
        {{ item.keyword }}
      </button>
      <div v-if="history.length === 0" class="history-empty">暂无搜索历史</div>
      <button v-if="history.length" class="history-clear" type="button" @click="clear">清空历史</button>
    </div>
    <template #reference><slot /></template>
  </el-popover>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { clearSearchHistory, searchHistory } from '@/api'

const emit = defineEmits<{
  (event: 'select', keyword: string): void
  (event: 'close'): void
}>()

const history = ref<any[]>([])
const visible = ref(false)

const load = async () => {
  try {
    const res: any = await searchHistory()
    history.value = res?.data || []
  } catch {
    history.value = []
  }
}

const props = defineProps<{
  open: boolean
  keyword: string
}>()

const handleVisibleChange = (open: boolean) => {
  visible.value = open && !props.keyword
  if (visible.value) load()
}

const select = (keyword: string) => {
  emit('select', keyword)
  emit('close')
}

const clear = async () => {
  try {
    await clearSearchHistory()
    history.value = []
    ElMessage.success('搜索历史已清空')
  } catch {
    ElMessage.error('清空搜索历史失败')
  }
}

watch(() => props.open, handleVisibleChange)
watch(() => props.keyword, (keyword) => {
  if (keyword) visible.value = false
})
</script>

<style scoped>
.history-panel {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

:deep(.el-popover__reference-wrapper) {
  display: contents;
}

.history-title {
  padding: 2px 8px 6px;
  color: #606266;
  font-size: 12px;
}

.history-item,
.history-clear {
  border: 0;
  background: transparent;
  cursor: pointer;
  padding: 7px 8px;
  text-align: left;
  color: #303133;
}

.history-item:hover,
.history-clear:hover {
  background: #f5f7fa;
}

.history-empty {
  padding: 8px;
  color: #909399;
  font-size: 13px;
}

.history-clear {
  border-top: 1px solid #ebeef5;
  color: #409eff;
  font-size: 12px;
}
</style>
