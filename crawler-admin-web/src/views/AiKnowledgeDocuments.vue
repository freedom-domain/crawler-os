<template>
  <section class="knowledge-document-page">
    <header class="page-heading">
      <div>
        <h1>{{ selectedBase?.name || '知识库文档管理' }}</h1>
        <p>管理当前知识库的文档收录状态、向量索引及文档详情。</p>
      </div>
      <div class="heading-actions">
        <el-select v-if="!routeKnowledgeBaseId" v-model="selectedKnowledgeBaseId" placeholder="选择知识库"
          class="base-select" popper-class="knowledge-base-select-popper"
          @change="changeKnowledgeBase">
          <el-option v-for="base in knowledgeBases" :key="base.id" :label="base.name" :value="base.id" />
        </el-select>
        <el-upload v-if="selectedBase?.sourceType === 'UPLOAD'" :show-file-list="false"
          :before-upload="beforeUpload" :http-request="uploadDocument">
          <el-button type="primary" :loading="uploading">上传文档</el-button>
        </el-upload>
        <el-tag :type="stats.enabled ? 'success' : 'info'" effect="light">
          {{ stats.enabled ? 'RAG 已启用' : 'RAG 已停用' }}
        </el-tag>
        <el-button :icon="Refresh" :loading="refreshing" @click="loadAll">刷新</el-button>
        <el-button type="primary" :icon="RefreshRight" :loading="rebuilding"
          :disabled="Boolean(vectorizingId) || !stats.enabled" @click="rebuildIndex">
          批量重建向量索引
        </el-button>
      </div>
    </header>

    <div v-loading="statsLoading" class="stats-grid">
      <el-card shadow="never"><span>源文档</span><strong>{{ stats.sourceDocuments }}</strong></el-card>
      <el-card shadow="never"><span>已收录文档</span><strong>{{ stats.includedDocuments }}</strong></el-card>
      <el-card shadow="never"><span>已排除文档</span><strong>{{ stats.excludedDocuments }}</strong></el-card>
      <el-card shadow="never"><span>向量分块</span><strong>{{ stats.vectorDocuments }}</strong></el-card>
    </div>

    <div class="index-info">
      <span v-if="selectedBase?.sourceType === 'UPLOAD'">
        文档来源 Bucket <code>{{ selectedBase.uploadBucket || '-' }}</code>
      </span>
      <span v-else-if="selectedBase">
        信息源索引 <code>{{ selectedBase.sourceIndex || '-' }}</code>
      </span>
      <span v-if="selectedBase">
        向量索引 <code>{{ selectedBase.vectorIndex || '-' }}</code>
      </span>
    </div>

    <el-card v-if="selectedBase?.sourceType === 'UPLOAD'" shadow="never" class="uploaded-card">
      <template #header>已上传文档（MinIO）</template>
      <el-table :data="uploadedDocuments" stripe>
        <el-table-column prop="title" label="标题" min-width="180" />
        <el-table-column prop="originalName" label="原始文件名" min-width="220" />
        <el-table-column prop="fileSize" label="大小" width="120">
          <template #default="{ row }">{{ formatFileSize(row.fileSize) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.vectorized ? 'success' : 'warning'">
              {{ row.vectorized ? '已向量化' : '待处理' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="minioBucket" label="MinIO Bucket" min-width="180" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="!row.vectorized"
              @click="showUploadedDetail(row)">分块详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" class="document-card">
      <div class="filter-toolbar">
        <el-input v-model="keyword" clearable :prefix-icon="Search"
          placeholder="搜索标题、正文、来源 URL" @keyup.enter="applySearch" />
        <el-checkbox v-model="includeExcluded" @change="applySearch">显示已排除文档</el-checkbox>
        <el-button type="primary" :icon="Search" :loading="loading" @click="applySearch">搜索</el-button>
      </div>

      <el-table :data="documents" v-loading="loading" row-key="id" stripe>
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.title || '无标题' }}</template>
        </el-table-column>
        <el-table-column prop="snippet" label="内容摘要" min-width="280" show-overflow-tooltip />
        <el-table-column prop="spiderName" label="来源" width="150" show-overflow-tooltip />
        <el-table-column label="收录状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.excluded ? 'info' : 'success'" effect="light">
              {{ row.excluded ? '已排除' : '已收录' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDetail(row)">详情</el-button>
            <el-button link type="primary" :loading="vectorizingId === row.id"
              :disabled="Boolean(vectorizingId) || rebuilding || !stats.enabled"
              @click="vectorizeDocument(row)">
              {{ row.excluded ? '重新纳入' : '重新向量化' }}
            </el-button>
            <el-button v-if="!row.excluded" link type="danger"
              :disabled="Boolean(vectorizingId) || rebuilding" @click="excludeDocument(row)">
              排除
            </el-button>
          </template>
        </el-table-column>
        <template #empty><el-empty description="没有找到知识库文档" /></template>
      </el-table>

      <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :total="total"
        :page-sizes="[10, 20, 50, 100]" layout="total, sizes, prev, pager, next"
        class="pagination" @change="loadDocuments" />
    </el-card>

    <el-dialog v-model="detailVisible" :title="detail?.title || '文档详情'" width="min(900px, 94vw)">
      <div v-loading="detailLoading" class="detail-content">
        <div v-if="detail" class="detail-meta">
          <span>文档 ID：{{ detail.id }}</span>
          <span>来源：{{ detail.spiderName || '-' }}</span>
          <a v-if="detail.url" :href="detail.url" target="_blank" rel="noopener noreferrer">{{ detail.url }}</a>
          <el-tag :type="detail.excluded ? 'info' : 'success'">
            {{ detail.excluded ? '已从知识库排除' : '已收录' }}
          </el-tag>
        </div>
        <el-tabs>
          <el-tab-pane label="原文内容">
            <pre v-if="detail" class="document-content">{{ detail.content || '暂无正文内容' }}</pre>
          </el-tab-pane>
          <el-tab-pane :label="`向量分块（${chunks.length}）`">
            <el-empty v-if="!chunksLoading && chunks.length === 0" description="暂无向量分块" />
            <el-collapse v-else v-loading="chunksLoading" class="chunk-list">
              <el-collapse-item v-for="chunk in chunks" :key="chunk.chunkId"
                :name="chunk.chunkId">
                <template #title>
                  <span>分块 {{ chunk.index + 1 }}</span>
                  <el-tag size="small" effect="plain" class="chunk-count">
                    {{ chunk.characterCount }} 字符
                  </el-tag>
                </template>
                <pre class="chunk-content">{{ chunk.content }}</pre>
              </el-collapse-item>
            </el-collapse>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, RefreshRight, Search } from '@element-plus/icons-vue'
import {
  excludeKnowledgeBaseDocument, getKnowledgeBaseDocument, getKnowledgeBaseDocuments,
  getKnowledgeBaseDocumentChunks,
  getKnowledgeBaseStats, syncRagIndex, vectorizeKnowledgeBaseDocument,
  getKnowledgeBases, getKnowledgeBaseUploadedDocuments, uploadKnowledgeBaseDocument
} from '@/api'

interface KnowledgeDocument {
  id: string
  title: string
  snippet: string
  url: string
  spiderName: string
  spiderId: number | null
  excluded: boolean
}

interface KnowledgeDocumentDetail extends KnowledgeDocument { content: string }

interface VectorChunk {
  chunkId: string
  index: number
  title: string
  content: string
  characterCount: number
  embeddingModel: string
}

const emptyStats = () => ({
  enabled: false, sourceIndex: '', vectorIndex: '', sourceDocuments: 0,
  includedDocuments: 0, excludedDocuments: 0, vectorDocuments: 0
})

const stats = ref(emptyStats())
const documents = ref<KnowledgeDocument[]>([])
const detail = ref<KnowledgeDocumentDetail | null>(null)
const keyword = ref('')
const includeExcluded = ref(true)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(false)
const statsLoading = ref(false)
const refreshing = ref(false)
const rebuilding = ref(false)
const vectorizingId = ref('')
const detailVisible = ref(false)
const detailLoading = ref(false)
const chunks = ref<VectorChunk[]>([])
const chunksLoading = ref(false)
const knowledgeBases = ref<any[]>([])
const selectedKnowledgeBaseId = ref('')
const selectedBase = ref<any>(null)
const uploading = ref(false)
const uploadedDocuments = ref<any[]>([])
const route = useRoute()
const routeKnowledgeBaseId = computed(() => {
  const paramId = route.params.knowledgeBaseId
  if (typeof paramId === 'string') return paramId
  const queryId = route.query.knowledgeBaseId
  if (typeof queryId === 'string') return queryId
  return ''
})

async function loadStats() {
  statsLoading.value = true
  try {
    const response: any = await getKnowledgeBaseStats(selectedKnowledgeBaseId.value)
    if (response) stats.value = response.data
  } catch {
    ElMessage.error('读取知识库状态失败')
  } finally {
    statsLoading.value = false
  }
}

async function loadDocuments() {
  loading.value = true
  documents.value = []
  total.value = 0
  try {
    const response: any = await getKnowledgeBaseDocuments({
      knowledgeBaseId: selectedKnowledgeBaseId.value,
      page: page.value, pageSize: pageSize.value,
      keyword: keyword.value, includeExcluded: includeExcluded.value
    })
    if (!response) return
    documents.value = response.data.records || []
    total.value = response.data.total || 0
  } catch {
    ElMessage.error('读取知识库文档失败')
  } finally {
    loading.value = false
  }
}

async function loadAll() {
  refreshing.value = true
  try {
    const baseResponse: any = await getKnowledgeBases()
    knowledgeBases.value = baseResponse.data || []
    const routeId = routeKnowledgeBaseId.value
    if (routeId) {
      selectedKnowledgeBaseId.value = routeId
    } else if (knowledgeBases.value.length && !knowledgeBases.value.some(
      (base: any) => String(base.id) === String(selectedKnowledgeBaseId.value)
    )) {
      selectedKnowledgeBaseId.value = knowledgeBases.value[0].id
    }
    if (!selectedKnowledgeBaseId.value || knowledgeBases.value.length === 0) {
      selectedKnowledgeBaseId.value = ''
      selectedBase.value = null
      uploadedDocuments.value = []
      stats.value = emptyStats()
      documents.value = []
      total.value = 0
      page.value = 1
      return
    }
    await loadKnowledgeDocumentContext()
    await Promise.all([loadStats(), loadDocuments()])
  } finally {
    refreshing.value = false
  }
}

async function changeKnowledgeBase() {
  page.value = 1
  await loadKnowledgeDocumentContext()
  await Promise.all([loadStats(), loadDocuments()])
}

function applySearch() {
  page.value = 1
  void loadDocuments()
}

function formatFileSize(size: number) {
  if (!size) return '0 B'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

async function showDetail(row: KnowledgeDocument) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    const response: any = await getKnowledgeBaseDocument(row.id, selectedKnowledgeBaseId.value)
    if (response) detail.value = response.data
    await loadDocumentChunks(row.id)
  } catch {
    ElMessage.error('读取文档详情失败')
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

async function showUploadedDetail(row: any) {
  detailVisible.value = true
  detailLoading.value = false
  detail.value = {
    id: row.sourceDocumentId,
    title: row.title,
    content: '',
    url: '',
    spiderName: '上传文档',
    spiderId: null,
    excluded: false,
    snippet: ''
  }
  await loadDocumentChunks(row.sourceDocumentId)
}

async function loadDocumentChunks(documentId: string) {
  chunksLoading.value = true
  chunks.value = []
  try {
    const response: any = await getKnowledgeBaseDocumentChunks(
      documentId, selectedKnowledgeBaseId.value
    )
    chunks.value = response?.data || []
  } catch {
    ElMessage.error('读取文档向量分块失败')
  } finally {
    chunksLoading.value = false
  }
}

async function vectorizeDocument(row: KnowledgeDocument) {
  vectorizingId.value = row.id
  try {
    const response: any = await vectorizeKnowledgeBaseDocument(row.id, selectedKnowledgeBaseId.value)
    if (!response) return
    ElMessage.success(
      `向量索引 ${selectedBase.value?.vectorIndex || ''} 已更新，文档向量化完成，共生成 ${response.data.chunks} 个分块`
    )
    await loadAll()
  } catch {
    ElMessage.error('文档向量化失败，请检查向量模型服务')
  } finally {
    vectorizingId.value = ''
  }
}

async function excludeDocument(row: KnowledgeDocument) {
  try {
    await ElMessageBox.confirm(
      '该文档会从知识库排除，但保留在爬虫源索引和普通搜索中。',
      '排除知识库文档',
      { type: 'warning' }
    )
  } catch {
    return
  }
  vectorizingId.value = row.id
  try {
    await excludeKnowledgeBaseDocument(row.id, selectedKnowledgeBaseId.value)
    ElMessage.success('文档已从知识库排除')
    await loadAll()
  } catch {
    ElMessage.error('排除知识库文档失败')
  } finally {
    vectorizingId.value = ''
  }
}

async function loadKnowledgeDocumentContext() {
  selectedBase.value = knowledgeBases.value.find(
    (base: any) => String(base.id) === String(selectedKnowledgeBaseId.value)
  ) || null
  if (selectedBase.value?.sourceType === 'UPLOAD') {
    const response: any = await getKnowledgeBaseUploadedDocuments(selectedKnowledgeBaseId.value)
    uploadedDocuments.value = response.data || []
  } else {
    uploadedDocuments.value = []
  }
}

function beforeUpload(file: File) {
  const allowed = /\.(txt|md|markdown|pdf|doc|docx|xls|xlsx|ppt|pptx|csv|rtf)$/i
  if (!allowed.test(file.name)) {
    ElMessage.error('仅支持 TXT、Markdown、PDF、Word、Excel、PPT、CSV、RTF 文档')
    return false
  }
  if (file.size > 50 * 1024 * 1024) {
    ElMessage.error('单个文档不能超过 50MB')
    return false
  }
  return true
}

async function uploadDocument(options: any) {
  if (!selectedKnowledgeBaseId.value) return
  uploading.value = true
  try {
    await uploadKnowledgeBaseDocument(selectedKnowledgeBaseId.value, options.file)
    ElMessage.success(`文档上传并向量化成功，向量索引 ${selectedBase.value?.vectorIndex || ''} 已更新`)
    await loadKnowledgeDocumentContext()
    await loadAll()
  } catch {
    ElMessage.error('文档上传失败，请检查 MinIO、文档解析或向量模型')
  } finally {
    uploading.value = false
  }
}

async function rebuildIndex() {
  try {
    await ElMessageBox.confirm(
      '将按当前配置重建向量索引，已排除文档不会重新收录。',
      '批量重建知识库',
      { type: 'warning' }
    )
  } catch {
    return
  }
  rebuilding.value = true
  try {
    const response: any = await syncRagIndex(selectedKnowledgeBaseId.value)
    if (!response) return
    ElMessage.success(
      `向量索引 ${selectedBase.value?.vectorIndex || ''} 已构建，知识库重建完成：${response.data.chunks} 个分块`
    )
    await loadAll()
  } catch {
    ElMessage.error('知识库重建失败，请检查 RAG 配置和向量模型服务')
  } finally {
    rebuilding.value = false
  }
}

watch(routeKnowledgeBaseId, (routeId) => {
  selectedKnowledgeBaseId.value = routeId
  void loadAll()
})

onMounted(loadAll)
</script>

<style scoped>
.knowledge-document-page { max-width: 1440px; margin: 0 auto; }
.page-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; margin-bottom: 16px; }
.page-heading h1 { margin: 0 0 6px; }
.page-heading p { margin: 0; color: var(--el-text-color-secondary); }
.heading-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; min-width: 0; }
.base-select { width: 240px; max-width: 100%; flex: 0 1 240px; }
:global(.knowledge-base-select-popper) { max-width: calc(100vw - 24px); }
:global(.knowledge-base-select-popper .el-select-dropdown__item) {
  height: auto;
  min-height: 34px;
  line-height: 1.4;
  white-space: normal;
  overflow-wrap: anywhere;
}
.stats-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin-bottom: 12px; }
.stats-grid :deep(.el-card__body) { display: flex; flex-direction: column; gap: 6px; }
.stats-grid span { color: var(--el-text-color-secondary); font-size: 12px; }
.stats-grid strong { font-size: 24px; }
.index-info { display: flex; gap: 16px; margin-bottom: 12px; color: var(--el-text-color-secondary); font-size: 12px; }
.filter-toolbar { display: grid; grid-template-columns: minmax(240px, 1fr) auto auto; gap: 12px; margin-bottom: 12px; }
.pagination { display: flex; justify-content: flex-end; margin-top: 16px; }
.detail-meta { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; color: var(--el-text-color-secondary); }
.document-content { max-height: 60vh; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; }
.chunk-list { margin-top: 4px; }
.chunk-count { margin-left: 8px; }
.chunk-content { max-height: 320px; overflow: auto; margin: 0; white-space: pre-wrap; overflow-wrap: anywhere; }
@media (max-width: 900px) {
  .stats-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .filter-toolbar { grid-template-columns: 1fr; }
  .page-heading { flex-direction: column; }
}
</style>
