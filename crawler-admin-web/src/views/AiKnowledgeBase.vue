<template>
  <section class="knowledge-page">
    <header class="page-heading">
      <div>
        <h1>知识库管理</h1>
        <p>新建和维护知识库，配置源索引、向量索引及向量模型。</p>
      </div>
      <div class="heading-actions">
        <el-button :icon="Plus" @click="openDocumentManagement">文档管理</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建知识库</el-button>
      </div>
    </header>

    <el-card shadow="never" v-loading="loading">
      <el-table :data="knowledgeBases" stripe>
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="description" label="简介" min-width="220" show-overflow-tooltip />
        <el-table-column prop="vectorModelName" label="向量模型" min-width="180" show-overflow-tooltip />
        <el-table-column prop="sourceIndex" label="源索引" min-width="160" show-overflow-tooltip />
        <el-table-column prop="vectorIndex" label="向量索引" min-width="160" show-overflow-tooltip />
        <el-table-column prop="vectorField" label="向量字段" min-width="160" show-overflow-tooltip />
        <el-table-column label="来源类型" width="120">
          <template #default="{ row }">
            {{ row.sourceType === 'UPLOAD' ? '直接上传' : 'ES 索引' }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'info'">
              {{ row.enabled ? '已启用' : '已停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="80" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="success" @click="openKnowledgeDocuments(row)">文档管理</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="removeKnowledgeBase(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑知识库' : '新建知识库'" width="680px">
      <el-form :model="form" label-position="top">
        <el-form-item label="知识库名称" required>
          <el-input v-model="form.name" maxlength="64" placeholder="例如：产品文档知识库" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="form.description" maxlength="512" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="文档来源类型" required>
          <el-select v-model="form.sourceType" style="width:100%">
            <el-option label="通过 ES 索引" value="ES_INDEX" />
            <el-option label="直接上传文档" value="UPLOAD" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.sourceType === 'ES_INDEX'" label="源索引" required>
          <el-input v-model="form.sourceIndex" placeholder="例如：spider_content" />
        </el-form-item>
        <el-form-item v-if="form.sourceType === 'UPLOAD'" label="MinIO Bucket">
          <el-input v-model="form.uploadBucket" placeholder="crawler-knowledge" />
        </el-form-item>
        <el-form-item label="向量模型" required>
          <el-select v-model="form.vectorModelId" style="width:100%" placeholder="请选择向量模型">
            <el-option v-for="model in vectorModels" :key="model.id" :label="model.name" :value="model.id" />
          </el-select>
          <el-descriptions
            v-if="selectedVectorModel"
            :column="2"
            border
            size="small"
            class="selected-model-config"
          >
            <el-descriptions-item label="服务提供方">
              {{ selectedVectorModel.embeddingProvider === 'ollama' ? 'Ollama' : 'OpenAI 兼容' }}
            </el-descriptions-item>
            <el-descriptions-item label="模型名称">
              {{ selectedVectorModel.embeddingModel }}
            </el-descriptions-item>
            <el-descriptions-item label="服务地址">
              {{ selectedVectorModel.embeddingBaseUrl }}
            </el-descriptions-item>
            <el-descriptions-item label="API Key">
              {{ selectedVectorModel.apiKeyConfigured ? '已配置' : '未配置' }}
            </el-descriptions-item>
            <el-descriptions-item label="向量维度">
              {{ selectedVectorModel.dimensions }}
            </el-descriptions-item>
            <el-descriptions-item label="Top K">
              {{ selectedVectorModel.topK }}
            </el-descriptions-item>
            <el-descriptions-item label="分块长度">
              {{ selectedVectorModel.chunkSize }} 字符
            </el-descriptions-item>
            <el-descriptions-item label="分块重叠">
              {{ selectedVectorModel.chunkOverlap }} 字符
            </el-descriptions-item>
          </el-descriptions>
        </el-form-item>
        <el-form-item label="向量索引" required>
          <el-input v-model="form.vectorIndex" placeholder="例如：product_docs_vector" />
        </el-form-item>
        <el-form-item label="向量字段" required>
          <el-input v-model="form.vectorField" placeholder="例如：embedding_bge_m3" />
        </el-form-item>
        <div class="form-grid">
          <el-form-item label="排序">
            <el-input-number v-model="form.sort" :min="0" :max="999" controls-position="right" />
          </el-form-item>
          <el-form-item label="状态">
            <el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { useRouter } from 'vue-router'
import {
  createKnowledgeBase, deleteKnowledgeBase, getKnowledgeBases,
  getVectorModels, updateKnowledgeBase
} from '@/api'

interface VectorModel {
  id: string
  name: string
  embeddingProvider: string
  embeddingBaseUrl: string
  embeddingModel: string
  apiKeyConfigured: boolean
  dimensions: number
  topK: number
  chunkSize: number
  chunkOverlap: number
}

const knowledgeBases = ref<any[]>([])
const router = useRouter()
const vectorModels = ref<VectorModel[]>([])
const selectedVectorModel = computed(() =>
  vectorModels.value.find(model => model.id === form.value.vectorModelId)
)
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref('')
const form = ref({
  name: '',
  description: '',
  enabled: true,
  vectorModelId: '',
  sourceIndex: 'spider_content',
  vectorIndex: 'spider_content_vector',
  vectorField: 'embedding',
  sourceType: 'ES_INDEX',
  uploadBucket: 'crawler-knowledge',
  sort: 0
})

async function loadAll() {
  loading.value = true
  try {
    const [baseResponse, vectorResponse] = await Promise.all([getKnowledgeBases(), getVectorModels()])
    knowledgeBases.value = baseResponse.data || []
    vectorModels.value = vectorResponse.data || []
  } finally {
    loading.value = false
  }
}

function openDocumentManagement() {
  router.push('/ai-knowledge-documents')
}

function openKnowledgeDocuments(row: any) {
  router.push({
    name: 'AiKnowledgeDocumentsById',
    params: { knowledgeBaseId: String(row.id) }
  })
}

function openCreate() {
  editingId.value = ''
  form.value = {
    name: '', description: '', enabled: true, vectorModelId: '',
    sourceIndex: 'spider_content', vectorIndex: 'spider_content_vector',
    vectorField: 'embedding', sourceType: 'ES_INDEX',
    uploadBucket: 'crawler-knowledge', sort: 0
  }
  dialogVisible.value = true
}

function openEdit(row: any) {
  editingId.value = row.id
  form.value = {
    name: row.name,
    description: row.description || '',
    enabled: row.enabled,
    vectorModelId: row.vectorModelId,
    sourceIndex: row.sourceIndex,
    vectorIndex: row.vectorIndex,
    vectorField: row.vectorField,
    sourceType: row.sourceType || 'ES_INDEX',
    uploadBucket: row.uploadBucket || 'crawler-knowledge',
    sort: row.sort
  }
  dialogVisible.value = true
}

async function save() {
  if (!form.value.name.trim()) return ElMessage.warning('请输入知识库名称')
  if (!form.value.vectorModelId
      || (form.value.sourceType === 'ES_INDEX' && !form.value.sourceIndex.trim())
      || !form.value.vectorIndex.trim() || !form.value.vectorField.trim()) {
    return ElMessage.warning('请完整填写向量模型和索引配置')
  }
  saving.value = true
  try {
    if (editingId.value) await updateKnowledgeBase(editingId.value, form.value)
    else await createKnowledgeBase(form.value)
    ElMessage.success('保存成功')
    dialogVisible.value = false
    await loadAll()
  } finally {
    saving.value = false
  }
}

async function removeKnowledgeBase(row: any) {
  await ElMessageBox.confirm(`确定删除知识库“${row.name}”？`, '删除知识库', { type: 'warning' })
  await deleteKnowledgeBase(row.id)
  ElMessage.success('删除成功')
  await loadAll()
}

onMounted(loadAll)
</script>

<style scoped>
.knowledge-page { max-width: 1440px; margin: 0 auto; }
.page-heading { display: flex; align-items: center; justify-content: space-between; gap: 20px; margin-bottom: 16px; }
.page-heading h1 { margin: 0 0 6px; }
.page-heading p { margin: 0; color: var(--el-text-color-secondary); }
.heading-actions { display: flex; align-items: center; gap: 8px; }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.selected-model-config { width: 100%; margin-top: 12px; }
</style>
