<template>
  <SearchResultItem
    v-for="row in rows"
    :key="row.id"
    :row="row"
    :class="{ 'row-removing': row._removed }"
    @preview="$emit('preview', $event)"
    @detail="$emit('detail', $event)"
    @images="$emit('images', $event)"
  >
    <template #images>
      <div v-if="row.images && row.images.length" class="result-images">
        <button
          v-for="(img, idx) in row.images.slice(0, 6)"
          :key="idx"
          type="button"
          class="result-thumb-wrap"
          :title="`查看全部 ${row.images.length} 张图片`"
          @click="$emit('images', row)"
        >
          <img
            :src="thumbUrl(img, 144)"
            class="result-thumb"
            loading="lazy"
            decoding="async"
            alt=""
          />
          <span class="result-thumb-overlay" aria-hidden="true">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7-11-7-11-7z"/>
              <circle cx="12" cy="12" r="3"/>
            </svg>
            <span class="result-thumb-overlay-text">查看图片</span>
          </span>
        </button>
      </div>
    </template>

    <template #tags>
      <div v-if="row.tags && row.tags.length" class="result-tags">
        <el-tag
          v-for="tag in row.tags"
          :key="tag"
          size="small"
          class="tag-item"
          :class="{ 'tag-item-editable': authenticated }"
          @click="authenticated && $emit('tag', row)"
        >{{ tag }}</el-tag>
      </div>
    </template>

    <template #meta>
      <div class="result-meta">
        <span v-if="row.spiderName" class="meta-tag spider-tag" :class="{ 'spider-tag-clickable': authenticated }" @click="authenticated && row.spiderId && $emit('spider', row)">
          <svg class="tag-icon" viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><path d="M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20"/><path d="M2 12h20"/></svg>
          {{ row.spiderName }}
        </span>
        <span v-if="row.spiderGroup" class="meta-tag group-tag group-tag-clickable" @click="$emit('group', row.spiderGroup)">
          <svg class="tag-icon" viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z"/></svg>
          {{ row.spiderGroup }}
        </span>
        <span v-if="authenticated" class="result-actions">
          <button
            type="button"
            class="fav-btn"
            :class="{ 'fav-btn--active': row.favorited, 'fav-btn--loading': row._favLoading }"
            :disabled="row._favLoading"
            :title="row.favorited ? '取消收藏' : '收藏该条内容'"
            :aria-label="row.favorited ? '取消收藏' : '收藏该条内容'"
            :aria-pressed="Boolean(row.favorited)"
            @click.stop="$emit('favorite', row, !row.favorited)"
          >
            <el-icon
              :size="16"
              class="fav-btn__icon"
              :class="{ 'fav-btn__icon--pop': row._favJustChanged }"
            >
              <StarFilled v-if="row.favorited" />
              <Star v-else />
            </el-icon>
          </button>
          <el-dropdown
            trigger="click"
            popper-class="table-action-popper"
            :show-timeout="100"
            :hide-timeout="100"
            @command="(command: string) => $emit('command', command, row)"
            @update:visible="(visible: boolean) => setDropdownOpen(row, visible)"
          >
            <button
              type="button"
              class="action-trigger"
              :class="{ 'is-open': row._dropdownOpen }"
              title="更多操作"
              aria-label="更多操作"
            >
              <el-icon class="action-trigger__icon"><MoreFilled /></el-icon>
            </button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="tag">
                  <span class="action-item"><el-icon class="action-icon action-icon--edit"><Edit /></el-icon>标签</span>
                </el-dropdown-item>
                <el-dropdown-item command="rerun" :disabled="!row.spiderId || !row.url">
                  <span class="action-item"><el-icon class="action-icon action-icon--rerun"><RefreshRight /></el-icon>重新爬取</span>
                </el-dropdown-item>
                <el-dropdown-item command="delete" class="action-item-danger">
                  <span class="action-item"><el-icon class="action-icon action-icon--delete"><Delete /></el-icon>删除</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </span>
        <span v-if="row.updateTime" class="meta-time update-time">
          {{ formatTime(row.updateTime) }} · {{ formatTimeAgo(row.updateTime) }}
        </span>
      </div>
    </template>
  </SearchResultItem>
</template>

