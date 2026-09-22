<template>
  <section class="search-results-frame">
    <slot name="heading" />

    <div v-loading="loading" class="result-list">
      <slot />
      <div v-if="!loading && total === 0" class="empty">
        未找到相关结果
      </div>
    </div>

    <!-- 游标式分页（PIT + search_after）：仅支持上一页/下一页 -->
    <div v-if="total > 0 && cursorMode" class="results-pagination cursor-pagination">
      <span class="cursor-total">共 {{ total }} 条</span>
      <el-button :disabled="loading || currentPage <= 1" @click="$emit('prev')">上一页</el-button>
      <span class="cursor-page">第 {{ currentPage }} 页</span>
      <el-button :disabled="loading || !hasMore" @click="$emit('next')">下一页</el-button>
      <el-select
        :model-value="pageSize"
        class="cursor-size"
        size="small"
        @update:model-value="$emit('update:pageSize', $event); $emit('change')"
      >
        <el-option v-for="s in pageSizes" :key="s" :label="`${s} 条/页`" :value="s" />
      </el-select>
    </div>
    <!-- 传统偏移量分页 -->
    <el-pagination
      v-else-if="total > 0"
      class="results-pagination"
      :current-page="currentPage"
      :page-size="pageSize"
      :total="total"
      :page-sizes="pageSizes"
      layout="total, sizes, prev, pager, next"
      @update:current-page="$emit('update:currentPage', $event)"
      @update:page-size="$emit('update:pageSize', $event)"
      @change="$emit('change')"
    />
  </section>
</template>

<script setup lang="ts">
defineProps<{
  loading: boolean
  total: number
  currentPage: number
  pageSize: number
  pageSizes?: number[]
  /** 为 true 时使用游标式（上一页/下一页）分页 */
  cursorMode?: boolean
  /** 游标式分页：是否还有下一页 */
  hasMore?: boolean
}>()

defineEmits<{
  (event: 'update:currentPage', value: number): void
  (event: 'update:pageSize', value: number): void
  (event: 'change'): void
  (event: 'prev'): void
  (event: 'next'): void
}>()
</script>

<style scoped>
.search-results-frame {
  width: 100%;
}

.result-list {
  min-height: 100px;
}

.results-pagination {
  margin-top: 24px;
  justify-content: center;
}

.cursor-pagination {
  display: flex;
  align-items: center;
  gap: 12px;
}

.cursor-total {
  color: #909399;
  font-size: 13px;
}

.cursor-page {
  color: #606266;
  font-size: 13px;
  min-width: 56px;
  text-align: center;
}

.cursor-size {
  width: 110px;
}

.empty {
  padding: 48px 0;
  color: #909399;
  text-align: center;
}
</style>
