<template>
  <el-dialog
    :model-value="visible"
    width="80%"
    top="5vh"
    class="content-preview-dialog"
    destroy-on-close
    @update:model-value="(v: boolean) => $emit('update:visible', v)"
    @closed="() => $emit('closed')"
  >
    <template #header>
      <div class="preview-content-header">
        <div class="preview-title-wrap">
          <span class="preview-title-text">{{ title }}</span>
        </div>
        <div v-if="sourceUrl" class="preview-source-row">
          <a
            :href="sourceUrl"
            target="_blank"
            rel="noopener noreferrer"
            class="preview-source-url"
          >{{ sourceUrl }}</a>
        </div>
        <div class="preview-content-controls">
          <el-switch
            :model-value="localize"
            :active-text="localizeLabel"
            @update:model-value="(v: boolean) => $emit('update:localize', v)"
            @change="(v: boolean) => $emit('localize-change', v)"
          />
          <el-switch
            :model-value="showSource"
            :active-text="sourceActive"
            :inactive-text="sourceInactive"
            @update:model-value="(v: boolean) => $emit('update:show-source', v)"
          />
        </div>
      </div>
    </template>
    <div v-loading="loading">
      <iframe
        v-if="!showSource"
        class="preview-container preview-html"
        :srcdoc="html || '<p>无正文内容</p>'"
        sandbox="allow-scripts allow-forms allow-popups allow-popups-to-escape-sandbox"
        title="内容预览"
      ></iframe>
      <pre v-else class="preview-container detail-text code-text">{{ source || '无原始内容' }}</pre>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  visible: boolean
  title: string
  html: string
  source: string
  loading?: boolean
  localize: boolean
  showSource: boolean
  sourceUrl?: string
  localizeLabel?: string
  sourceActive?: string
  sourceInactive?: string
}>(), {
  loading: false,
  // 文案以文件页（File.vue）为准
  localizeLabel: 'CSS/JS 本地',
  sourceActive: '源码',
  sourceInactive: '预览',
})

defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'update:localize', v: boolean): void
  (e: 'localize-change', v: boolean): void
  (e: 'update:show-source', v: boolean): void
  (e: 'closed'): void
}>()
</script>

<style scoped>
.preview-content-header {
  display: flex;
  flex-direction: column;
  gap: 8px;
  color: #172b4d;
  font-size: 14px;
  font-weight: 600;
}
.preview-title-wrap {
  min-width: 0;
  width: 100%;
  text-align: center;
}
/* 标题可换行，不省略 */
.preview-title-wrap .preview-title-text {
  word-break: break-all;
  overflow-wrap: anywhere;
}
/* URL 独占一行，居中，超长自动换行 */
.preview-source-row {
  width: 100%;
  min-width: 0;
  font-weight: 400;
  text-align: center;
}
.preview-source-url {
  display: inline;
  color: var(--teal);
  font-size: 12px;
  font-weight: 400;
  line-height: 1.5;
  text-decoration: none;
  word-break: break-all;
  overflow-wrap: anywhere;
}
.preview-source-url:hover {
  text-decoration: underline;
}
/* 宽屏：两个开关竖排 */
.preview-content-controls {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
  margin-right: 28px;
}
.preview-content-controls :deep(.el-switch) {
  --el-switch-on-color: #0f9f9a;
  --el-switch-off-color: #c0c4cc;
  height: 20px;
  font-size: 11px;
}
.preview-content-controls :deep(.el-switch__label) {
  font-size: 10px;
  padding: 0 2px;
  line-height: 20px;
}
.preview-content-controls :deep(.el-switch__core) {
  height: 16px;
  width: 30px;
}
.preview-content-controls :deep(.el-switch__core::after) {
  width: 12px;
  height: 12px;
}
.preview-container {
  max-height: 70vh;
  overflow-y: auto;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 16px;
  background: #fff;
}
.preview-html {
  display: block;
  width: 100%;
  height: 70vh;
  padding: 0;
  line-height: 1.8;
  color: #486581;
  word-break: break-word;
}
.preview-html img { max-width: 100%; height: auto; }
.preview-html iframe { max-width: 100%; }
.code-text {
  white-space: pre-wrap;
  word-break: break-word;
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
  font-size: 13px;
}
.detail-text {
  line-height: 1.8;
  color: #486581;
}

/* 720 断点：开关平铺右对齐，关闭 × 留右上角 */
@media (max-width: 720px) {
  .preview-content-header {
    padding-right: 46px;
  }
  .preview-content-controls {
    width: 100%;
    justify-content: flex-end;
    margin-right: 0;
  }
}
</style>
