<template>
  <el-dialog v-model="visible" title="搜索结果详情" width="min(1100px, calc(100vw - 32px))" top="5vh" destroy-on-close>
    <template #header>
      <div class="detail-title-row">
        <div class="detail-dialog-title">{{ detail?.title || '搜索结果详情' }}</div>
        <div v-if="detail" class="detail-header-meta">
          <div class="detail-url-row">
            <a v-if="detail.url" class="detail-url" :href="detail.url" target="_blank" rel="noopener noreferrer">{{ detail.url }}</a>
            <span v-else class="detail-url">暂无来源地址</span>
          </div>
          <div class="detail-info-row">
            <div class="detail-time-row">
              <span class="detail-time"><span class="detail-time-label">抓取</span>{{ formatTime(detail.crawlTime) || '未知' }}</span>
              <span class="detail-time"><span class="detail-time-label">更新</span>{{ formatTime(detail.updateTime) || '未更新' }}</span>
            </div>
            <div class="detail-badges">
              <span v-if="detail.spiderName" class="meta-tag">{{ detail.spiderName }}</span>
              <span v-if="detail.spiderGroup" class="meta-tag group-tag">{{ detail.spiderGroup }}</span>
              <span class="detail-meta-item">来源：{{ detail.sourceType || '未知' }}</span>
              <span class="detail-meta-item">标签：{{ detail.tags?.length ? detail.tags.join(' / ') : '无' }}</span>
            </div>
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
            fit="contain"
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
.detail-title-row { display: flex; flex-direction: column; align-items: stretch; gap: 8px; padding: 0; }
.detail-header-meta { display: flex; width: 100%; flex-direction: column; align-items: stretch; gap: 8px; min-width: 0; color: #627d98; font-size: 12px; }
.detail-url-row { min-width: 0; padding: 8px 10px; border-radius: 6px; background: #f5f8fa; text-align: center; }
.detail-dialog-title { width: 100%; min-width: 0; color: #102a43; font-size: 20px; font-weight: 700; line-height: 1.4; text-align: center; overflow-wrap: anywhere; }
.detail-url { display: block; color: #087f7d; font-size: 13px; line-height: 1.5; overflow-wrap: anywhere; }
.detail-info-row { display: flex; flex-wrap: wrap; align-items: center; justify-content: center; gap: 6px 16px; }
.detail-time-row { display: flex; flex-wrap: wrap; justify-content: center; gap: 6px 16px; }
.detail-time { display: inline-flex; flex-wrap: wrap; gap: 4px; color: var(--ink-500); font-size: 12px; }
.detail-time-label { color: #627d98; font-weight: 600; }
.detail-badges { display: flex; flex-wrap: wrap; align-items: center; justify-content: center; gap: 6px 8px; }
.detail-meta-item { color: #627d98; }
.meta-tag { background: #f1f5f5; padding: 3px 8px; border-radius: 4px; color: var(--ink-700); font-size: 12px; font-weight: 500; }
.group-tag { background: #d9f5ef; color: var(--teal-dark); }
.detail-images { display: flex; flex-direction: column; gap: 12px; align-items: center; padding: 4px 0; }
.detail-image { width: 100%; max-width: 480px; height: auto; border-radius: 8px; border: 1px solid #e3e8ee; }
.detail-text { white-space: pre-wrap; line-height: 1.8; color: var(--ink-900); max-height: 42vh; overflow-y: auto; }
.detail-content-preview { padding: 4px 0; }
.empty { text-align: center; color: var(--ink-500); padding: 40px 0; font-size: 14px; }

@media (max-width: 720px) {
  .detail-images { gap: 8px; }
}
</style>