<script setup lang="ts">
import SearchResultItem from '@/components/SearchResultItem.vue'
import { Star, StarFilled, Edit, RefreshRight, Delete, MoreFilled } from '@element-plus/icons-vue'
import { formatTimeAgo } from '@/utils/dateTime'

defineProps<{
  rows: any[]
  authenticated?: boolean
  imageUrl: (objectName: string, width?: number) => string
  thumbUrl: (objectName: string, width?: number) => string
  formatTime: (value: string) => string
}>()

defineEmits<{
  (event: 'preview', row: any): void
  (event: 'detail', row: any): void
  (event: 'images', row: any): void
  (event: 'tag', row: any): void
  (event: 'spider', row: any): void
  (event: 'group', group: string): void
  (event: 'favorite', row: any, value: boolean): void
  (event: 'command', command: string, row: any): void
}>()

// 操作下拉打开/关闭状态：仅由点击（visible 变化）驱动，hover 不旋转箭头
const setDropdownOpen = (row: any, visible: boolean) => {
  row._dropdownOpen = visible
}
</script>

<style scoped>
.result-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 0 0 10px;
}

.result-thumb-wrap {
  position: relative;
  display: block;
  width: 72px;
  height: 72px;
  padding: 0;
  border: 1px solid #e3e8ee;
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  overflow: hidden;
  transition: transform .18s ease, box-shadow .18s ease, border-color .18s ease;
}

.result-thumb-wrap img {
  width: 100%;
  height: 100%;
  object-fit: contain;
  display: block;
  transition: transform .22s ease;
}

.result-thumb-wrap:hover {
  transform: translateY(-2px);
  border-color: var(--teal);
  box-shadow: 0 6px 14px rgba(15, 159, 154, .16);
}

.result-thumb-wrap:hover img {
  transform: scale(1.06);
}

.result-thumb-wrap:focus-visible {
  outline: 2px solid #087f70;
  outline-offset: 2px;
}

.result-thumb-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  background: rgba(8, 127, 112, .42);
  color: #fff;
  opacity: 0;
  transition: opacity .18s ease;
  pointer-events: none;
  backdrop-filter: blur(1px);
}

.result-thumb-wrap:hover .result-thumb-overlay {
  opacity: 1;
}

.result-thumb-overlay-text {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: .5px;
  text-shadow: 0 1px 2px rgba(0, 0, 0, .3);
}

.result-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 0 0 10px;
}

.result-tags :deep(.el-tag) {
  border-radius: 999px;
  font-weight: 600;
}

.result-tags :deep(.el-tag--small) {
  padding: 0 10px;
  height: 24px;
  line-height: 22px;
}

.tag-item { cursor: default; }
.tag-item-editable { cursor: pointer; }

.result-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  color: var(--ink-700);
  font-size: 13px;
}

.meta-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 550;
  letter-spacing: .2px;
  line-height: 1;
  transition: background .18s ease, color .18s ease, box-shadow .18s ease;
}

.meta-tag .tag-icon {
  flex-shrink: 0;
  opacity: .7;
}

.spider-tag {
  background: #f1f5f5;
  color: var(--ink-700);
  border: 1px solid #e3e8ee;
}

.spider-tag-clickable { cursor: pointer; }
.spider-tag-clickable:hover {
  background: #e9f6f5;
  color: #0f766e;
  border-color: #b7e7dc;
  box-shadow: 0 1px 4px rgba(15, 129, 124, .1);
}
.spider-tag-clickable:hover .tag-icon { opacity: 1; }

.group-tag {
  background: #d9f5ef;
  color: var(--teal-dark);
  border: 1px solid #b7e7dc;
}
.group-tag-clickable { cursor: pointer; }
.group-tag-clickable:hover {
  background: #c9ece5;
  color: #075e55;
  border-color: #8ed6c8;
  box-shadow: 0 1px 4px rgba(15, 129, 124, .15);
}
.group-tag-clickable:hover .tag-icon { opacity: 1; }

.update-time { color: var(--ink-500); font-size: 12px; }

