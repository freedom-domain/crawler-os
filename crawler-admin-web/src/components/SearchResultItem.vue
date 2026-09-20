<template>
  <div class="result-item">
    <a class="result-url" :href="row.url" target="_blank" rel="noopener noreferrer">{{ row.url }}</a>
    <h3 class="result-title" @click.prevent="$emit('preview', row)" v-html="row.titleHl || row.title"></h3>
    <div class="result-content-line">
      <p class="result-content" v-html="row.contentHl || (row.content?.substring(0, 200) + '...')"></p>
      <span
        class="result-detail-link"
        role="button"
        tabindex="0"
        @click="$emit('detail', row)"
        @keydown.enter.prevent="$emit('detail', row)"
        @keydown.space.prevent="$emit('detail', row)"
      >
        详情
      </span>
    </div>
    <slot name="images" />
    <slot name="tags" />
    <slot name="meta" />
  </div>
</template>

<script setup lang="ts">
defineProps<{
  row: any
}>()

defineEmits<{ 
  (e: 'preview', row: any): void
  (e: 'detail', row: any): void
}>()
</script>

<style scoped>
.result-item {
  padding: 20px 0;
  border-bottom: 1px solid #f1f3f4;
}

.result-item:last-child {
  border-bottom: none;
}

.result-url {
  display: block;
  color: #006621;
  font-size: 13px;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-decoration: none;
}

.result-url:hover {
  text-decoration: underline;
}

.result-title {
  color: #1a0dab;
  font-size: 18px;
  font-weight: 400;
  margin: 0 0 6px 0;
  line-height: 1.4;
  cursor: pointer;
  transition: color 0.2s ease, text-shadow 0.2s ease, transform 0.2s ease;
}

.result-title:hover {
  color: #0d47a1;
  text-shadow: 0 1px 0 rgba(13, 71, 161, 0.08);
}

.result-title:active {
  color: #0b3c8a;
  transform: translateY(1px);
}

.result-title :deep(em) {
  font-style: normal;
  color: #1a0dab;
  font-weight: 700;
}

.result-content-line {
  display: inline;
  line-height: 1.6;
  margin: 0 0 8px;
}

.result-content {
  color: #545454;
  font-size: 14px;
  line-height: 1.6;
  margin: 0;
  display: inline;
}

.result-detail-link {
  display: inline;
  color: #1a73e8;
  font-size: 13px;
  cursor: pointer;
  margin-left: 4px;
  white-space: nowrap;
  vertical-align: baseline;
}

.result-detail-link:hover {
  text-decoration: underline;
}

.result-content :deep(em) {
  font-style: normal;
  font-weight: 700;
}

.result-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 0 0 10px 0;
}

.result-thumb {
  width: 72px;
  height: 72px;
  border-radius: 4px;
  border: 1px solid #eee;
  cursor: pointer;
  display: block;
}

.result-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 0 0 8px 0;
}

.tag-item {
  cursor: pointer;
}

.result-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: #999;
  flex-wrap: wrap;
}

.meta-tag {
  background: #f0f0f0;
  padding: 2px 8px;
  border-radius: 3px;
  color: #666;
}

.spider-tag {
  cursor: pointer;
}

.group-tag {
  background: #e8f0fe;
  color: #1967d2;
}

.update-time {
  color: #999;
}

@media (max-width: 460px) {
  .result-url,
  .result-title,
  .result-content,
  .result-detail-link {
    word-break: break-word;
    overflow-wrap: anywhere;
  }
  .result-item {
    padding: 16px 0;
  }
  .result-thumb {
    width: 64px;
    height: 64px;
  }
}
</style>
