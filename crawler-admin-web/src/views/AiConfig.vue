<template>
  <div class="ai-config-page">
    <el-card class="config-card">
      <template #header>
        <div class="card-header">
          <div>
            <h2>AI / RAG 配置</h2>
            <p>当前环境：{{ config.environment || '-' }}。修改保存后立即生效。</p>
          </div>
          <el-tag :type="form.enabled ? 'success' : 'info'" effect="light">
            {{ form.enabled ? 'RAG 已启用' : 'RAG 已停用' }}
          </el-tag>
        </div>
      </template>

      <el-alert
        title="密钥安全"
        description="API Key 只写入数据库，不会在读取配置时返回。留空表示保留当前密钥；勾选清除后会删除已保存密钥。"
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
            <h3>向量模型（Embedding）</h3>
            <span>用于生成文档与问题的向量</span>
          </div>
          <div class="form-grid">
            <el-form-item label="服务提供方" prop="embeddingProvider">
              <el-select v-model="form.embeddingProvider">
                <el-option label="Ollama 原生 API" value="ollama" />
                <el-option label="OpenAI 兼容 API" value="openai" />
              </el-select>
            </el-form-item>
            <el-form-item label="服务地址" prop="embeddingBaseUrl">
              <el-input v-model="form.embeddingBaseUrl" placeholder="http://host:11434" />
            </el-form-item>
            <el-form-item label="模型名称" prop="embeddingModel">
              <el-input v-model="form.embeddingModel" placeholder="bge-m3" />
            </el-form-item>
            <el-form-item label="向量维度" prop="dimensions">
              <el-input-number v-model="form.dimensions" :min="1" :max="65536" controls-position="right" />
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
            <el-form-item label="服务提供方" prop="chatProvider">
              <el-select v-model="form.chatProvider">
                <el-option label="OpenAI 兼容 API" value="openai" />
                <el-option label="Ollama 原生 API" value="ollama" />
              </el-select>
            </el-form-item>
            <el-form-item label="服务地址" prop="chatBaseUrl">
              <el-input v-model="form.chatBaseUrl" placeholder="http://host:port/v1" />
            </el-form-item>
            <el-form-item label="模型名称" prop="chatModel">
              <el-input v-model="form.chatModel" placeholder="Qwen3.8-27B" />
            </el-form-item>
            <el-form-item label="API Key">
              <el-input
                v-model="form.apiKey"
                type="password"
                show-password
                autocomplete="new-password"
                :placeholder="config.apiKeyConfigured ? '已保存密钥；留空保持不变' : '可选；无密钥时留空'"
                :disabled="form.clearApiKey"
              />
              <el-checkbox v-model="form.clearApiKey" :disabled="!config.apiKeyConfigured">
                清除已保存的 API Key
              </el-checkbox>
            </el-form-item>
          </div>
        </section>

        <el-divider />

        <section class="config-section">
          <div class="section-title">
            <h3>检索与分块</h3>
            <span>控制召回数量和文本切分参数</span>
          </div>
          <div class="form-grid">
            <el-form-item label="Top K" prop="topK">
              <el-input-number v-model="form.topK" :min="1" :max="20" controls-position="right" />
            </el-form-item>
            <el-form-item label="分块长度（字符）" prop="chunkSize">
              <el-input-number v-model="form.chunkSize" :min="200" :max="20000" :step="100" controls-position="right" />
            </el-form-item>
            <el-form-item label="分块重叠（字符）" prop="chunkOverlap">
              <el-input-number v-model="form.chunkOverlap" :min="0" :max="19999" :step="50" controls-position="right" />
            </el-form-item>
            <el-form-item label="向量索引前缀" prop="vectorIndexPrefix">
              <el-input v-model="form.vectorIndexPrefix" />
            </el-form-item>
            <el-form-item label="向量字段前缀" prop="vectorFieldPrefix">
              <el-input v-model="form.vectorFieldPrefix" />
            </el-form-item>
          </div>
        </section>

        <div class="form-actions">
          <el-button :disabled="loading || saving" @click="loadConfig">重新加载</el-button>
          <el-button type="primary" :loading="saving" :disabled="loading" @click="saveConfig">
            保存配置
          </el-button>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { getRagConfig, updateRagConfig } from '@/api'

interface RagConfig {
  environment: string
  enabled: boolean
  sourceIndex: string
  embeddingProvider: string
  embeddingBaseUrl: string
  embeddingModel: string
  dimensions: number
  chatProvider: string
  chatBaseUrl: string
  chatModel: string
  apiKeyConfigured: boolean
  topK: number
  chunkSize: number
  chunkOverlap: number
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
  chatProvider: 'openai',
  chatBaseUrl: '',
  chatModel: '',
  apiKeyConfigured: false,
  topK: 5,
  chunkSize: 1500,
  chunkOverlap: 200,
  vectorIndexPrefix: '',
  vectorFieldPrefix: '',
  vectorIndex: '',
  vectorField: ''
})

const config = reactive<RagConfig>(emptyConfig())
const form = reactive({
  enabled: false,
  sourceIndex: '',
  embeddingProvider: 'ollama',
  embeddingBaseUrl: '',
  embeddingModel: '',
  dimensions: 1024,
  chatProvider: 'openai',
  chatBaseUrl: '',
  chatModel: '',
  apiKey: '',
  clearApiKey: false,
  topK: 5,
  chunkSize: 1500,
  chunkOverlap: 200,
  vectorIndexPrefix: '',
  vectorFieldPrefix: ''
})
const formRef = ref<FormInstance>()
const loading = ref(false)
const saving = ref(false)

