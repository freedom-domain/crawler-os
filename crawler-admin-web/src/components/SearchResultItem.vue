<template>
  <div class="result-item">
    <a class="result-url" :href="row.url" target="_blank" rel="noopener noreferrer">{{ row.url }}</a>
    <h3 class="result-title" @click.prevent="$emit('preview', row)">
      <span v-html="row.titleHl || row.title"></span>
      <button
        v-if="row.images?.length"
        type="button"
        class="result-image-count"
        @click.stop="$emit('images', row)"
      >图片 <strong>{{ row.images.length }}</strong> 张</button>
    </h3>
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
  (e: 'images', row: any): void
}>()
</script>

<style scoped>
.result-item {
  padding: 22px 0;
  border-bottom: 1px solid #f1f5f5;
}

.result-item:last-child {
  border-bottom: none;
}

.result-url {
  display: block;
  width: fit-content;
  max-width: 100%;
  color: var(--teal-dark);
  font-size: 13px;
  font-weight: 500;
  margin-bottom: 5px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-decoration: none;
}

.result-url:hover {
  color: var(--teal);
  text-decoration: underline;
}

.result-title {
  display: block;
  width: fit-content;
  max-width: 100%;
  color: var(--ink-950);
  font-size: 18px;
  font-weight: 700;
  margin: 0 0 7px 0;
  line-height: 1.42;
  cursor: pointer;
  transition: color 0.2s ease, text-shadow 0.2s ease, transform 0.2s ease;
}

.result-image-count {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-family: inherit;
  margin-left: 10px;
  padding: 3px 10px;
  border: 1px solid #b7e7dc;
  border-radius: 999px;
  background: linear-gradient(135deg, #eefaf7 0%, #e4f5f2 100%);
  color: #087f70;
  font-size: 12px;
  font-weight: 600;
  line-height: inherit;
  vertical-align: middle;
  white-space: nowrap;
  cursor: pointer;
  transition: background .18s ease, box-shadow .18s ease, transform .18s ease;
}

.result-image-count:hover {
  background: linear-gradient(135deg, #e2f5f0 0%, #d7f1e9 100%);
  box-shadow: 0 4px 10px rgba(15, 129, 124, .16);
  transform: translateY(-1px);
}

.result-image-count:focus-visible {
  outline: 2px solid #087f70;
  outline-offset: 2px;
}

.result-image-count strong {
  color: #075e55;
  font-size: 13px;
  font-weight: 800;
}

.result-title:hover {
  color: var(--teal-dark);
  text-shadow: 0 1px 0 rgba(15, 159, 154, 0.08);
}

.result-title:active {
  color: var(--teal);
  transform: translateY(1px);
}

.result-title :deep(em) {
  font-style: normal;
  color: var(--teal);
  font-weight: 800;
}

.result-content-line {
  display: inline;
  line-height: 1.6;
  margin: 0 0 8px;
}

.result-content {
  color: #5e6c84;
  font-size: 14px;
  line-height: 1.7;
  margin: 0;
  display: inline;
}

.result-detail-link {
  display: inline;
  color: var(--teal);
  font-size: 13px;
  cursor: pointer;
  margin-left: 4px;
  white-space: nowrap;
  vertical-align: baseline;
}

.result-detail-link:hover {
  color: var(--teal-dark);
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
  object-fit: contain;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #e3e8ee;
  cursor: pointer;
  display: block;
  transition: transform .18s ease, box-shadow .18s ease, border-color .18s ease;
}

.result-thumb:hover {
  transform: translateY(-2px);
  border-color: var(--teal);
  box-shadow: 0 6px 14px rgba(15, 159, 154, .14);
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
  color: var(--ink-500);
  flex-wrap: wrap;
}

.meta-tag {
  background: #eef2f5;
  padding: 3px 10px;
  border-radius: 999px;
  color: var(--ink-700);
  font-weight: 550;
  font-size: 12px;
}

.spider-tag {
  cursor: pointer;
}

.group-tag {
  background: #d9f5ef;
  color: var(--teal-dark);
}

.update-time {
  color: var(--ink-500);
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
    padding: 18px 0;
  }
  .result-thumb {
    width: 64px;
    height: 64px;
  }
}
</style>