/* 搜索结果操作使用轻量图标按钮，避免抢占标题和正文的视觉层级。 */
.result-actions {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.action-trigger {
  display: inline-grid;
  align-items: center;
  justify-items: center;
  width: 30px;
  height: 30px;
  padding: 0;
  border: 1px solid transparent;
  border-radius: 50%;
  background: transparent;
  color: var(--ink-500);
  cursor: pointer;
  transition: background .18s ease, border-color .18s ease, color .18s ease;
}

.action-trigger:hover {
  background: #eef7f6;
  border-color: #d7eae7;
  color: var(--teal-dark);
}

.action-trigger:active,
.action-trigger.is-open {
  background: #e2f2ef;
  border-color: #c6e4df;
  color: var(--teal-dark);
}

.action-trigger:focus-visible,
.fav-btn:focus-visible {
  outline: 2px solid var(--teal);
  outline-offset: 2px;
}

.action-trigger__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  flex-shrink: 0;
}



.fav-btn {
  display: inline-grid;
  align-items: center;
  justify-items: center;
  width: 30px;
  height: 30px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: transparent;
  color: #9aa7b5;
  cursor: pointer;
  transition: background .18s ease, border-color .18s ease, color .18s ease;
}

.fav-btn:hover {
  background: #fcf7eb;
  color: #b48732;
}

.fav-btn--active {
  background: #fcf7eb;
  color: #b48732;
}

.fav-btn--active:hover {
  background: #f8efd9;
  color: #946b20;
}

.fav-btn--loading {
  cursor: wait;
  opacity: .65;
}

.fav-btn__icon {
  color: inherit;
  transition: transform .2s ease;
}

.fav-btn__icon--pop {
  animation: fav-pop .42s cubic-bezier(.28, 1.6, .5, 1);
}

@keyframes fav-pop {
  0%   { transform: scale(1); }
  35%  { transform: scale(1.45) rotate(-8deg); }
  65%  { transform: scale(.88) rotate(4deg); }
  100% { transform: scale(1) rotate(0); }
}

.action-item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.action-icon {
  font-size: 15px;
  flex-shrink: 0;
}

.action-icon--edit {
  color: #6b7c8d;
  transition: color .15s ease;
}

.action-icon--rerun {
  color: #6b7c8d;
  transition: color .15s ease;
}

.action-icon--delete {
  color: #94a3b1;
  transition: color .15s ease;
}

@media (max-width: 460px) {
  .result-thumb { width: 64px; height: 64px; }
  .result-meta { gap: 8px; }
}
</style>

<!--
  操作下拉菜单样式（全局，非 scoped）
  el-dropdown 弹层通过 teleport 渲染到 body，脱离了组件 DOM 树，
  scoped 样式的 :deep() 无法匹配到这些节点，必须用全局选择器。
  通过 popper-class="table-action-popper" 限定作用范围，不影响其他下拉。
-->
<style>
.table-action-popper .el-dropdown-menu {
  min-width: 136px;
  border-radius: 10px;
  padding: 5px 0;
  box-shadow: 0 8px 24px rgba(15, 30, 40, .12);
  border: 1px solid #edf3f2;
  background: #fff;
}

.table-action-popper .el-dropdown-menu__item {
  border-radius: 6px;
  margin: 1px 5px;
  padding: 8px 12px;
  font-size: 13px;
  color: #3d4f60;
  transition: background .15s ease, color .15s ease;
}

.table-action-popper .el-dropdown-menu__item:not(.is-disabled):hover {
  background: #eefaf7;
  color: #075e55;
}

.table-action-popper .el-dropdown-menu__item.is-disabled {
  color: #b6c2cc;
}

.table-action-popper .el-dropdown-menu__item:not(.is-disabled):hover .action-icon--edit {
  color: #0a8074;
}

.table-action-popper .el-dropdown-menu__item:not(.is-disabled):hover .action-icon--rerun {
  color: #0a8074;
}

.table-action-popper .el-dropdown-menu__item.action-item-danger {
  color: #e05c5c;
}

.table-action-popper .el-dropdown-menu__item.action-item-danger .action-icon--delete {
  color: #e05c5c;
}

.table-action-popper .el-dropdown-menu__item.action-item-danger:hover {
  background: #fdecec;
  color: #c8342e;
}

.table-action-popper .el-dropdown-menu__item.action-item-danger:hover .action-icon--delete {
  color: #c8342e;
}
</style>
