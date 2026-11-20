<template>
  <div class="ai-config-page" :class="{ 'ai-config-page--embedded': embedded }">
    <component
      :is="embedded ? 'div' : 'el-card'"
      v-loading="loading"
      element-loading-text="正在加载 AI/RAG 配置..."
      class="config-card"
      :class="{ 'config-card--embedded': embedded }"
    >
      <div class="card-header">
        <div class="card-heading">
          <div class="card-title-row">
            <h2>AI / RAG 配置</h2>
            <el-tag :type="form.enabled ? 'success' : 'info'" effect="light">
              {{ form.enabled ? 'RAG 已启用' : 'RAG 已停用' }}
            </el-tag>
          </div>
          <p>当前环境：{{ config.environment || '-' }}。修改保存后立即生效。</p>
        </div>
      </div>

      <el-alert
        title="密钥安全"
        description="聊天模型的 API Key 按模型配置单独保存，不会在读取配置时返回。请在聊天模型管理中维护密钥。"
        type="info"
        :closable="false"
        show-icon
        class="config-alert"
      />

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        class="config-form"
        @submit.prevent
      >
        <section class="config-section">
          <div class="section-title">
            <h3>RAG 状态与数据源</h3>
            <span>控制索引同步和问答功能</span>
          </div>
          <div class="form-grid">
            <el-form-item label="启用 RAG">
              <el-switch v-model="form.enabled" active-text="启用" inactive-text="停用" />
            </el-form-item>
            <el-form-item label="ES 源索引" prop="sourceIndex">
              <el-input v-model="form.sourceIndex" placeholder="spider_content" />
            </el-form-item>
          </div>
        </section>

        <el-divider />

        <section class="config-section">
          <div class="section-title">
            <h3>向量模型</h3>
            <span>从已保存的模型配置中选择</span>
          </div>
          <div class="form-grid">
            <el-form-item label="引用向量模型" prop="vectorModelId">
              <el-select v-model="form.vectorModelId" placeholder="请选择向量模型">
                <el-option
                  v-for="model in vectorModels"
                  :key="model.id"
                  :label="`${model.name}（${model.embeddingModel}）`"
                  :value="model.id"
                />
              </el-select>
              <el-button link type="primary" @click="vectorManagerVisible = true">
                管理向量模型
              </el-button>
            </el-form-item>
            <el-form-item label="检索与分块参数">
              <span>Top K {{ selectedVectorModel?.topK ?? config.topK }} · 分块长度 {{ selectedVectorModel?.chunkSize ?? config.chunkSize }} 字符 · 重叠 {{ selectedVectorModel?.chunkOverlap ?? config.chunkOverlap }} 字符</span>
            </el-form-item>
            <el-form-item label="向量索引前缀" prop="vectorIndexPrefix">
              <el-input v-model="form.vectorIndexPrefix" />
            </el-form-item>
            <el-form-item label="向量索引名称" prop="vectorIndexSuffix">
              <el-input v-model="form.vectorIndexSuffix" placeholder="例如 bge_m3">
                <template #prepend>{{ form.vectorIndexPrefix }}_</template>
              </el-input>
            </el-form-item>
            <el-form-item label="向量字段前缀" prop="vectorFieldPrefix">
              <el-input v-model="form.vectorFieldPrefix" />
            </el-form-item>
          </div>
          <div class="index-preview">
            <span>向量索引</span><code>{{ config.vectorIndex || '保存后计算' }}</code>
            <span>向量字段</span><code>{{ config.vectorField || '保存后计算' }}</code>
          </div>
        </section>

        <el-divider />

        <section class="config-section">
          <div class="section-title">
            <h3>聊天模型</h3>
            <span>用于基于检索资料生成最终回答</span>
          </div>
          <div class="form-grid">
            <el-form-item label="引用聊天模型" prop="chatModelId">
              <el-select v-model="form.chatModelId" placeholder="请选择聊天模型">
                <el-option
                  v-for="model in chatModels"
                  :key="model.id"
                  :label="`${model.name}（${model.chatModel}）`"
                  :value="model.id"
                />
              </el-select>
              <el-button link type="primary" @click="chatManagerVisible = true">
                管理聊天模型
              </el-button>
            </el-form-item>
            <el-form-item label="当前模型配置">
              <span v-if="selectedChatModel">
                {{ selectedChatModel.chatProvider === 'ollama' ? 'Ollama' : selectedChatModel.chatProvider === 'vllm' ? 'vLLM' : 'OpenAI 兼容' }} ·
                {{ selectedChatModel.chatBaseUrl }} ·
                API Key {{ selectedChatModel.apiKeyConfigured ? '已配置' : '未配置' }}
              </span>
              <span v-else>请选择聊天模型配置</span>
            </el-form-item>
          </div>
        </section>

        <el-divider />

        <div class="form-actions">
          <el-button :disabled="loading || saving" @click="loadConfig">重新加载</el-button>
          <el-button type="primary" :loading="saving" :disabled="loading" @click="saveConfig">
            保存配置
          </el-button>
        </div>
      </el-form>
    </component>

    <el-dialog
      v-model="vectorManagerVisible"
      title="向量模型管理"
      width="min(1100px, 94vw)"
      top="5vh"
      destroy-on-close
      class="model-manager-dialog"
      @closed="loadVectorModels"
    >
      <AiVectorModels embedded />
    </el-dialog>

    <el-dialog
      v-model="chatManagerVisible"
      title="聊天模型管理"
      width="min(1100px, 94vw)"
      top="5vh"
      destroy-on-close
      class="model-manager-dialog"
      @closed="loadChatModels"
    >
      <AiChatModels embedded />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onActivated, onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { getChatModels, getRagConfig, getVectorModels, updateRagConfig } from '@/api'
