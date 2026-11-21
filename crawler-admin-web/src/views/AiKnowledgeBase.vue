<template>
  <section class="knowledge-page">
    <header class="page-heading">
      <div>
        <h1>知识库管理</h1>
        <p>管理爬虫文档在 RAG 知识库中的收录和向量索引。排除文档不会从爬虫源索引或普通搜索中删除。</p>
      </div>
      <div class="heading-actions">
        <el-tag :type="stats.enabled ? 'success' : 'info'" effect="light">
          {{ stats.enabled ? 'RAG 已启用' : 'RAG 已停用' }}
        </el-tag>
        <el-button :icon="Setting" @click="configVisible = true">RAG 配置</el-button>
        <el-button :icon="Refresh" :loading="refreshing" @click="loadAll">刷新</el-button>
        <el-button
          type="primary"
          :icon="RefreshRight"
          :loading="rebuilding"
          :disabled="Boolean(vectorizingId) || !stats.enabled"
          @click="rebuildIndex"
        >
          批量重建向量索引
        </el-button>
      </div>
    </header>

    <el-dialog
      v-model="configVisible"
      title="RAG 配置"
      width="min(1180px, 96vw)"
      top="3vh"
      class="rag-config-dialog"
    >
      <AiConfig embedded />
    </el-dialog>

    <div v-loading="statsLoading" class="stats-grid">
      <el-card shadow="never">
        <span>源文档</span>
        <strong>{{ stats.sourceDocuments }}</strong>
      </el-card>
      <el-card shadow="never">
        <span>已收录文档</span>
        <strong>{{ stats.includedDocuments }}</strong>
      </el-card>
      <el-card shadow="never">
        <span>已排除文档</span>
        <strong>{{ stats.excludedDocuments }}</strong>
      </el-card>
      <el-card shadow="never">
        <span>向量分块</span>
        <strong>{{ stats.vectorDocuments }}</strong>
      </el-card>
    </div>

    <div class="index-info">
      <span>源索引 <code>{{ stats.sourceIndex || '-' }}</code></span>
      <span>向量索引 <code>{{ stats.vectorIndex || '-' }}</code></span>
    </div>

    <el-card shadow="never" class="document-card">
      <div class="filter-toolbar">
        <el-input
          v-model="keyword"
          clearable
          :prefix-icon="Search"
          placeholder="搜索标题、正文、来源 URL"
          @keyup.enter="applySearch"
        />
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
            <el-button
              link
              type="primary"
              :loading="vectorizingId === row.id"
              :disabled="Boolean(vectorizingId) || rebuilding || !stats.enabled"
              @click="vectorizeDocument(row)"
            >
              {{ row.excluded ? '重新纳入' : '重新向量化' }}
            </el-button>
            <el-button
              v-if="!row.excluded"
              link
              type="danger"
              :disabled="Boolean(vectorizingId) || rebuilding"
              @click="excludeDocument(row)"
            >
              排除
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="没有找到知识库文档" />
        </template>
      </el-table>

      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        class="pagination"
        @change="loadDocuments"
      />
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
        <pre v-if="detail" class="document-content">{{ detail.content || '暂无正文内容' }}</pre>
      </div>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, RefreshRight, Search, Setting } from '@element-plus/icons-vue'
