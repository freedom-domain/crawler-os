<template>
  <el-card>
    <div class="history-toolbar">
      <span>记录每次成功打开的图片预览，最多显示 100 条/页。</span>
      <div>
        <el-button type="danger" plain :disabled="loading" @click="clearHistory">清空记录</el-button>
      </div>
    </div>

    <el-form class="history-filters" :inline="true" @submit.prevent="search">
      <el-form-item>
        <el-input
          v-model="title"
          clearable
          placeholder="标题"
          style="width: 200px"
          @keyup.enter="search"
          @clear="search"
        />
      </el-form-item>
      <el-form-item>
        <el-input
          v-model="url"
          clearable
          placeholder="来源 URL"
          style="width: 240px"
          @keyup.enter="search"
          @clear="search"
        />
      </el-form-item>
      <el-form-item>
        <el-select
          v-model="spiderName"
          clearable
          filterable
          placeholder="爬虫"
          style="width: 200px"
          @change="search"
        >
          <el-option v-for="spider in spiderOptions" :key="spider.id" :label="spider.name" :value="spider.name" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" :loading="loading" @click="search">搜索</el-button>
        <el-button :icon="Refresh" :loading="loading" @click="resetFilters">重置</el-button>
      </el-form-item>
    </el-form>

    <div class="table-scroll-wrapper">
    <el-table :data="list" v-loading="loading" stripe resizable border>
      <template #empty>
        <el-empty v-if="!loading" description="暂无图片预览记录" />
      </template>
      <el-table-column prop="title" label="内容标题" min-width="280" show-overflow-tooltip resizable>
        <template #default="{ row }">
          <a class="title-link" href="javascript:void(0)" @click="openPreview(row)">
            {{ row.title || '无标题' }}
          </a>
        </template>
      </el-table-column>
      <el-table-column prop="url" label="来源 URL" min-width="300" show-overflow-tooltip resizable>
        <template #default="{ row }">{{ row.url || '-' }}</template>
      </el-table-column>
      <el-table-column prop="spiderName" label="爬虫" min-width="160" show-overflow-tooltip resizable>
        <template #default="{ row }">{{ row.spiderName || '-' }}</template>
      </el-table-column>
      <el-table-column prop="imageCount" label="图片数" width="100" align="center" resizable />
      <el-table-column prop="createTime" label="预览时间" min-width="180" resizable>
        <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90" fixed="right" align="center" resizable>
        <template #default="{ row }">
          <el-button link type="danger" @click="deleteHistory(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    </div>

    <el-pagination
      v-if="total"
      class="pagination"
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      :page-sizes="[10, 15, 20, 50, 100]"
      layout="total, sizes, prev, pager, next, jumper"
      @change="loadData"
    />
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { clearImagePreviewHistory, deleteImagePreviewHistory, imagePreviewHistoryPage, spiderPage } from '@/api'
import { confirm } from '@/utils/confirm'
import { formatDateTime } from '@/utils/dateTime'

const list = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(15)
const total = ref(0)
const title = ref('')
const url = ref('')
const spiderName = ref('')
const spiderOptions = ref<any[]>([])

const loadData = async () => {
  loading.value = true
  try {
    const res: any = await imagePreviewHistoryPage({
      current: page.value,
      size: size.value,
      title: title.value.trim() || undefined,
      url: url.value.trim() || undefined,
      spiderName: spiderName.value.trim() || undefined
    })
    const result = res.data
    list.value = result?.records || []
    total.value = result?.total || 0
  } catch {
    ElMessage.error('图片预览记录加载失败')
  } finally {
    loading.value = false
  }
}

const search = () => {
  page.value = 1
  loadData()
}

const resetFilters = () => {
  title.value = ''
  url.value = ''
  spiderName.value = ''
  search()
}

const loadSpiderOptions = async () => {
  try {
    const res: any = await spiderPage({ current: 1, size: 1000 })
    spiderOptions.value = res.data?.records || []
  } catch {
    ElMessage.error('爬虫列表加载失败')
  }
}

const openPreview = (row: any) => {
  const query = new URLSearchParams({
    id: String(row.contentId),
    title: String(row.title || '图片预览')
  })
  const win = window.open(`/image-preview?${query.toString()}`, '_blank')
  if (!win) {
    ElMessage.warning('浏览器阻止了新窗口，请允许弹出窗口后重试')
  }
}

const deleteHistory = async (row: any) => {
  try {
    if (!await confirm('确定删除这条图片预览记录吗？')) return
  } catch {
    return
  }
  try {
    await deleteImagePreviewHistory(row.id)
    if (list.value.length === 1 && page.value > 1) page.value--
    await loadData()
    ElMessage.success('记录已删除')
  } catch {
    ElMessage.error('图片预览记录删除失败')
  }
}

const clearHistory = async () => {
  try {
    if (!await confirm('确定清空全部图片预览记录吗？')) return
  } catch {
    return
  }
  try {
    await clearImagePreviewHistory()
    page.value = 1
    await loadData()
    ElMessage.success('记录已清空')
  } catch {
    ElMessage.error('图片预览记录清空失败')
  }
}

onMounted(() => {
  loadData()
  loadSpiderOptions()
})
</script>

<style scoped>
.history-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
  color: var(--el-text-color-secondary);
}

.history-filters {
  margin-bottom: 8px;
}

.title-link {
  color: var(--el-color-primary);
  cursor: pointer;
  text-decoration: none;
}

.title-link:hover {
  text-decoration: underline;
}

.pagination {
  justify-content: flex-end;
  margin-top: 16px;
}
</style>
