<template>
  <el-card>
    <template #header>
      <div class="card-header">
        <span>我的收藏</span>
        <div class="header-actions">
          <el-input v-model="keyword" clearable placeholder="搜索标题、URL、正文或标签" style="width: 280px" @keyup.enter="refresh" @clear="refresh" />
          <el-button type="primary" :icon="Refresh" :loading="loading" @click="refresh">刷新</el-button>
        </div>
      </div>
    </template>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="title" label="标题" min-width="240" show-overflow-tooltip>
        <template #default="{ row }">{{ row.title || '无标题' }}</template>
      </el-table-column>
      <el-table-column label="来源 URL" min-width="300" show-overflow-tooltip>
        <template #default="{ row }">
          <a v-if="row.url" class="source-url" :href="row.url" target="_blank" rel="noopener noreferrer">{{ row.url }}</a>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="spiderName" label="爬虫" width="160" show-overflow-tooltip>
        <template #default="{ row }">{{ row.spiderName || '-' }}</template>
      </el-table-column>
      <el-table-column label="标签" min-width="180">
        <template #default="{ row }">
          <el-tag v-for="tag in row.tags || []" :key="tag" size="small" class="tag">{{ tag }}</el-tag>
          <span v-if="!row.tags || !row.tags.length">-</span>
        </template>
      </el-table-column>
      <el-table-column prop="crawlTime" label="抓取时间" width="180" />
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button type="danger" link @click="remove(row)">取消收藏</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && !list.length" description="暂无收藏" />
    <el-pagination
      v-if="total > 0"
      class="pagination"
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next, jumper"
      @change="loadData"
    />
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { favoriteDelete, favoritePage } from '@/api'

const list = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)
const keyword = ref('')

const loadData = async () => {
  loading.value = true
  try {
    const res: any = await favoritePage({ current: page.value, size: size.value, keyword: keyword.value.trim() || undefined })
    list.value = res?.data?.content || []
    total.value = res?.data?.totalElements || 0
  } finally {
    loading.value = false
  }
}

const refresh = () => {
  page.value = 1
  loadData()
}

const remove = async (row: any) => {
  try {
    await ElMessageBox.confirm('确定取消收藏该内容吗？', '提示', { type: 'warning' })
    await favoriteDelete(row.id)
    ElMessage.success('已取消收藏')
    await loadData()
  } catch (error: any) {
    if (error !== 'cancel') console.error(error)
  }
}

onMounted(loadData)
</script>

<style scoped>
.card-header { display: flex; align-items: center; justify-content: space-between; }
.pagination { margin-top: 16px; display: flex; justify-content: flex-end; }
.source-url { color: #2563eb; text-decoration: none; }
.source-url:hover { text-decoration: underline; }
.tag { margin-right: 6px; margin-bottom: 4px; }
</style>