const validUrl = (_rule: unknown, value: string, callback: (error?: Error) => void) => {
  try {
    const url = new URL(value)
    if (url.protocol === 'http:' || url.protocol === 'https:') return callback()
  } catch {
    // Validation below reports malformed URLs.
  }
  callback(new Error('请输入有效的 HTTP 或 HTTPS 地址'))
}

const rules: FormRules = {
  sourceIndex: [{ required: true, message: '请输入 ES 源索引', trigger: 'blur' }, { pattern: /^[a-z0-9][a-z0-9._-]*$/, message: '索引名称格式不合法', trigger: 'blur' }],
  embeddingProvider: [{ required: true, message: '请选择向量模型提供方', trigger: 'change' }],
  embeddingBaseUrl: [{ required: true, validator: validUrl, trigger: 'blur' }],
  embeddingModel: [{ required: true, message: '请输入向量模型名称', trigger: 'blur' }],
  dimensions: [{ required: true, type: 'number', min: 1, message: '向量维度必须大于 0', trigger: 'change' }],
  chatProvider: [{ required: true, message: '请选择聊天模型提供方', trigger: 'change' }],
  chatBaseUrl: [{ required: true, validator: validUrl, trigger: 'blur' }],
  chatModel: [{ required: true, message: '请输入聊天模型名称', trigger: 'blur' }],
  topK: [{ required: true, type: 'number', min: 1, max: 20, message: 'Top K 范围为 1–20', trigger: 'change' }],
  chunkSize: [{ required: true, type: 'number', min: 200, message: '分块长度至少为 200', trigger: 'change' }],
  chunkOverlap: [{
    required: true,
    type: 'number',
    validator: (_rule, value, callback) => {
      if (value >= 0 && value < form.chunkSize) callback()
      else callback(new Error('重叠长度必须小于分块长度'))
    },
    trigger: 'change'
  }],
  vectorIndexPrefix: [{ required: true, pattern: /^[a-z0-9_-]+$/, message: '仅支持小写字母、数字、下划线和连字符', trigger: 'blur' }],
  vectorFieldPrefix: [{ required: true, pattern: /^[a-zA-Z0-9_-]+$/, message: '仅支持字母、数字、下划线和连字符', trigger: 'blur' }]
}

const applyConfig = (data: RagConfig) => {
  Object.assign(config, data)
  Object.assign(form, {
    enabled: data.enabled,
    sourceIndex: data.sourceIndex,
    embeddingProvider: data.embeddingProvider,
    embeddingBaseUrl: data.embeddingBaseUrl,
    embeddingModel: data.embeddingModel,
    dimensions: data.dimensions,
    chatProvider: data.chatProvider,
    chatBaseUrl: data.chatBaseUrl,
    chatModel: data.chatModel,
    apiKey: '',
    clearApiKey: false,
    topK: data.topK,
    chunkSize: data.chunkSize,
    chunkOverlap: data.chunkOverlap,
    vectorIndexPrefix: data.vectorIndexPrefix,
    vectorFieldPrefix: data.vectorFieldPrefix
  })
}

const loadConfig = async () => {
  loading.value = true
  try {
    const response: any = await getRagConfig()
    if (!response) return
    applyConfig(response.data)
  } catch {
    ElMessage.error('加载 AI/RAG 配置失败')
  } finally {
    loading.value = false
  }
}

const saveConfig = async () => {
  if (!await formRef.value?.validate().catch(() => false)) return
  saving.value = true
  const payload: Record<string, unknown> = {
    enabled: form.enabled,
    sourceIndex: form.sourceIndex,
    embeddingProvider: form.embeddingProvider,
    embeddingBaseUrl: form.embeddingBaseUrl,
    embeddingModel: form.embeddingModel,
    dimensions: form.dimensions,
    chatProvider: form.chatProvider,
    chatBaseUrl: form.chatBaseUrl,
    chatModel: form.chatModel,
    topK: form.topK,
    chunkSize: form.chunkSize,
    chunkOverlap: form.chunkOverlap,
    vectorIndexPrefix: form.vectorIndexPrefix,
    vectorFieldPrefix: form.vectorFieldPrefix,
    clearApiKey: form.clearApiKey
  }
  if (form.apiKey) payload.apiKey = form.apiKey
  try {
    const response: any = await updateRagConfig(payload)
    if (!response) return
    applyConfig(response.data)
    ElMessage.success('AI/RAG 配置已保存并生效')
  } catch {
    ElMessage.error('保存 AI/RAG 配置失败')
  } finally {
    saving.value = false
  }
}

onMounted(loadConfig)
</script>

<style scoped>
.ai-config-page { max-width: 1180px; margin: 0 auto; }
.config-card { border-radius: 12px; }
.card-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
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
@media (max-width: 680px) {
  .form-grid { grid-template-columns: 1fr; }
  .section-title { display: block; }
  .section-title span { display: block; margin-top: 5px; }
  .index-preview { grid-template-columns: 1fr; }
}
</style>
