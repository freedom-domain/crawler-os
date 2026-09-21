<template>
  <section class="search-results-frame">
    <slot name="heading" />

    <div v-loading="loading" class="result-list">
      <slot />
      <div v-if="!loading && total === 0" class="empty">
        未找到相关结果
      </div>
    </div>

    <el-pagination
      v-if="total > 0"
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
}>()

defineEmits<{
  (event: 'update:currentPage', value: number): void
  (event: 'update:pageSize', value: number): void
  (event: 'change'): void
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

.empty {
  padding: 48px 0;
  color: #909399;
  text-align: center;
}
</style>