import AiChatModels from '@/views/AiChatModels.vue'
import AiVectorModels from '@/views/AiVectorModels.vue'

defineProps<{ embedded?: boolean }>()

interface RagConfig {
  environment: string
  enabled: boolean
  sourceIndex: string
  embeddingProvider: string
  embeddingBaseUrl: string
  embeddingModel: string
  dimensions: number
  vectorModelId: string | null
  chatModelId: string | null
  chatProvider: string
  chatBaseUrl: string
  chatModel: string
  apiKeyConfigured: boolean
  topK: number
  chunkSize: number
  chunkOverlap: number
  vectorIndexName: string
  vectorIndexPrefix: string
  vectorFieldPrefix: string
  vectorIndex: string
  vectorField: string
}

const emptyConfig = (): RagConfig => ({
  environment: '',
  enabled: false,
  sourceIndex: '',
  embeddingProvider: 'ollama',
  embeddingBaseUrl: '',
  embeddingModel: '',
  dimensions: 1024,
  vectorModelId: null,
  chatModelId: null,
  chatProvider: 'openai',
  chatBaseUrl: '',
  chatModel: '',
  apiKeyConfigured: false,
  topK: 5,
  chunkSize: 1500,
  chunkOverlap: 200,
  vectorIndexName: '',
  vectorIndexPrefix: '',
  vectorFieldPrefix: '',
  vectorIndex: '',
  vectorField: ''
})

interface VectorModel {
  id: string
  name: string
  embeddingModel: string
  topK: number
  chunkSize: number
  chunkOverlap: number
}

interface ChatModel {
  id: string
  name: string
  chatProvider: string
  chatBaseUrl: string
  chatModel: string
  apiKeyConfigured: boolean
}

const config = reactive<RagConfig>(emptyConfig())
const vectorModels = ref<VectorModel[]>([])
const chatModels = ref<ChatModel[]>([])
const form = reactive({
  enabled: false,
  sourceIndex: '',
  vectorModelId: null as string | null,
  chatModelId: null as string | null,
  vectorIndexSuffix: '',
  vectorIndexPrefix: '',
  vectorFieldPrefix: ''
})
const selectedVectorModel = computed(() =>
  vectorModels.value.find(model => model.id === form.vectorModelId)
)
const selectedChatModel = computed(() =>
  chatModels.value.find(model => model.id === form.chatModelId)
)
const formRef = ref<FormInstance>()
const loading = ref(false)
const saving = ref(false)
const vectorManagerVisible = ref(false)
const chatManagerVisible = ref(false)

const rules: FormRules = {
  sourceIndex: [{ required: true, message: '请输入 ES 源索引', trigger: 'blur' }, { pattern: /^[a-z0-9][a-z0-9._-]*$/, message: '索引名称格式不合法', trigger: 'blur' }],
  vectorModelId: [{ required: true, message: '请选择向量模型', trigger: 'change' }],
  chatModelId: [{ required: true, message: '请选择聊天模型', trigger: 'change' }],
  vectorIndexSuffix: [{ required: true, pattern: /^[a-z0-9][a-z0-9._-]*$/, message: '索引名称后缀仅支持小写字母、数字、点、下划线和连字符', trigger: 'blur' }],
  vectorIndexPrefix: [{ required: true, pattern: /^[a-z0-9_-]+$/, message: '仅支持小写字母、数字、下划线和连字符', trigger: 'blur' }],
  vectorFieldPrefix: [{ required: true, pattern: /^[a-zA-Z0-9_-]+$/, message: '仅支持字母、数字、下划线和连字符', trigger: 'blur' }]
}

