<template>
  <SearchResultItem
    v-for="row in rows"
    :key="row.id"
    :row="row"
    @preview="$emit('preview', $event)"
    @detail="$emit('detail', $event)"
    @images="$emit('images', $event)"
  >
    <template #images>
      <div v-if="row.images && row.images.length" class="result-images">
        <img
          v-for="(img, idx) in row.images.slice(0, 6)"
          :key="idx"
          :src="thumbUrl(img, 144)"
          class="result-thumb"
          loading="lazy"
          decoding="async"
          @click="$emit('images', row)"
        />
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
        <span v-if="row.spiderName" class="meta-tag spider-tag" :class="{ 'spider-tag-clickable': authenticated }" @click="authenticated && row.spiderId && $emit('spider', row)">{{ row.spiderName }}</span>
        <span v-if="row.spiderGroup" class="meta-tag group-tag">{{ row.spiderGroup }}</span>
        <span v-if="row.updateTime" class="meta-time update-time">
          更新: {{ formatTime(row.updateTime) }} · {{ formatTimeAgo(row.updateTime) }}
        </span>
        <el-button
          v-if="authenticated"
          size="small"
          text
          :type="row.favorited ? 'warning' : 'primary'"
          @click.stop="$emit('favorite', row, !row.favorited)"
        >
          <el-icon :size="16" :color="row.favorited ? '#c79a2e' : ''"><StarFilled v-if="row.favorited" /><Star v-else /></el-icon>
          <span style="margin-left: 2px">{{ row.favorited ? '已收藏' : '收藏' }}</span>
        </el-button>
        <el-dropdown v-if="authenticated" trigger="click" popper-class="table-action-popper" @command="(command: string) => $emit('command', command, row)">
          <el-button size="small" text type="primary">
            操作<el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="tag">
                <span class="action-item"><el-icon><Edit /></el-icon>标签</span>
              </el-dropdown-item>
              <el-dropdown-item command="rerun" :disabled="!row.spiderId || !row.url">
                <span class="action-item"><el-icon><RefreshRight /></el-icon>重新爬取</span>
              </el-dropdown-item>
              <el-dropdown-item command="delete" divided class="is-danger">
                <span class="action-item"><el-icon><Delete /></el-icon>删除</span>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </template>
  </SearchResultItem>
</template>

<script setup lang="ts">
import SearchResultItem from '@/components/SearchResultItem.vue'
import { ArrowDown, Star, StarFilled, Edit, RefreshRight, Delete } from '@element-plus/icons-vue'
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
  (event: 'favorite', row: any, value: boolean): void
  (event: 'command', command: string, row: any): void
}>()
</script>

<style scoped>
.result-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 0 0 10px;
}

.result-thumb {
  width: 72px;
  height: 72px;
  border: 1px solid #e3e8ee;
  border-radius: 6px;
  cursor: pointer;
  display: block;
}

.result-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin: 0 0 8px;
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
  padding: 2px 8px;
  border-radius: 4px;
  background: #f1f5f5;
  color: var(--ink-700);
}

.group-tag {
  background: #d9f5ef;
  color: var(--teal-dark);
}

.spider-tag { cursor: default; }
.spider-tag-clickable { cursor: pointer; }
.spider-tag-clickable:hover { background: #e9f6f5; color: #0f766e; }
.update-time { color: var(--ink-500); font-size: 12px; }

.action-item { display: inline-flex; align-items: center; gap: 8px; }

@media (max-width: 460px) {
  .result-thumb { width: 64px; height: 64px; }
  .result-meta { gap: 8px; }
}
</style>
