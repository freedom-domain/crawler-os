<template>
  <div ref="wrapperRef" class="history-dropdown-wrapper">
    <slot />
    <Transition name="history-fade">
      <div v-if="open && !keyword" class="history-popover">
        <div class="history-panel">
          <div class="history-header">
            <span class="history-title">搜索历史</span>
            <button v-if="history.length" class="history-clear" type="button" @click="clear">清空</button>
          </div>
          <div class="history-list">
            <div v-for="item in history" :key="item.id" class="history-item">
              <span class="history-icon" aria-hidden="true">
                <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <circle cx="12" cy="12" r="9"/>
                  <path d="M12 7v5l3 2"/>
                </svg>
              </span>
              <span class="history-keyword">{{ item.keyword }}</span>
              <span v-if="item.createTime" class="history-time">{{ formatTime(item.createTime) }}</span>
              <button class="history-remove" type="button" aria-label="删除此搜索历史" @click.stop="remove(item.keyword)">
                <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                  <path d="M18 6 6 18M6 6l12 12"/>
                </svg>
              </button>
              <button class="history-select" type="button" aria-label="使用此搜索历史" @click="select(item.keyword)"></button>
            </div>
            <div v-if="history.length === 0" class="history-empty">
              <span class="history-empty-icon" aria-hidden="true">
                <svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                  <circle cx="11" cy="11" r="7"/>
                  <path d="m20 20-3.5-3.5"/>
                </svg>
              </span>
              <span>暂无搜索历史</span>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { clearSearchHistory, deleteSearchHistory, searchHistory, syncSearchHistory } from '@/api'

const LOCAL_HISTORY_KEY = 'crawler-search-history'

const emit = defineEmits<{
  (event: 'select', keyword: string): void
  (event: 'close'): void
}>()

const history = ref<any[]>([])
const wrapperRef = ref<HTMLElement | null>(null)
const load = async () => {
  if (!props.authenticated) {
    history.value = readLocalHistory().map((item, index) => ({ id: `local-${index}`, keyword: item.keyword, createTime: item.time }))
    return
  }
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
  authenticated: boolean
}>()

const handleVisibleChange = (open: boolean) => {
  if (open && !props.keyword) {
    load()
  }
}

const readLocalHistory = () => {
  try {
    const values = JSON.parse(localStorage.getItem(LOCAL_HISTORY_KEY) || '[]')
    if (!Array.isArray(values)) return []
    return values
      .map((value): { keyword: string; time: number } | null => {
        if (typeof value === 'string' && value.trim()) {
          return { keyword: value.trim(), time: Date.now() }
        }
        if (value && typeof value === 'object' && typeof value.keyword === 'string' && value.keyword.trim()) {
          return { keyword: value.keyword.trim(), time: typeof value.time === 'number' ? value.time : Date.now() }
        }
        return null
      })
      .filter((item): item is { keyword: string; time: number } => item !== null)
  } catch {
    return []
  }
}

const syncLocalHistory = async () => {
  if (!props.authenticated) return
  const localHistory = readLocalHistory()
  if (!localHistory.length) return
  try {
    await syncSearchHistory(localHistory.map(item => item.keyword))
    localStorage.removeItem(LOCAL_HISTORY_KEY)
  } catch {
    // Keep local history for the next successful login sync.
  }
}

const formatTime = (value: string | number | Date) => {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  const minute = 60 * 1000
  const hour = 60 * minute
  const day = 24 * hour
  if (diff < minute) return '刚刚'
  if (diff < hour) return `${Math.floor(diff / minute)} 分钟前`
  if (diff < day && date.getDate() === now.getDate()) return `${Math.floor(diff / hour)} 小时前`
  if (diff < day) return '昨天'
  if (diff < 7 * day) return `${Math.floor(diff / day)} 天前`
  const month = date.getMonth() + 1
  const dayOfMonth = date.getDate()
  if (date.getFullYear() === now.getFullYear()) return `${month}月${dayOfMonth}日`
  return `${date.getFullYear()}年${month}月${dayOfMonth}日`
}

const select = (keyword: string) => {
  emit('select', keyword)
  emit('close')
}

