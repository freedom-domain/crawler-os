<template>
  <el-card>
    <template #header><span>数据搜索</span></template>

    <div class="search-bar">
      <div class="search-box">
        <el-icon class="search-icon"><Search /></el-icon>
        <input
          v-model="keyword"
          class="search-input"
          placeholder="搜索爬取的内容…"
          @keyup.enter="loadData"
        />
        <button v-if="keyword" class="clear-btn" @click="keyword = ''; loadData()">&times;</button>
      </div>
      <el-button type="primary" class="search-btn" @click="loadData">搜索</el-button>
    </div>

    <div v-if="total > 0" class="result-count">
      找到约 {{ total }} 条结果
    </div>

    <div v-loading="loading" class="result-list">
      <div v-for="row in list" :key="row.id" class="result-item">
        <div class="result-url">{{ row.url }}</div>
        <h3 class="result-title" v-html="row.titleHl || row.title"></h3>
        <p class="result-content" v-html="row.contentHl || (row.content?.substring(0, 200) + '...')"></p>
        <div class="result-meta">
          <span v-if="row.spiderName" class="meta-tag">{{ row.spiderName }}</span>
          <span class="meta-time">{{ formatTime(row.crawlTime) }}</span>
          <el-button size="small" text type="primary" @click="showPreview(row)">预览</el-button>
          <el-button size="small" text type="danger" @click="handleDelete(row)">删除</el-button>
        </div>
      </div>
      <div v-if="!loading && list.length === 0" class="empty">
        未找到相关结果
      </div>
    </div>

    <el-pagination
      v-if="total > 0"
      style="margin-top: 24px; justify-content: center"
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      @change="loadData"
    />

    <el-dialog v-model="previewVisible" :title="previewTitle" width="80%" top="5vh" destroy-on-close>
      <div v-loading="previewLoading" class="preview-container" v-html="previewHtml"></div>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { searchContent, searchDetail, searchDelete } from '@/api'
import { Search } from '@element-plus/icons-vue'

const list = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const keyword = ref('')
const previewVisible = ref(false)
const previewLoading = ref(false)
const previewTitle = ref('')
const previewHtml = ref('')

const formatTime = (t: string) => {
  if (!t) return ''
  const d = new Date(t)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

const loadData = async () => {
  loading.value = true
  try {
    const res: any = await searchContent({ current: page.value, size: size.value, keyword: keyword.value })
    list.value = res.data?.content || []
    total.value = res.data?.totalElements || 0
  } finally {
    loading.value = false
  }
}

const showPreview = async (row: any) => {
  previewVisible.value = true
  previewLoading.value = true
  previewTitle.value = row.title || '内容预览'
  previewHtml.value = ''
  try {
    const res: any = await searchDetail(row.id)
    previewHtml.value = res.data?.rawHtml || '<p>无原始内容</p>'
  } catch {
    previewHtml.value = '<p>加载失败</p>'
  } finally {
    previewLoading.value = false
  }
}

const handleDelete = async (row: any) => {
  await ElMessageBox.confirm('确定删除该条数据?', '警告', { type: 'warning' })
  await searchDelete(row.id)
  ElMessage.success('删除成功')
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.search-bar {
  margin-bottom: 20px;
  display: flex;
  align-items: center;
  gap: 12px;
  max-width: 700px;
}

.search-box {
  flex: 1;
  display: flex;
  align-items: center;
  border: 1px solid #dfe1e5;
  border-radius: 24px;
  padding: 0 16px;
  height: 44px;
  background: #fff;
  transition: box-shadow 0.2s;
}

.search-box:focus-within {
  box-shadow: 0 1px 6px rgba(32, 33, 36, 0.28);
  border-color: transparent;
}

.search-icon {
  color: #9aa0a6;
  font-size: 20px;
  margin-right: 12px;
  flex-shrink: 0;
}

.search-input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 16px;
  height: 100%;
  color: #202124;
}

.search-input::placeholder {
  color: #9aa0a6;
}

.clear-btn {
  border: none;
  background: none;
  color: #9aa0a6;
  font-size: 22px;
  cursor: pointer;
  padding: 0 4px;
  line-height: 1;
}

.clear-btn:hover {
  color: #202124;
}

.search-btn {
  border-radius: 24px;
  padding: 0 24px;
  height: 44px;
  font-size: 15px;
}

.result-count {
  color: #999;
  font-size: 13px;
  margin-bottom: 16px;
}

.result-list {
  min-height: 100px;
}

.result-item {
  padding: 16px 0;
  border-bottom: 1px solid #f0f0f0;
}

.result-item:last-child {
  border-bottom: none;
}

.result-url {
  color: #006621;
  font-size: 13px;
  margin-bottom: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.result-title {
  color: #1a0dab;
  font-size: 18px;
  font-weight: 400;
  margin: 0 0 6px 0;
  cursor: pointer;
  line-height: 1.4;
}

.result-title :deep(em) {
  font-style: normal;
  color: #1a0dab;
  font-weight: 700;
}

.result-content {
  color: #545454;
  font-size: 14px;
  line-height: 1.6;
  margin: 0 0 8px 0;
}

.result-content :deep(em) {
  font-style: normal;
  font-weight: 700;
}

.result-meta {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: #999;
}

.meta-tag {
  background: #f0f0f0;
  padding: 2px 8px;
  border-radius: 3px;
  color: #666;
}

.empty {
  text-align: center;
  color: #999;
  padding: 40px 0;
  font-size: 14px;
}

.preview-container {
  max-height: 70vh;
  overflow-y: auto;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 16px;
  background: #fff;
}
</style>
