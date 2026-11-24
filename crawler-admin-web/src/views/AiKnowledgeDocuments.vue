<template>
  <section class="knowledge-document-page">
    <header class="page-heading">
      <div>
        <h1>{{ selectedBase?.name || '知识库文档管理' }}</h1>
        <p>管理当前知识库的文档收录状态、向量索引及文档详情。</p>
      </div>
      <div class="heading-actions">
        <AiChunkStrategyManager ref="chunkStrategyManagerRef" :show-trigger="false" />
        <el-select v-if="!routeKnowledgeBaseId" v-model="selectedKnowledgeBaseId" placeholder="选择知识库"
          class="base-select" popper-class="knowledge-base-select-popper"
          @change="changeKnowledgeBase">
          <el-option v-for="base in knowledgeBases" :key="base.id" :label="base.name" :value="base.id" />
        </el-select>
        <el-upload v-if="selectedBase?.sourceType === 'UPLOAD'" :show-file-list="false"
          :before-upload="beforeUpload" :http-request="uploadDocument">
          <el-button type="primary" :loading="uploading">上传文档</el-button>
        </el-upload>
        <el-button :icon="Refresh" :loading="refreshing" @click="loadAll">刷新</el-button>
        <el-dropdown trigger="click" popper-class="knowledge-index-dropdown" @command="handleIndexCommand">
          <el-button type="primary">
            索引与策略<el-icon class="el-icon--right"><ArrowDown /></el-icon>
          </el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="rebuild" :icon="RefreshRight" :disabled="indexActionDisabled">
                批量重建向量索引
              </el-dropdown-item>
              <el-dropdown-item v-if="selectedBase?.sourceType === 'ES_INDEX'" command="sync"
                :icon="RefreshRight" divided :disabled="indexActionDisabled">
                增量同步向量索引
              </el-dropdown-item>
              <el-dropdown-item command="strategy" :icon="Operation" divided>
                分块策略管理
              </el-dropdown-item>
              <el-dropdown-item command="clear" :icon="Delete" divided class="is-danger"
                :disabled="indexActionDisabled">
                清除向量索引
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <template v-if="rebuilding">
          <el-tag type="warning" effect="plain">{{ rebuildTaskMessage || '正在重建' }}</el-tag>
          <el-button type="danger" plain @click="cancelRebuild">取消重建</el-button>
        </template>
        <template v-if="syncing">
          <el-tag type="success" effect="plain">{{ syncTaskMessage || '正在增量同步' }}</el-tag>
          <el-button type="danger" plain @click="cancelSync">取消同步</el-button>
        </template>
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

    <el-card shadow="never" class="document-card">
      <div class="filter-toolbar">
        <el-input v-model="keyword" clearable :prefix-icon="Search"
          placeholder="搜索标题、正文、来源 URL" @keyup.enter="applySearch" />
        <el-checkbox v-model="includeExcluded" @change="applySearch">显示已排除文档</el-checkbox>
        <el-select v-model="vectorizedFilter" clearable placeholder="向量化状态" @change="applySearch">
          <el-option label="已向量化" :value="true" />
          <el-option label="待处理" :value="false" />
        </el-select>
        <el-button type="primary" :icon="Search" :loading="loading" @click="applySearch">搜索</el-button>
      </div>

      <div class="table-scroll-wrapper">
        <el-table :data="documents" v-loading="loading" row-key="id" stripe resizable border
          class="document-list-table">
          <el-table-column prop="title" label="标题" min-width="180" fixed="left" show-overflow-tooltip resizable>
            <template #default="{ row }">{{ row.title || '无标题' }}</template>
          </el-table-column>
          <el-table-column v-if="selectedBase?.sourceType === 'UPLOAD'" label="原始文件名"
            min-width="220" show-overflow-tooltip resizable>
            <template #default="{ row }">{{ uploadedDocumentFor(row)?.originalName || '-' }}</template>
          </el-table-column>
          <el-table-column v-if="selectedBase?.sourceType === 'UPLOAD'" label="大小" width="120" resizable>
            <template #default="{ row }">{{ uploadedFileSize(row) }}</template>
          </el-table-column>
          <el-table-column prop="snippet" label="内容摘要" min-width="280" show-overflow-tooltip resizable />
          <el-table-column label="文档来源" min-width="130" resizable>
            <template #default="{ row }">
              <el-tag v-if="isUploadedDocument(row)" type="warning" effect="light">MinIO 上传</el-tag>
              <el-tag v-else type="success" effect="light">爬虫采集</el-tag>
              <span v-if="!isUploadedDocument(row) && row.spiderName" class="source-name">
                {{ row.spiderName }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="向量化状态" width="110" resizable>
            <template #default="{ row }">
              <el-tag :type="row.vectorized ? 'success' : 'warning'">
                {{ row.vectorized ? '已向量化' : '待处理' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="chunkCount" label="分块数量" width="100" align="center" resizable>
            <template #default="{ row }">{{ row.chunkCount }}</template>
          </el-table-column>
          <el-table-column v-if="selectedBase?.sourceType === 'UPLOAD'" label="MinIO Bucket"
            min-width="180" show-overflow-tooltip resizable>
            <template #default="{ row }">{{ uploadedDocumentFor(row)?.minioBucket || '-' }}</template>
          </el-table-column>
          <el-table-column label="收录状态" width="110" resizable>
            <template #default="{ row }">
              <el-tag :type="row.excluded ? 'info' : 'success'" effect="light">
                {{ row.excluded ? '已排除' : '已收录' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" fixed="right" align="center" resizable>
            <template #default="{ row }">
              <el-dropdown trigger="click" popper-class="knowledge-document-operation-menu"
                @command="(command: string) => handleDocumentCommand(command, row)">
                <el-button link size="small" class="action-more-btn"><el-icon><MoreFilled /></el-icon></el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="detail">文档详情</el-dropdown-item>
                    <el-dropdown-item command="vectorize"
                      :disabled="Boolean(vectorizingId) || Boolean(deletingId) || Boolean(clearingIndex)
                        || rebuilding || syncing || !selectedBase?.enabled">
                      {{ row.excluded ? '重新纳入' : '重新向量化' }}
                    </el-dropdown-item>
                    <el-dropdown-item v-if="!row.excluded" command="exclude" divided
                      :disabled="Boolean(vectorizingId) || Boolean(deletingId) || Boolean(clearingIndex)
                        || rebuilding || syncing" class="is-danger">
                      排除
                    </el-dropdown-item>
                    <el-dropdown-item v-if="isUploadedDocument(row)" command="delete" divided
                      :disabled="Boolean(vectorizingId) || Boolean(deletingId) || Boolean(clearingIndex)
                        || rebuilding || syncing"
                      class="is-danger">
                      删除文档
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </el-table-column>
          <template #empty><el-empty description="没有找到知识库文档" /></template>
        </el-table>
      </div>

      <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :total="total"
        :page-sizes="[10, 15, 20, 50, 100]" layout="total, sizes, prev, pager, next, jumper"
        class="pagination" @change="loadDocuments" />
    </el-card>

    <el-drawer v-model="detailVisible" :title="detail?.title || '文档详情'"
      size="min(900px, 94vw)" direction="rtl" append-to-body class="document-detail-drawer">
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
            <div class="original-pane">
              <div class="document-content" v-loading="originalLoading && originalHtml === ''"
                @scroll="onOriginalScroll">
                <div v-if="originalHtml" class="document-markdown" v-html="originalHtml" />
                <el-empty v-else-if="originalTotalLength === 0 && !originalLoading && !originalError"
                  description="暂无正文内容" />
                <el-empty v-else-if="originalError" :description="originalError" />
              </div>
              <div v-if="originalTotalLength > 0" class="original-footer">
                <span>{{ originalContent.length }} / {{ originalTotalLength }} 字符</span>
                <span v-if="originalError" class="original-error">加载失败，滚动到底部自动重试</span>
                <span v-else-if="originalContent.length < originalTotalLength">向下滚动自动加载</span>
              </div>
            </div>
          </el-tab-pane>
          <el-tab-pane :label="`向量分块（${chunks.length}）`">
            <el-empty v-if="!chunksLoading && chunks.length === 0" description="暂无向量分块" />
            <!-- element-plus 2.14 的 collapse 只 emit change（携带激活 name 数组），无 expand/active-change -->
            <el-collapse v-else v-loading="chunksLoading" class="chunk-list"
              @change="onChunksActiveChange">
              <el-collapse-item v-for="chunk in chunks" :key="chunk.chunkId"
                :name="chunk.chunkId">
                <template #title>
                  <span>分块 {{ chunk.index + 1 }}</span>
                  <el-tag v-if="chunk.heading" size="small" type="warning" effect="plain" class="chunk-count">
                    {{ chunk.heading }}
                  </el-tag>
                  <el-tag size="small" effect="plain" class="chunk-count">
                    {{ chunk.characterCount }} 字符
                  </el-tag>
                  <el-tag v-if="chunk.chunkStrategyName" size="small" type="info" effect="plain">
                    {{ chunk.chunkStrategyName }}
                  </el-tag>
                </template>
                <div class="chunk-content" v-loading="chunk.contentLoading">
                  <div v-if="chunk.content != null" class="document-markdown"
                    v-html="renderDocumentMarkdown(chunk.content)" />
                  <div v-else-if="!chunk.contentLoading" class="chunk-content-hint">
                    内容加载失败，请折叠后重新展开
                  </div>
                </div>
              </el-collapse-item>
            </el-collapse>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>

    <el-dialog v-model="vectorizeDialogVisible" title="重新向量化文档" width="520px">
      <p class="vectorize-dialog-tip">
        选择分块策略后开始向量化；不选择则按文档字符数和类型自动匹配策略。
      </p>
      <el-select v-model="vectorizeStrategyId" clearable placeholder="自动匹配分块策略"
        style="width: 100%">
        <el-option v-for="strategy in chunkStrategies" :key="strategy.id"
          :label="strategyLabel(strategy)" :value="strategy.id" />
      </el-select>
      <template #footer>
        <el-button @click="vectorizeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="Boolean(vectorizingId)" @click="confirmVectorize">
          开始向量化
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowDown, Delete, MoreFilled, Operation, Refresh, RefreshRight, Search } from '@element-plus/icons-vue'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import AiChunkStrategyManager from '@/components/AiChunkStrategyManager.vue'
import {
  excludeKnowledgeBaseDocument, getKnowledgeBaseDocument, getKnowledgeBaseDocuments,
  getKnowledgeBaseDocumentChunk, getKnowledgeBaseDocumentChunks,
  getKnowledgeBaseDocumentContent,
  getChunkStrategies,
  getKnowledgeBaseStats, syncRagIndex, syncRagIndexIncremental, vectorizeKnowledgeBaseDocument,
  clearKnowledgeVectorIndex,
  getKnowledgeBases, getKnowledgeBaseUploadedDocuments, uploadKnowledgeBaseDocument,
  deleteKnowledgeBaseUploadedDocument,
  getRagSyncTask, getLatestRagSyncTask, cancelRagSyncTask
} from '@/api'

interface KnowledgeDocument {
  id: string
  title: string
  snippet: string
  url: string
  spiderName: string
  spiderId: number | null
  excluded: boolean
  vectorized: boolean
  chunkCount: number
}

interface UploadedDocument {
  originalName: string
  fileSize: number
  minioBucket: string
  sourceDocumentId: string
  vectorized: boolean
  objectName: string
}

interface KnowledgeDocumentDetail extends KnowledgeDocument { content: string }

interface VectorChunk {
  chunkId: string
  index: number
  title: string
  heading?: string
  /** 分块正文：列表接口不返回，展开时按需加载 */
  content: string | null
  contentLoading?: boolean
  characterCount: number
  embeddingModel: string
  chunkStrategyId: string
  chunkStrategyName: string
}

function uploadedDocumentFor(row: KnowledgeDocument) {
  return uploadedDocumentsBySourceId.value.get(row.id)
}

function isUploadedDocument(row: KnowledgeDocument) {
  return selectedBase.value?.sourceType === 'UPLOAD' || Boolean(uploadedDocumentFor(row))
}

function uploadedFileSize(row: KnowledgeDocument) {
  const document = uploadedDocumentFor(row)
  return document ? formatFileSize(document.fileSize) : '-'
}

const STRATEGY_TYPE_LABELS: Record<string, string> = {
  FIXED_CHARACTER: '固定字符',
  PARAGRAPH: '段落',
  RECURSIVE: '递归',
  SEMANTIC: '语义'
}

function strategyLabel(strategy: any) {
  const type = STRATEGY_TYPE_LABELS[strategy.strategyType] || strategy.strategyType
  const overlap = strategy.strategyType === 'SEMANTIC' ? '' : ` / 重叠 ${strategy.chunkOverlap}`
  const suffix = strategy.defaultStrategy ? '（默认）' : ''
  return `${strategy.name} · ${type} · ${strategy.chunkSize}${overlap}${suffix}`
}

const emptyStats = () => ({
  sourceIndex: '', vectorIndex: '', sourceDocuments: 0,
  includedDocuments: 0, excludedDocuments: 0, vectorDocuments: 0
})

const stats = ref(emptyStats())
const documents = ref<KnowledgeDocument[]>([])
const detail = ref<KnowledgeDocumentDetail | null>(null)
const keyword = ref('')
const includeExcluded = ref(true)
const vectorizedFilter = ref<boolean | null>(null)
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const loading = ref(false)
const statsLoading = ref(false)
const refreshing = ref(false)
const rebuilding = ref(false)
const vectorizingId = ref('')
const deletingId = ref('')
const vectorizeDialogVisible = ref(false)
const vectorizeDocumentRow = ref<KnowledgeDocument | null>(null)
const vectorizeStrategyId = ref<string>('')
const chunkStrategies = ref<any[]>([])
const detailVisible = ref(false)
const detailLoading = ref(false)
const detailDocumentId = ref('')
const chunks = ref<VectorChunk[]>([])
const chunksLoading = ref(false)
const knowledgeBases = ref<any[]>([])
const selectedKnowledgeBaseId = ref('')
const selectedBase = ref<any>(null)
const uploading = ref(false)
const uploadedDocuments = ref<UploadedDocument[]>([])
const uploadedDocumentsBySourceId = computed(() => new Map(
  uploadedDocuments.value.map(document => [document.sourceDocumentId, document])
))
const route = useRoute()
const routeKnowledgeBaseId = computed(() => {
  const paramId = route.params.knowledgeBaseId
  if (typeof paramId === 'string') return paramId
  const queryId = route.query.knowledgeBaseId
  if (typeof queryId === 'string') return queryId
  return ''
})
const routeObjectName = computed(() => {
  const objectName = route.params.objectName
  return typeof objectName === 'string' ? objectName : ''
})
const markdown = new MarkdownIt({ breaks: true, html: false, linkify: true })

function renderDocumentMarkdown(content: string) {
  return DOMPurify.sanitize(markdown.render(content || '暂无正文内容'))
}

function updateDocumentTabTitle() {
  const knowledgeBaseName = selectedBase.value?.name
  window.dispatchEvent(new CustomEvent('crawler-tab-title-update', {
    detail: {
      path: route.path,
      title: knowledgeBaseName ? `${knowledgeBaseName} - 文档管理` : '知识库文档管理'
    }
  }))
}

function handleDocumentCommand(command: string, row: KnowledgeDocument) {
  if (command === 'detail') void showDetail(row)
  else if (command === 'vectorize') void openVectorizeDialog(row)
  else if (command === 'exclude') void excludeDocument(row)
  else if (command === 'delete') void deleteDocument(row)
}

async function openVectorizeDialog(row: KnowledgeDocument) {
  vectorizeDocumentRow.value = row
  vectorizeStrategyId.value = ''
  vectorizeDialogVisible.value = true
  try {
    const response: any = await getChunkStrategies()
    chunkStrategies.value = response?.data || []
  } catch {
    chunkStrategies.value = []
  }
}

function confirmVectorize() {
  const row = vectorizeDocumentRow.value
  vectorizeDialogVisible.value = false
  if (row) void vectorizeDocument(row, vectorizeStrategyId.value || undefined)
}

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
  try {
    const response: any = await getKnowledgeBaseDocuments({
      knowledgeBaseId: selectedKnowledgeBaseId.value,
      page: page.value, pageSize: pageSize.value,
      keyword: keyword.value, includeExcluded: includeExcluded.value,
      vectorized: vectorizedFilter.value ?? undefined
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
    if (routeObjectName.value) {
      const document = uploadedDocuments.value.find(
        item => item.objectName === `knowledge/${selectedKnowledgeBaseId.value}/${routeObjectName.value}`
      )
      if (document) {
        await showDetail(document)
      } else {
        ElMessage.error('找不到对应的知识库上传文档')
      }
    }
  } finally {
    refreshing.value = false
  }
}

async function changeKnowledgeBase() {
  page.value = 1
  await loadKnowledgeDocumentContext()
  await Promise.all([loadStats(), loadDocuments()])
  void restoreRebuildTask()
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

async function showDetail(row: KnowledgeDocument | UploadedDocument) {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  chunks.value = []
  chunksLoading.value = false
  originalContent.value = ''
  originalHtml.value = ''
  originalTotalLength.value = 0
  originalError.value = ''
  const documentId = 'sourceDocumentId' in row ? row.sourceDocumentId : row.id
  detailDocumentId.value = documentId
  try {
    // 原文正文不再随详情返回，改由 /content 接口按字符偏移分页加载
    const response: any = await getKnowledgeBaseDocument(
      documentId, selectedKnowledgeBaseId.value, false
    )
    if (detailDocumentId.value === documentId && response) {
      detail.value = response.data
      void loadOriginalContent(true)
      await loadDocumentChunks(documentId)
    }
  } catch {
    if (detailDocumentId.value === documentId) {
      ElMessage.error('读取文档详情失败')
      detailVisible.value = false
    }
  } finally {
    detailLoading.value = false
  }
}

async function loadDocumentChunks(documentId: string) {
  chunksLoading.value = true
  chunks.value = []
  try {
    const response: any = await getKnowledgeBaseDocumentChunks(
      documentId, selectedKnowledgeBaseId.value
    )
    if (detailDocumentId.value === documentId) {
      // 只保留当前文档的分块（chunkId 形如 <文档ID>:<序号>），防止跨文档分块混入
      chunks.value = (response?.data || []).filter(
        (chunk: VectorChunk) => String(chunk.chunkId).startsWith(`${documentId}:`)
      )
    }
  } catch {
    ElMessage.error('读取文档向量分块失败')
  } finally {
    chunksLoading.value = false
  }
}

/** el-collapse-item 无 expand 事件，由父级 change 事件统一触发（值为当前激活 name 数组，accordion 模式为单个 name） */
function onChunksActiveChange(activeNames: string | (string | number)[]) {
  const active = Array.isArray(activeNames) ? activeNames : [activeNames]
  for (const name of active) {
    const chunk = chunks.value.find(item => item.chunkId === String(name))
    if (chunk) void loadChunkContent(chunk)
  }
}

const chunkContentLoading = new Set<string>()

/** 展开分块时自动加载正文（列表接口不返回 content），成功后缓存不再重复请求；失败自动重试 2 次 */
async function loadChunkContent(chunk: VectorChunk) {
  if (chunk.content != null || chunkContentLoading.has(chunk.chunkId)) return
  chunkContentLoading.add(chunk.chunkId)
  const documentId = detailDocumentId.value
  chunk.contentLoading = true
  const fetchOnce = async () => {
    try {
      const response: any = await getKnowledgeBaseDocumentChunk(
        documentId, chunk.chunkId, selectedKnowledgeBaseId.value
      )
      if (detailDocumentId.value === documentId && response?.data) {
        chunk.content = response.data.content ?? null
      }
    } catch {
      // 继续重试
    }
  }
  await fetchOnce()
  for (let i = 1; i <= 2 && chunk.content == null && detailDocumentId.value === documentId; i++) {
    await new Promise(resolve => setTimeout(resolve, 800))
    await fetchOnce()
  }
  chunk.contentLoading = false
  chunkContentLoading.delete(chunk.chunkId)
}

const originalContent = ref('')
const originalHtml = ref('')
const originalTotalLength = ref(0)
const originalLoading = ref(false)
const originalError = ref('')

/** 按字符偏移分页加载原文（服务端切片、页尾对齐换行），只渲染新增片段并追加，避免整篇重渲染 */
async function loadOriginalContent(reset = false) {
  if (originalLoading.value) return
  const documentId = detailDocumentId.value
  if (!documentId) return
  if (reset) {
    originalContent.value = ''
    originalHtml.value = ''
    originalTotalLength.value = 0
    originalError.value = ''
  }
  originalLoading.value = true
  try {
    const response: any = await getKnowledgeBaseDocumentContent(
      documentId, originalContent.value.length, 50000, selectedKnowledgeBaseId.value
    )
    if (detailDocumentId.value !== documentId) return
    const data = response?.data
    if (!data) {
      originalError.value = '原文加载失败'
      return
    }
    originalTotalLength.value = data.totalLength ?? 0
    if (data.content) {
      originalContent.value += data.content
      originalHtml.value += DOMPurify.sanitize(markdown.render(data.content))
      originalError.value = ''
    }
  } catch {
    if (detailDocumentId.value === documentId) originalError.value = '原文加载失败'
  } finally {
    if (detailDocumentId.value === documentId) originalLoading.value = false
  }
}

function onOriginalScroll(event: Event) {
  const el = event.target as HTMLElement
  const hasMore = originalTotalLength.value > 0
    && originalContent.value.length < originalTotalLength.value
  if (!hasMore || originalLoading.value) return
  originalError.value = ''
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 200) void loadOriginalContent(false)
}

async function vectorizeDocument(row: KnowledgeDocument, strategyId?: string) {
  vectorizingId.value = row.id
  try {
    const response: any = await vectorizeKnowledgeBaseDocument(
      row.id, selectedKnowledgeBaseId.value, strategyId
    )
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

async function deleteDocument(row: KnowledgeDocument) {
  try {
    await ElMessageBox.confirm(
      '将删除该上传文档的原始文件（MinIO）、向量分块和源文档记录，删除后不可恢复。确定删除吗？',
      '删除上传文档',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  deletingId.value = row.id
  try {
    await deleteKnowledgeBaseUploadedDocument(selectedKnowledgeBaseId.value, row.id)
    if (detailDocumentId.value === row.id) detailVisible.value = false
    ElMessage.success('上传文档已删除')
    await loadAll()
  } catch {
    ElMessage.error('删除上传文档失败，请稍后重试')
  } finally {
    deletingId.value = ''
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
  updateDocumentTabTitle()
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

const REBUILD_POLL_INTERVAL_MS = 2000
const rebuildTaskId = ref('')
const rebuildTaskMessage = ref('')
let rebuildPollTimer: number | null = null

function stopRebuildPolling() {
  if (rebuildPollTimer !== null) {
    window.clearTimeout(rebuildPollTimer)
    rebuildPollTimer = null
  }
}

function resetRebuildState() {
  stopRebuildPolling()
  rebuilding.value = false
  rebuildTaskId.value = ''
  rebuildTaskMessage.value = ''
}

async function pollRebuildTask(taskId: string) {
  let response: any
  try {
    response = await getRagSyncTask(taskId)
  } catch {
    // 请求拦截器已提示错误；停止轮询并恢复按钮状态
    resetRebuildState()
    return
  }
  if (!response || !response.data) {
    resetRebuildState()
    return
  }
  const data = response.data
  if (data.status === 'PENDING' || data.status === 'RUNNING') {
    rebuildTaskMessage.value = data.message || '正在重建向量索引'
    rebuildPollTimer = window.setTimeout(() => {
      rebuildPollTimer = null
      void pollRebuildTask(taskId)
    }, REBUILD_POLL_INTERVAL_MS)
    return
  }
  resetRebuildState()
  if (data.status === 'COMPLETED') {
    ElMessage.success(
      `向量索引 ${selectedBase.value?.vectorIndex || ''} 已构建，知识库重建完成：${data.result?.chunks ?? 0} 个分块`
    )
  } else if (data.status === 'CANCELED') {
    ElMessage.warning(data.message || '向量索引重建已取消')
  } else {
    ElMessage.error(data.message || '知识库重建失败，请检查 RAG 配置和向量模型服务')
  }
  void loadAll()
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
  stopRebuildPolling()
  rebuilding.value = true
  rebuildTaskId.value = ''
  rebuildTaskMessage.value = '正在提交重建任务'
  try {
    const response: any = await syncRagIndex(selectedKnowledgeBaseId.value)
    if (!response?.data?.taskId) {
      resetRebuildState()
      return
    }
    rebuildTaskId.value = response.data.taskId
    rebuildTaskMessage.value = response.data.message || '等待执行'
    void pollRebuildTask(rebuildTaskId.value)
  } catch {
    resetRebuildState()
    // 请求拦截器已提示后端错误信息
  }
}

async function cancelRebuild() {
  if (!rebuildTaskId.value) return
  try {
    await ElMessageBox.confirm(
      '取消会中断重建，向量索引可能处于部分状态，之后可再次重建。确定取消吗？',
      '取消重建',
      { type: 'warning', confirmButtonText: '取消重建', cancelButtonText: '继续重建' }
    )
  } catch {
    return
  }
  try {
    const response: any = await cancelRagSyncTask(rebuildTaskId.value)
    if (response?.data?.message) rebuildTaskMessage.value = response.data.message
    ElMessage.success(response?.data?.message || '已发送取消请求')
  } catch {
    // 请求拦截器已提示后端错误信息
  }
}

async function restoreRebuildTask() {
  resetRebuildState()
  resetSyncState()
  if (!selectedKnowledgeBaseId.value) return
  try {
    const response: any = await getLatestRagSyncTask(selectedKnowledgeBaseId.value)
    const data = response?.data
    if (!data || (data.status !== 'PENDING' && data.status !== 'RUNNING')) return
    if (data.mode === 'INCREMENTAL') {
      syncing.value = true
      syncTaskId.value = data.taskId
      syncTaskMessage.value = data.message || '正在增量同步'
      void pollSyncTask(data.taskId)
    } else {
      rebuilding.value = true
      rebuildTaskId.value = data.taskId
      rebuildTaskMessage.value = data.message || '正在重建向量索引'
      void pollRebuildTask(data.taskId)
    }
  } catch {
    // 无重建任务或读取失败：保持空闲状态
  }
}

// ===================== 清除向量索引 =====================
const clearingIndex = ref(false)

async function clearVectorIndex() {
  if (!selectedKnowledgeBaseId.value) return
  try {
    await ElMessageBox.confirm(
      `将删除知识库“${selectedBase.value?.name || ''}”全部文档的向量分块，源文档与上传文件保留，之后可通过“批量重建向量索引”或“重新向量化”恢复。确定清除吗？`,
      '清除向量索引',
      { type: 'warning', confirmButtonText: '清除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  clearingIndex.value = true
  try {
    await clearKnowledgeVectorIndex(selectedKnowledgeBaseId.value)
    ElMessage.success('知识库向量索引已清除')
    await loadAll()
  } catch {
    ElMessage.error('清除向量索引失败，请检查 ES 连接和知识库配置')
  } finally {
    clearingIndex.value = false
  }
}

// ===================== 增量同步（内容索引新增文档） =====================
const SYNC_POLL_INTERVAL_MS = 2000
const syncing = ref(false)
const syncTaskId = ref('')
const syncTaskMessage = ref('')
let syncPollTimer: number | null = null

function stopSyncPolling() {
  if (syncPollTimer !== null) {
    window.clearTimeout(syncPollTimer)
    syncPollTimer = null
  }
}

function resetSyncState() {
  stopSyncPolling()
  syncing.value = false
  syncTaskId.value = ''
  syncTaskMessage.value = ''
}

async function pollSyncTask(taskId: string) {
  let response: any
  try {
    response = await getRagSyncTask(taskId)
  } catch {
    // 请求拦截器已提示错误；停止轮询并恢复按钮状态
    resetSyncState()
    return
  }
  if (!response || !response.data) {
    resetSyncState()
    return
  }
  const data = response.data
  if (data.status === 'PENDING' || data.status === 'RUNNING') {
    syncTaskMessage.value = data.message || '正在增量同步向量索引'
    syncPollTimer = window.setTimeout(() => {
      syncPollTimer = null
      void pollSyncTask(taskId)
    }, SYNC_POLL_INTERVAL_MS)
    return
  }
  resetSyncState()
  if (data.status === 'COMPLETED') {
    ElMessage.success(
      `增量同步完成：新增 ${data.result?.sourceDocuments ?? 0} 篇文档、${data.result?.chunks ?? 0} 个分块`
    )
  } else if (data.status === 'CANCELED') {
    ElMessage.warning(data.message || '向量索引增量同步已取消')
  } else {
    ElMessage.error(data.message || '向量索引增量同步失败，请检查 RAG 配置和向量模型服务')
  }
  void loadAll()
}

async function syncNewContent() {
  try {
    await ElMessageBox.confirm(
      '仅向量化内容索引中新增且尚未收录的文档，已向量化文档不会被重复处理。',
      '增量同步知识库',
      { type: 'warning' }
    )
  } catch {
    return
  }
  stopSyncPolling()
  syncing.value = true
  syncTaskId.value = ''
  syncTaskMessage.value = '正在提交增量同步任务'
  try {
    const response: any = await syncRagIndexIncremental(selectedKnowledgeBaseId.value)
    if (!response?.data?.taskId) {
      resetSyncState()
      return
    }
    syncTaskId.value = response.data.taskId
    syncTaskMessage.value = response.data.message || '等待执行'
    void pollSyncTask(syncTaskId.value)
  } catch {
    resetSyncState()
    // 请求拦截器已提示后端错误信息
  }
}

async function cancelSync() {
  if (!syncTaskId.value) return
  try {
    await ElMessageBox.confirm(
      '取消会中断增量同步，已写入的分块会保留，未处理的文档可稍后再次同步。确定取消吗？',
      '取消增量同步',
      { type: 'warning', confirmButtonText: '取消同步', cancelButtonText: '继续同步' }
    )
  } catch {
    return
  }
  try {
    const response: any = await cancelRagSyncTask(syncTaskId.value)
    if (response?.data?.message) syncTaskMessage.value = response.data.message
    ElMessage.success(response?.data?.message || '已发送取消请求')
  } catch {
    // 请求拦截器已提示后端错误信息
  }
}

// ===================== 头部“索引与策略”下拉菜单 =====================
const chunkStrategyManagerRef = ref<InstanceType<typeof AiChunkStrategyManager> | null>(null)
const indexActionDisabled = computed(
  () => Boolean(vectorizingId.value) || Boolean(deletingId.value) || clearingIndex.value
    || rebuilding.value || syncing.value || !selectedBase.value?.enabled
)

function handleIndexCommand(command: string) {
  if (command === 'rebuild') void rebuildIndex()
  else if (command === 'sync') void syncNewContent()
  else if (command === 'strategy') chunkStrategyManagerRef.value?.open()
  else if (command === 'clear') void clearVectorIndex()
}

watch(routeKnowledgeBaseId, (routeId) => {
  selectedKnowledgeBaseId.value = routeId
  void loadAll()
  void restoreRebuildTask()
})

onMounted(async () => {
  await loadAll()
  void restoreRebuildTask()
})

onBeforeUnmount(() => {
  stopRebuildPolling()
  stopSyncPolling()
})
</script>

<style scoped>
.knowledge-document-page { width: 100%; max-width: none; margin: 0; }
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
.source-name { margin-left: 8px; color: var(--el-text-color-secondary); }
.filter-toolbar { display: grid; grid-template-columns: minmax(240px, 1fr) auto 160px auto; gap: 12px; margin-bottom: 12px; }
.document-list-table { width: 100%; }
.pagination { display: flex; justify-content: flex-end; margin-top: 16px; }
.action-more-btn { font-weight: 700; color: var(--teal); }
.action-more-btn:hover { color: var(--teal-hover); }
:global(.knowledge-document-operation-menu .el-dropdown-menu__item.is-danger:not(.is-disabled)) { color: var(--el-color-danger); }
:global(.knowledge-document-operation-menu .el-dropdown-menu__item.is-danger:not(.is-disabled):hover) {
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
}
:global(.document-detail-drawer .el-drawer__header) { margin-bottom: 16px; }
:global(.document-detail-drawer .el-drawer__body) {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding-top: 0;
}
.detail-content { display: flex; flex-direction: column; flex: 1; min-height: 0; }
.detail-content :deep(.el-tabs) { flex: 1; min-height: 0; display: flex; flex-direction: column; }
.detail-content :deep(.el-tabs__content) { flex: 1; min-height: 0; }
.detail-content :deep(.el-tab-pane) { height: 100%; }
.detail-meta { display: flex; flex-wrap: wrap; gap: 12px; align-items: center; color: var(--el-text-color-secondary); flex-shrink: 0; margin-bottom: 12px; }
.vectorize-dialog-tip { margin: 0 0 14px; color: var(--el-text-color-secondary); font-size: 13px; line-height: 1.6; }
.document-content { height: 100%; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; }
.original-pane { height: 100%; display: flex; flex-direction: column; }
.original-pane .document-content { height: auto; flex: 1; min-height: 0; }
.original-footer {
  padding: 6px 16px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
  display: flex;
  gap: 12px;
  justify-content: center;
}
.original-error { color: var(--el-color-danger); }
.document-markdown { white-space: normal; }
.document-markdown :deep(:first-child) { margin-top: 0; }
.document-markdown :deep(:last-child) { margin-bottom: 0; }
.document-markdown :deep(p) { margin: 8px 0 12px; }
.document-markdown :deep(h1), .document-markdown :deep(h2), .document-markdown :deep(h3),
.document-markdown :deep(h4), .document-markdown :deep(h5), .document-markdown :deep(h6) {
  margin: 18px 0 8px;
  color: var(--el-text-color-primary);
  line-height: 1.45;
  font-weight: 700;
}
.document-markdown :deep(h1) { font-size: 1.75em; }
.document-markdown :deep(h2) { font-size: 1.45em; }
.document-markdown :deep(h3) { font-size: 1.25em; }
.document-markdown :deep(h4), .document-markdown :deep(h5), .document-markdown :deep(h6) { font-size: 1.1em; }
.document-markdown :deep(ul), .document-markdown :deep(ol) { margin: 8px 0 12px; padding-left: 24px; }
.document-markdown :deep(li) { margin: 3px 0; }
.document-markdown :deep(li > p) { margin: 4px 0; }
.document-markdown :deep(blockquote) {
  margin: 10px 0;
  padding: 4px 12px;
  border-left: 3px solid var(--el-color-primary);
  color: var(--el-text-color-secondary);
}
.document-markdown :deep(hr) { margin: 16px 0; border: 0; border-top: 1px solid var(--el-border-color); }
.document-markdown :deep(pre) {
  overflow-x: auto;
  margin: 10px 0;
  padding: 12px;
  border-radius: 8px;
  background: var(--el-fill-color-light);
  line-height: 1.6;
}
.document-markdown :deep(pre code) { padding: 0; background: transparent; }
.document-markdown :deep(code) {
  padding: 2px 5px;
  border-radius: 4px;
  background: var(--el-fill-color-light);
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  font-size: .92em;
}
.document-markdown :deep(a) { color: var(--el-color-primary); text-decoration: underline; }
.document-markdown :deep(table) { display: block; max-width: 100%; overflow-x: auto; border-collapse: collapse; margin: 10px 0; }
.document-markdown :deep(th), .document-markdown :deep(td) {
  min-width: 100px;
  padding: 6px 10px;
  border: 1px solid var(--el-border-color);
  text-align: left;
}
.document-markdown :deep(th) { background: var(--el-fill-color-light); }
.document-markdown :deep(img) { max-width: 100%; height: auto; }
.chunk-list { margin-top: 4px; height: 100%; overflow-y: auto; }
.chunk-count { margin-left: 8px; }
.chunk-content { max-height: 320px; overflow: auto; margin: 0; overflow-wrap: anywhere; }
.chunk-content-hint { color: var(--el-text-color-secondary); font-size: 13px; }
@media (max-width: 900px) {
  .stats-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .filter-toolbar { grid-template-columns: 1fr; }
  .page-heading { flex-direction: column; }
}
</style>