const applyConfig = (data: RagConfig) => {
  Object.assign(config, data)
  const vectorIndexPrefix = data.vectorIndexPrefix || ''
  const vectorIndexName = data.vectorIndexName || data.vectorIndex || ''
  const vectorIndexPrefixWithSeparator = `${vectorIndexPrefix}_`
  const vectorIndexSuffix = vectorIndexName.startsWith(vectorIndexPrefixWithSeparator)
    ? vectorIndexName.slice(vectorIndexPrefixWithSeparator.length)
    : data.embeddingModel.toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_|_$/g, '')
  Object.assign(form, {
    enabled: data.enabled,
    sourceIndex: data.sourceIndex,
    vectorModelId: data.vectorModelId,
    chatModelId: data.chatModelId,
    vectorIndexSuffix,
    vectorIndexPrefix,
    vectorFieldPrefix: data.vectorFieldPrefix
  })
}

const loadConfig = async () => {
  loading.value = true
  try {
    const [configResponse, vectorModelsResponse, chatModelsResponse]: any[] = await Promise.all([
      getRagConfig(),
      getVectorModels(),
      getChatModels()
    ])
    if (!configResponse || !vectorModelsResponse || !chatModelsResponse) return
    vectorModels.value = vectorModelsResponse.data || []
    chatModels.value = chatModelsResponse.data || []
    applyConfig(configResponse.data)
  } catch {
    ElMessage.error('加载 AI/RAG 配置失败')
  } finally {
    loading.value = false
  }
}

const loadVectorModels = async () => {
  try {
    const response: any = await getVectorModels()
    if (response) vectorModels.value = response.data || []
  } catch {
    ElMessage.error('刷新向量模型列表失败')
  }
}

const loadChatModels = async () => {
  try {
    const response: any = await getChatModels()
    if (response) chatModels.value = response.data || []
  } catch {
    ElMessage.error('刷新聊天模型列表失败')
  }
}

const saveConfig = async () => {
  if (!await formRef.value?.validate().catch(() => false)) return
  saving.value = true
  const payload: Record<string, unknown> = {
    enabled: form.enabled,
    sourceIndex: form.sourceIndex,
    vectorModelId: form.vectorModelId,
    chatModelId: form.chatModelId,
    vectorIndex: `${form.vectorIndexPrefix}_${form.vectorIndexSuffix}`,
    vectorIndexPrefix: form.vectorIndexPrefix,
    vectorFieldPrefix: form.vectorFieldPrefix
  }
  try {
    const response: any = await updateRagConfig(payload)
    if (!response) return
    applyConfig(response.data)
    ElMessage.success('AI/RAG 配置已保存并生效')
  } catch (error) {
    if (error instanceof Error && error.message === '所选聊天模型不存在') return
    ElMessage.error('保存 AI/RAG 配置失败')
  } finally {
    saving.value = false
  }
}

let hasActivated = false
onMounted(loadConfig)
onActivated(() => {
  if (hasActivated) loadConfig()
  hasActivated = true
})
</script>

<style scoped>
.ai-config-page { max-width: 1180px; margin: 0 auto; }
.ai-config-page--embedded { max-width: none; margin: 0; }
.config-card { border-radius: 12px; }
.config-card--embedded { padding: 20px 22px; border: 0; border-radius: 0; }
.card-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.card-heading { flex: 1; min-width: 0; }
.card-title-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.card-header h2 { margin: 0; color: var(--ink-900); font-size: 20px; }
.card-header p { margin: 6px 0 0; color: var(--ink-500); font-size: 13px; }
.config-alert { margin-bottom: 24px; }
.config-section { padding: 2px 0; }
.section-title { display: flex; align-items: baseline; gap: 12px; margin-bottom: 18px; }
.section-title h3 { margin: 0; color: var(--ink-800); font-size: 16px; }
.section-title span { color: var(--ink-500); font-size: 12px; }
.form-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 0 24px; }
.form-grid :deep(.el-select), .form-grid :deep(.el-input-number) { width: 100%; }
.index-preview { display: grid; grid-template-columns: 130px 1fr; gap: 10px 14px; align-items: center; padding: 14px 16px; border-radius: 8px; background: var(--chrome-hover); color: var(--ink-600); font-size: 13px; }
.index-preview code { color: var(--ink-800); overflow-wrap: anywhere; }
.form-actions { display: flex; justify-content: flex-end; gap: 10px; padding-top: 20px; }
.config-form :deep(.el-divider) { margin: 24px 0; }
.config-form :deep(.el-form-item__content) { display: block; }
.config-form :deep(.el-checkbox) { display: flex; margin-top: 8px; }
:global(.model-manager-dialog .el-dialog__body) { max-height: 78vh; overflow: auto; }
@media (max-width: 680px) {
  .config-card--embedded { padding: 12px 4px; }
  .form-grid { grid-template-columns: 1fr; }
  .section-title { display: block; }
  .section-title span { display: block; margin-top: 5px; }
  .index-preview { grid-template-columns: 1fr; }
}
</style>