const remove = async (keyword: string) => {
  try {
    if (props.authenticated) {
      await deleteSearchHistory(keyword)
    } else {
      const values = readLocalHistory().filter(item => item.keyword !== keyword)
      localStorage.setItem(LOCAL_HISTORY_KEY, JSON.stringify(values))
    }
    history.value = history.value.filter(item => item.keyword !== keyword)
  } catch {
    ElMessage.error('删除搜索历史失败')
  }
}

const clear = async () => {
  try {
    if (props.authenticated) {
      await clearSearchHistory()
    } else {
      localStorage.removeItem(LOCAL_HISTORY_KEY)
    }
    history.value = []
    ElMessage.success('搜索历史已清空')
  } catch {
    ElMessage.error('清空搜索历史失败')
  }
}

watch(() => props.open, handleVisibleChange)
watch(() => props.authenticated, syncLocalHistory, { immediate: true })

const handleDocumentPointerDown = (event: PointerEvent) => {
  if (props.open && wrapperRef.value && !wrapperRef.value.contains(event.target as Node)) {
    emit('close')
  }
}

onMounted(() => document.addEventListener('pointerdown', handleDocumentPointerDown))
onUnmounted(() => document.removeEventListener('pointerdown', handleDocumentPointerDown))
</script>

<style scoped>
.history-dropdown-wrapper {
  position: relative;
  flex: 1;
  width: 100%;
  min-width: 0;
}

.history-popover {
  position: absolute;
  top: calc(100% - 2px);
  left: 0;
  z-index: 30;
  width: 100%;
  max-width: 100%;
  box-sizing: border-box;
  padding: 0;
  border: 1px solid #e2e5e9;
  border-top: 0;
  border-radius: 0 0 16px 16px;
  background: #fff;
  box-shadow: 0 8px 24px rgba(32, 33, 36, 0.14);
  transform-origin: top center;
  overflow: hidden;
}

.history-fade-enter-active,
.history-fade-leave-active {
  transition: opacity 0.16s ease, transform 0.16s ease;
}

.history-fade-enter-from,
.history-fade-leave-to {
  opacity: 0;
  transform: translateY(-6px) scaleY(0.96);
}

.history-panel {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.history-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px 8px;
  border-bottom: 1px solid #f1f3f4;
}

.history-title {
  font-size: 13px;
  font-weight: 600;
  color: #5f6368;
  letter-spacing: 0.3px;
}

.history-clear {
  border: 0;
  background: transparent;
  cursor: pointer;
  padding: 4px 10px;
  border-radius: 12px;
  color: #9aa0a6;
  font-size: 12px;
  line-height: 1;
  transition: background 0.15s ease, color 0.15s ease;
}

.history-clear:hover {
  background: #f1f3f4;
  color: #ea4335;
}

.history-list {
  display: flex;
  flex-direction: column;
  max-height: 320px;
  overflow-y: auto;
  padding: 6px 0;
}

.history-list::-webkit-scrollbar {
  width: 6px;
}

.history-list::-webkit-scrollbar-thumb {
  background: #dadce0;
  border-radius: 3px;
}

.history-item {
  display: flex;
  align-items: center;
  gap: 10px;
  position: relative;
  padding: 9px 16px;
  border-radius: 0;
  transition: background 0.12s ease;
}

.history-select {
  position: absolute;
  inset: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
}

.history-remove {
  position: relative;
  z-index: 1;
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: #9aa0a6;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.12s ease, background 0.12s ease, color 0.12s ease;
}

.history-item:hover .history-remove,
.history-remove:focus-visible {
  opacity: 1;
}

.history-remove:hover {
  background: #f1f3f4;
  color: #ea4335;
}

.history-icon {
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #f1f3f4;
  color: #70757a;
}

.history-keyword {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
  color: #202124;
}

.history-time {
  flex: 0 0 auto;
  font-size: 12px;
  color: #9aa0a6;
}

.history-item:hover {
  background: #f8f9fa;
}

.history-item:hover .history-icon {
  background: #e8f0fe;
  color: #1a73e8;
}

.history-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 28px 16px;
  color: #9aa0a6;
  font-size: 13px;
}

.history-empty-icon {
  color: #dadce0;
}
</style>
