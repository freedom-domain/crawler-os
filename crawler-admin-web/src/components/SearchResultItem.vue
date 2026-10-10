<template>
  <div class="result-item">
    <a class="result-url" :href="row.url" target="_blank" rel="noopener noreferrer">{{ row.url }}</a>
    <h3 class="result-title" @click.prevent="$emit('preview', row)">
      <span v-html="row.titleHl || row.title"></span>
      <button
        v-if="row.images?.length"
        type="button"
        class="result-image-count"
        :title="`点击查看全部 ${row.images.length} 张图片`"
        @click.stop="$emit('images', row)"
      >
        <span class="result-image-count__icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
            <rect x="3" y="3" width="18" height="18" rx="2.5"/>
            <circle cx="8.5" cy="8.5" r="1.6"/>
            <path d="M21 15.5l-4.8-4.8a1.5 1.5 0 0 0-2.1 0L5 19.5"/>
          </svg>
        </span>
        <span class="result-image-count__text">{{ row.images.length }}</span>
        <span class="result-image-count__label">张图片</span>
        <span class="result-image-count__chevron" aria-hidden="true">
          <svg viewBox="0 0 12 12" width="10" height="10" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round">
            <path d="M4 2.5l4 3.5-4 3.5"/>
          </svg>
        </span>
      </button>
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
  transition: opacity .24s ease, transform .24s ease,
    padding .28s ease, max-height .28s ease, border-bottom-width .2s ease;
}

.result-item:last-child {
  border-bottom: none;
}

/* 删除时平滑淡出：高度折叠 + 透明 + 右移，配合父级 setTimeout 移除 */
.row-removing {
  overflow: hidden;
  opacity: 0;
  transform: translateX(24px);
  max-height: 0 !important;
  padding-top: 0 !important;
  padding-bottom: 0 !important;
  border-bottom-width: 0 !important;
  pointer-events: none;
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
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-family: inherit;
  margin-left: 10px;
  padding: 3px 8px 3px 6px;
  border: 1px solid #b7e7dc;
  border-radius: 999px;
  background: linear-gradient(135deg, #f2fcfa 0%, #e4f5f2 100%);
  color: #087f70;
  font-size: 12px;
  font-weight: 600;
  line-height: inherit;
  vertical-align: middle;
  white-space: nowrap;
  cursor: pointer;
  overflow: hidden;
  transition: background .2s ease, box-shadow .2s ease, transform .2s ease,
    border-color .2s ease, color .2s ease;
}

/* 悬停扫光 */
.result-image-count::after {
  content: "";
  position: absolute;
  top: 0;
  left: -120%;
  width: 70%;
  height: 100%;
  background: linear-gradient(
    110deg,
    transparent 0%,
    rgba(255, 255, 255, 0.55) 50%,
    transparent 100%
  );
  transform: skewX(-20deg);
  transition: left .55s ease;
  pointer-events: none;
}

.result-image-count:hover {
  background: linear-gradient(135deg, #d9f3ed 0%, #c9ece5 100%);
  border-color: #8ed6c8;
  box-shadow: 0 4px 12px rgba(15, 129, 124, .2), 0 1px 2px rgba(15, 129, 124, .08);
  transform: translateY(-1px);
  color: #075e55;
}

.result-image-count:hover::after {
  left: 130%;
}

.result-image-count:active {
  transform: translateY(0);
  box-shadow: 0 2px 6px rgba(15, 129, 124, .18);
}

.result-image-count:focus-visible {
  outline: 2px solid #087f70;
  outline-offset: 2px;
}

.result-image-count__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: rgba(255, 255, 255, .65);
  box-shadow: inset 0 0 0 1px rgba(8, 127, 112, .12);
  color: #0a8074;
  flex-shrink: 0;
}

.result-image-count__text {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: linear-gradient(180deg, #0a8577 0%, #087f70 100%);
  color: #fff;
  font-size: 11.5px;
  font-weight: 800;
  letter-spacing: .3px;
  line-height: 1;
  font-variant-numeric: tabular-nums;
  box-shadow: 0 1px 2px rgba(7, 94, 85, .22);
}

.result-image-count__label {
  color: #0a6a60;
  font-weight: 600;
  letter-spacing: .2px;
}

.result-image-count__chevron {
  display: inline-flex;
  align-items: center;
  color: #0a8074;
  opacity: .55;
  margin-left: -2px;
  transition: transform .2s ease, opacity .2s ease;
}

.result-image-count:hover .result-image-count__chevron {
  transform: translateX(2px);
  opacity: 1;
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