import AiConfig from '@/views/AiConfig.vue'
import {
  excludeKnowledgeBaseDocument, getKnowledgeBaseDocument, getKnowledgeBaseDocuments,
  getKnowledgeBaseStats, syncRagIndex, vectorizeKnowledgeBaseDocument
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

interface KnowledgeDocumentDetail {
  id: string
  title: string
  content: string
  url: string
  spiderName: string
  spiderId: number | null
  excluded: boolean
}

interface KnowledgeStats {
  enabled: boolean
  sourceIndex: string
  vectorIndex: string
  sourceDocuments: number
  includedDocuments: number
  excludedDocuments: number
  vectorDocuments: number
}

const emptyStats = (): KnowledgeStats => ({
  enabled: false,
  sourceIndex: '',
  vectorIndex: '',
  sourceDocuments: 0,
  includedDocuments: 0,
  excludedDocuments: 0,
  vectorDocuments: 0
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
const configVisible = ref(false)

const loadStats = async () => {
  statsLoading.value = true
  try {
    const response: any = await getKnowledgeBaseStats()
    if (response) stats.value = response.data
  } catch {
    ElMessage.error('读取知识库状态失败')
  } finally {
    statsLoading.value = false
  }
}

const loadDocuments = async () => {
  loading.value = true
  try {
    const response: any = await getKnowledgeBaseDocuments({
      page: page.value,
      pageSize: pageSize.value,
      keyword: keyword.value,
      includeExcluded: includeExcluded.value
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

const loadAll = async () => {
  refreshing.value = true
  try {
    await Promise.all([loadStats(), loadDocuments()])
  } finally {
    refreshing.value = false
  }
}

const applySearch = () => {
  page.value = 1
  void loadDocuments()
}

const showDetail = async (row: KnowledgeDocument) => {
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  try {
    const response: any = await getKnowledgeBaseDocument(row.id)
    if (response) detail.value = response.data
  } catch {
    ElMessage.error('读取文档详情失败')
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

const vectorizeDocument = async (row: KnowledgeDocument) => {
  vectorizingId.value = row.id
  try {
    const response: any = await vectorizeKnowledgeBaseDocument(row.id)
    if (!response) return
    ElMessage.success(`文档向量化完成，共生成 ${response.data.chunks} 个分块`)
    await loadAll()
    if (detail.value?.id === row.id) await showDetail(row)
  } catch {
    ElMessage.error('文档向量化失败，请检查 Embedding 模型服务')
  } finally {
    vectorizingId.value = ''
  }
}

const excludeDocument = async (row: KnowledgeDocument) => {
  try {
    await ElMessageBox.confirm(
      '该文档会从 RAG 知识库及后续批量重建中排除，但保留在爬虫源索引和普通搜索中。仍可重新纳入。',
      '排除知识库文档',
      { type: 'warning', confirmButtonText: '确认排除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }

  vectorizingId.value = row.id
  try {
    await excludeKnowledgeBaseDocument(row.id)
    ElMessage.success('文档已从知识库排除')
    await loadAll()
  } catch {
    ElMessage.error('排除知识库文档失败')
  } finally {
    vectorizingId.value = ''
  }
}

const rebuildIndex = async () => {
  try {
    await ElMessageBox.confirm(
      '将按当前已保存配置重建向量索引。已排除文档不会重新收录，重建期间请勿重复触发。',
      '批量重建知识库',
      { type: 'warning', confirmButtonText: '开始重建', cancelButtonText: '取消' }
    )
  } catch {
    return
  }

  rebuilding.value = true
  try {
    const response: any = await syncRagIndex()
    if (!response) return
    const result = response.data
    ElMessage.success(
      `知识库重建完成：${result.sourceDocuments} 篇源文档，${result.chunks} 个分块，${result.vectorDocuments} 个向量文档`
    )
    await loadAll()
  } catch {
    ElMessage.error('知识库重建失败，请检查 RAG 配置、ES 源索引及向量模型服务')
  } finally {
    rebuilding.value = false
  }
}

onMounted(loadAll)
</script>

<style scoped>
.knowledge-page { max-width: 1440px; margin: 0 auto; }
.page-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; margin-bottom: 20px; }
.page-heading h1 { margin: 0; color: var(--ink-900); font-size: 22px; }
.page-heading p { max-width: 800px; margin: 7px 0 0; color: var(--ink-500); font-size: 13px; line-height: 1.6; }
.heading-actions { display: flex; flex: 0 0 auto; gap: 10px; }
:global(.rag-config-dialog .el-dialog__body) { max-height: 82vh; overflow: auto; padding-top: 8px; }
.stats-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; }
.stats-grid :deep(.el-card__body) { display: flex; align-items: center; justify-content: space-between; }
.stats-grid span { color: var(--ink-500); font-size: 13px; }
.stats-grid strong { color: var(--ink-900); font-size: 23px; }
.index-info { display: flex; flex-wrap: wrap; gap: 14px 28px; margin: 14px 2px; color: var(--ink-500); font-size: 12px; }
.index-info code { margin-left: 5px; color: var(--ink-800); }
.document-card { border-radius: 12px; }
.filter-toolbar { display: flex; align-items: center; gap: 12px; margin-bottom: 16px; }
.filter-toolbar :deep(.el-input) { max-width: 420px; }
.filter-toolbar .el-checkbox { margin-left: auto; }
.pagination { justify-content: flex-end; margin-top: 18px; }
.detail-content { min-height: 120px; }
.detail-meta { display: flex; flex-wrap: wrap; align-items: center; gap: 8px 18px; margin-bottom: 16px; color: var(--ink-500); font-size: 12px; overflow-wrap: anywhere; }
.detail-meta a { color: var(--teal-ink); }
.document-content { max-height: 55vh; overflow: auto; margin: 0; padding: 16px; border-radius: 8px; background: var(--chrome-hover); color: var(--ink-800); font: inherit; line-height: 1.7; white-space: pre-wrap; overflow-wrap: anywhere; }
@media (max-width: 760px) {
  .page-heading { flex-direction: column; }
  .heading-actions { width: 100%; }
  .stats-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .filter-toolbar { flex-wrap: wrap; }
  .filter-toolbar :deep(.el-input) { max-width: none; }
  .filter-toolbar .el-checkbox { margin-left: 0; }
}
</style>
