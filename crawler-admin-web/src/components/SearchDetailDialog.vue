<template>
  <el-dialog v-model="visible" title="搜索结果详情" width="min(800px, calc(100vw - 32px))" top="5vh" destroy-on-close>
    <template #header>
      <div class="detail-title-row">
        <div class="detail-dialog-title">{{ detail?.title || '搜索结果详情' }}</div>
        <div v-if="detail" class="detail-header-meta">
          <div class="detail-url-row">
            <a v-if="detail.url" class="detail-url" :href="detail.url" target="_blank" rel="noopener noreferrer">{{ detail.url }}</a>
            <span v-else class="detail-url">暂无来源地址</span>
            <span class="detail-time">抓取：{{ formatTime(detail.crawlTime) || '未知' }} | 更新：{{ formatTime(detail.updateTime) || '未更新' }}</span>
          </div>
          <div class="detail-badges">
            <span v-if="detail.spiderName" class="meta-tag">{{ detail.spiderName }}</span>
            <span v-if="detail.spiderGroup" class="meta-tag group-tag">{{ detail.spiderGroup }}</span>
            <span class="detail-meta-item">来源：{{ detail.sourceType || '未知' }}</span>
            <span class="detail-meta-item">标签：{{ detail.tags?.length ? detail.tags.join(' / ') : '无' }}</span>
          </div>
        </div>
      </div>
    </template>
    <div v-loading="loading" class="detail-dialog-body">
      <div v-if="detail" class="detail-content">
        <div class="detail-text detail-content-preview">{{ detail.content || '无正文内容' }}</div>
        <div v-if="detail.images && detail.images.length" class="detail-images">
          <el-image
            v-for="(img, idx) in detail.images"
            :key="idx"
            :src="imageUrl(img)"
            :preview-src-list="detail.images.map(imageUrl)"
            :initial-index="idx"
            fit="cover"
            class="detail-image"
            preview-teleported
            hide-on-click-modal
          />
        </div>
      </div>
      <div v-else class="empty">暂无详情</div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  modelValue: boolean
  loading: boolean
  detail: any
  imageUrl: (objectName: string) => string
  formatTime: (value: string) => string
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value)
})
</script>

<style scoped>
.detail-dialog-body { min-height: 220px; }
.detail-content { display: flex; flex-direction: column; gap: 18px; }
.detail-title-row { display: flex; align-items: flex-start; gap: 12px; padding: 0; }
.detail-header-meta { display: flex; flex: 1 1 58%; flex-wrap: wrap; align-items: center; justify-content: flex-end; gap: 4px 8px; min-width: 0; color: #627d98; font-size: 12px; }
.detail-url-row { display: flex; flex: 1 1 320px; align-items: baseline; flex-wrap: wrap; justify-content: flex-end; gap: 10px; }
.detail-dialog-title { flex: 1 1 42%; min-width: 0; color: #102a43; font-size: 20px; font-weight: 700; line-height: 1.4; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.detail-url { color: #087f7d; font-size: 13px; word-break: break-all; }
.detail-time { color: #909399; font-size: 12px; white-space: nowrap; }
.detail-badges { display: flex; flex-wrap: wrap; align-items: center; justify-content: flex-end; gap: 6px 8px; }
.detail-meta-item { color: #627d98; }
.meta-tag { background: #f1f3f4; padding: 3px 8px; border-radius: 4px; color: #5f6368; font-size: 12px; font-weight: 500; }
.group-tag { background: #e8f0fe; color: #1967d2; }
.detail-images { display: flex; flex-wrap: wrap; gap: 12px; justify-content: center; padding: 4px 0; }
.detail-image { width: clamp(160px, 22vw, 240px); height: clamp(110px, 16vw, 160px); border-radius: 8px; border: 1px solid #eee; overflow: hidden; }
.detail-text { white-space: pre-wrap; line-height: 1.8; color: #303133; max-height: 42vh; overflow-y: auto; }
.detail-content-preview { padding: 4px 0; }
.empty { text-align: center; color: #999; padding: 40px 0; font-size: 14px; }

@media (max-width: 720px) {
  .detail-title-row { flex-direction: column; }
  .detail-dialog-title { flex-basis: auto; width: 100%; white-space: normal; }
  .detail-header-meta { justify-content: flex-start; }
  .detail-badges { justify-content: flex-start; }
  .detail-images { gap: 8px; }
  .detail-image { width: calc(50% - 4px); height: 120px; }
}
</style>
