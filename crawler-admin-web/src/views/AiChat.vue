<template>
  <div class="ai-chat-page">
    <aside class="chat-sidebar">
      <div class="sidebar-heading">
        <div class="brand-mark"><el-icon><MagicStick /></el-icon></div>
        <div>
          <strong>AI 助手</strong>
          <span>知识库问答</span>
        </div>
      </div>

      <el-button class="new-chat-button" type="primary" :loading="creatingConversation" @click="createConversation">
        <el-icon><Plus /></el-icon>
        开始新对话
      </el-button>

      <div class="history-heading">最近对话 <span>{{ conversations.length }}</span></div>
      <div v-if="historyError" class="history-error" role="alert">
        <span>{{ historyError }}</span>
        <button type="button" @click="loadConversations">重试</button>
      </div>
      <div v-if="conversations.length" class="conversation-list">
        <button
          v-for="conversation in conversations"
          :key="conversation.id"
          type="button"
          class="conversation-item"
          :class="{ 'is-active': conversation.id === activeConversationId }"
          @click="selectConversation(conversation.id)"
        >
          <el-icon><ChatDotRound /></el-icon>
          <span class="conversation-title">{{ conversation.title }}</span>
          <span
            class="delete-conversation"
            title="删除对话"
            @click.stop="deleteConversation(conversation.id)"
          ><el-icon><Delete /></el-icon></span>
        </button>
      </div>
      <div v-else-if="loadingConversations" class="history-loading" role="status">
        <el-icon class="thinking-icon"><Loading /></el-icon>
        <span>正在加载历史对话</span>
        <span class="loading-dots" aria-hidden="true"><i /><i /><i /></span>
      </div>
      <div v-else class="history-empty">对话会显示在这里</div>

      <div class="sidebar-footer">
        <el-icon><Cpu /></el-icon>
        <span>回答基于已收录的爬虫内容</span>
      </div>
    </aside>

    <section class="chat-main">
      <header class="chat-header">
        <div>
          <h1>{{ activeConversation?.title || 'AI 知识库助手' }}</h1>
          <p>向已收录的内容提问，答案附带相关来源</p>
        </div>
        <div class="header-actions">
          <el-button class="mobile-new-chat" size="small" :loading="creatingConversation" @click="createConversation">
            <el-icon><Plus /></el-icon>新对话
          </el-button>
          <el-button
            v-if="canConfigureAi"
            class="ai-settings-button"
            plain
            @click="settingsVisible = true"
          >
            <el-icon><Setting /></el-icon>
            <span>AI 配置</span>
          </el-button>
          <div class="model-badge"><span></span> RAG 知识库</div>
        </div>
      </header>

      <el-drawer
        v-model="settingsVisible"
        title="AI 配置"
        size="min(900px, 94vw)"
        destroy-on-close
        class="ai-settings-drawer"
        @closed="loadContextWindow"
      >
        <AiConfig embedded />
      </el-drawer>

      <div v-if="conversations.length > 0" class="mobile-conversations">
        <button
          v-for="conversation in conversations"
          :key="conversation.id"
          type="button"
          class="mobile-conversation"
          :class="{ 'is-active': conversation.id === activeConversationId }"
          @click="selectConversation(conversation.id)"
        >{{ conversation.title }}</button>
      </div>

      <div ref="messageListRef" class="message-list" @scroll="saveScrollPosition">
        <div v-if="loadingConversations || loadingMessages" class="loading-history" role="status">
          <div class="history-loading-indicator">
            <el-icon class="thinking-icon"><Loading /></el-icon>
          </div>
          <strong>正在加载聊天记录</strong>
          <span>马上就好</span>
          <span class="loading-dots" aria-hidden="true"><i /><i /><i /></span>
        </div>
        <div v-else-if="!activeConversation?.messages.length" class="welcome-state">
          <div class="welcome-icon"><el-icon><MagicStick /></el-icon></div>
          <p class="welcome-eyebrow">CRAWLEROS AI</p>
          <h2>你好，有什么可以帮你？</h2>
          <p class="welcome-description">我会从已收录的网页内容中检索信息，并附上答案来源。</p>
          <div class="suggestion-list">
            <button
              v-for="suggestion in suggestions"
              :key="suggestion"
              type="button"
              class="suggestion-card"
              @click="draft = suggestion"
            >
              <span>{{ suggestion }}</span>
              <el-icon><ArrowRight /></el-icon>
            </button>
          </div>
        </div>

        <div v-else class="message-thread">
          <article
            v-for="(message, index) in activeConversation.messages"
            :key="`${activeConversation.id}-${index}`"
            class="message-row"
            :class="`message-${message.role}`"
          >
            <div class="message-avatar">
              <el-icon v-if="message.role === 'assistant'"><MagicStick /></el-icon>
              <el-icon v-else><UserFilled /></el-icon>
            </div>
            <div class="message-content">
              <div v-if="message.role === 'assistant'" class="message-author">CrawlerOS AI</div>
              <div
                v-if="message.role === 'assistant' && message.pending && !message.text"
                class="message-text is-pending"
              >
                {{ loadingMessageText }}
                <span class="loading-dots" aria-hidden="true">
                  <i v-for="dot in 3" :key="dot" />
                </span>
              </div>
              <div
                v-else-if="message.role === 'assistant'"
                class="message-text message-markdown"
                :class="{ 'is-pending': message.pending, 'is-error': message.error }"
              >
                <div v-html="renderMarkdown(displayMessageText(message))" />
                <span v-if="message.pending" class="loading-dots" aria-hidden="true">
                  <i v-for="dot in 3" :key="dot" />
                </span>
              </div>
              <div v-else class="message-text">
                <el-icon v-if="message.pending" class="thinking-icon"><Loading /></el-icon>
                {{ message.text }}
              </div>
              <span v-if="message.status === 'FAILED'" class="message-status-failed">回答失败</span>
              <div v-if="message.sources?.length" class="source-list">
                <div class="source-heading"><el-icon><Link /></el-icon>参考来源 <span>{{ message.sources.length }}</span></div>
                <a
                  v-for="(source, sourceIndex) in message.sources"
                  :key="source.id || `${source.url}-${sourceIndex}`"
                  class="source-card"
                  :href="safeSourceUrl(source.url)"
                  :target="safeSourceUrl(source.url) ? '_blank' : undefined"
                  rel="noopener noreferrer"
                  :class="{ 'is-disabled': !safeSourceUrl(source.url) }"
                  @click="!safeSourceUrl(source.url) && $event.preventDefault()"
                >
                  <span class="source-index">{{ String(sourceIndex + 1).padStart(2, '0') }}</span>
                  <span class="source-copy">
                    <strong>{{ source.title || source.url || '未命名来源' }}</strong>
                    <small>{{ source.spiderName || source.url || 'CrawlerOS 知识库' }}</small>
                  </span>
                  <el-icon v-if="safeSourceUrl(source.url)"><Link /></el-icon>
                </a>
              </div>
            </div>
          </article>
        </div>
      </div>

      <div class="composer-wrap">
        <div class="composer">
          <div v-if="showSlashCommands" class="slash-command-menu" role="listbox" aria-label="AI 命令">
            <button
              v-for="(command, index) in visibleSlashCommands"
              :key="command.name"
              type="button"
              class="slash-command-option"
              :class="{ 'is-active': index === activeSlashCommandIndex }"
              role="option"
              :aria-selected="index === activeSlashCommandIndex"
              @mousedown.prevent
              @click="runSlashCommand(command.name)"
            >
              <span class="slash-command-icon"><el-icon><component :is="command.icon" /></el-icon></span>
              <span class="slash-command-copy">
                <strong>{{ command.command }}</strong>
                <small>{{ command.description }}</small>
              </span>
              <kbd>{{ command.usage }}</kbd>
            </button>
          </div>
          <el-input
            v-model="draft"
            type="textarea"
            :autosize="{ minRows: 1, maxRows: 5 }"
            maxlength="2000"
            resize="none"
            placeholder="输入问题，输入 / 查看 AI 命令..."
            aria-label="输入问题"
            @input="resetSlashCommandSelection"
            @keydown="handleComposerKeydown"
          />
          <div class="composer-toolbar">
            <div class="composer-options">
              <div class="retrieval-control">
                <span>检索数量</span>
                <el-select v-model="topK" size="small" aria-label="检索数量">
                  <el-option v-for="count in retrievalOptions" :key="count" :label="`${count} 条`" :value="count" />
                </el-select>
              </div>
              <div
                v-if="contextWindow > 0"
                class="context-usage"
                :title="`估算当前会话全部成功的历史消息（不含本次问题、系统提示和检索资料）；分母为 AI 配置中所选聊天模型的上下文长度。`"
                aria-label="对话历史上下文使用估算"
              >
                <div class="context-usage-copy">
                  <span>上下文使用估算</span>
                  <strong>约 {{ formatTokenCount(estimatedHistoryTokens) }} / {{ formatTokenCount(contextWindow) }} Token</strong>
                </div>
                <el-progress
                  :percentage="contextUsagePercentage"
                  :show-text="false"
                  :stroke-width="5"
                  :color="contextUsageColor"
                />
                <span v-if="estimatedHistoryTokens > contextWindow" class="context-usage-warning">
                  历史消息估算已超过模型上下文长度
                </span>
              </div>
              <span v-else-if="contextWindowError" class="context-usage-error">上下文用量暂不可用</span>
            </div>
            <div class="send-tools">
              <span class="keyboard-hint">Enter 发送 · Shift + Enter 换行</span>
              <el-button
                class="send-button"
                type="primary"
                :disabled="!draft.trim() || sending"
                :loading="sending"
                aria-label="发送问题"
                @click="handleComposerEnter"
              >
                <el-icon v-if="!sending"><Promotion /></el-icon>
                <span>发送</span>
              </el-button>
            </div>
          </div>
        </div>
        <p class="disclaimer">AI 生成的内容可能不准确，请结合原始来源进行判断。</p>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onActivated, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import {
  chatContextWindow, chatConversationCreate, chatConversationDelete, chatConversationList, chatMessages,
  clearChatMessages, resumeChatMessageStream, streamChatMessage
} from '@/api'
import {
  ArrowRight, ChatDotRound, Cpu, Delete, Help, Link, Loading, MagicStick, Plus,
  Promotion, Refresh, Setting, UserFilled
} from '@element-plus/icons-vue'
import { confirm } from '@/utils/confirm'
import AiConfig from '@/views/AiConfig.vue'
import { useUserStore } from '@/stores/user'

interface SourceReference {
  id: string
  title: string
  url: string
  spiderName: string
  score: number
  snippet: string
}

interface ChatMessage {
  id?: number
  role: 'user' | 'assistant'
  text: string
  status?: 'SUCCESS' | 'FAILED' | 'STREAMING'
  pending?: boolean
  error?: boolean
  sources?: SourceReference[]
}

interface Conversation {
  id: number
  title: string
  messages: ChatMessage[]
  messagesLoaded: boolean
}

const suggestions = [
  '最近收录了哪些值得关注的内容？',
  '帮我总结一下知识库中的主要观点',
  '有哪些内容提到了人工智能的发展？'
]

const conversations = ref<Conversation[]>([])
const activeConversationId = ref<number | null>(null)
const settingsVisible = ref(false)
const userStore = useUserStore()
const canConfigureAi = computed(() => userStore.permissions.includes('ai:config'))
const activeConversation = computed(() =>
  conversations.value.find(conversation => conversation.id === activeConversationId.value)
)
const contextWindow = ref(0)
const contextWindowError = ref(false)
const estimateTokens = (content: string) => {
  let tokens = 0
  let plainCharacters = 0
  for (const character of content) {
    const codePoint = character.codePointAt(0)!
    const isCjk = (codePoint >= 0x4E00 && codePoint <= 0x9FFF)
      || (codePoint >= 0x3400 && codePoint <= 0x4DBF)
      || (codePoint >= 0xF900 && codePoint <= 0xFAFF)
      || (codePoint >= 0x3040 && codePoint <= 0x30FF)
      || (codePoint >= 0xAC00 && codePoint <= 0xD7AF)
    if (isCjk) {
      tokens += Math.ceil(plainCharacters / 4) + 1
      plainCharacters = 0
    } else {
      plainCharacters++
    }
  }
  return tokens + Math.ceil(plainCharacters / 4)
}
const estimatedHistoryTokens = computed(() => {
  const messages = (activeConversation.value?.messages || [])
    .filter(message => message.status === 'SUCCESS' && (message.role === 'user' || message.role === 'assistant'))
  return messages.reduce((total, message) => total + estimateTokens(message.text) + 4, 0)
})
const contextUsagePercentage = computed(() => contextWindow.value
  ? Math.min(100, Math.round(estimatedHistoryTokens.value / contextWindow.value * 100))
  : 0)
const contextUsageColor = computed(() =>
  estimatedHistoryTokens.value > contextWindow.value
    ? '#f56c6c'
    : contextUsagePercentage.value >= 90 ? '#e6a23c' : '#0f9b8e'
)
const formatTokenCount = (value: number) => value.toLocaleString()
const draft = ref('')
const topK = ref(5)
const retrievalOptions = computed(() => [...new Set([3, 5, 8, 10, topK.value])].sort((a, b) => a - b))
const sending = ref(false)
const streamConnections = new Map<number, Promise<void>>()
const connectedStreamIds = new Set<number>()
const activeSlashCommandIndex = ref(0)
const slashCommandsDismissed = ref(false)
const slashCommands = [
  { name: 'help', command: '/help', description: '查看可用的 AI 命令', usage: 'Enter', icon: Help },
  { name: 'new', command: '/new', description: '开始一个新对话', usage: 'Enter', icon: Plus },
  { name: 'clear', command: '/clear', description: '清空当前对话消息', usage: 'Enter', icon: Delete },
  { name: 'retry', command: '/retry', description: '重新发送上一个问题', usage: 'Enter', icon: Refresh },
  { name: 'topk', command: '/topk <数量>', description: '设置检索数量（1–20）', usage: '1–20', icon: Cpu }
]
const visibleSlashCommands = computed(() => {
  const match = draft.value.match(/^\/([^\s]*)/)
  if (!match) return []
  const query = match[1].toLocaleLowerCase()
  return slashCommands.filter(command =>
    command.name.startsWith(query) || command.description.toLocaleLowerCase().includes(query)
  )
})
const showSlashCommands = computed(() =>
  !sending.value && !slashCommandsDismissed.value && visibleSlashCommands.value.length > 0
)
const messageListRef = ref<HTMLElement>()
const conversationScrollPositions = new Map<number, number>()
const conversationFollowingBottom = new Map<number, boolean>()
const pendingScrollRestoreId = ref<number | null>(null)
const creatingConversation = ref(false)
const loadingConversations = ref(true)
const loadingMessages = ref(false)
const historyError = ref('')
const markdown = new MarkdownIt({
  breaks: true,
  html: false,
  linkify: true,
  typographer: false
})
markdown.renderer.rules.link_open = (tokens, index, options, environment, renderer) => {
  tokens[index].attrSet('target', '_blank')
  tokens[index].attrSet('rel', 'noopener noreferrer')
  return renderer.renderToken(tokens, index, options)
}

const loadConversations = async () => {
  loadingConversations.value = true
  historyError.value = ''
  let selectedConversation: Conversation | undefined
  try {
    const response: any = await chatConversationList()
    if (!response) return
    conversations.value = (response.data || []).map((item: any) => ({
      id: item.id,
      title: item.title,
      messages: [],
      messagesLoaded: false
    }))
    selectedConversation = conversations.value.find(item => item.id === activeConversationId.value)
      || conversations.value[0]
    activeConversationId.value = selectedConversation?.id ?? null
    if (selectedConversation) await loadMessages(selectedConversation)
  } catch (error) {
    historyError.value = error instanceof Error ? error.message : '加载聊天记录失败'
  } finally {
    loadingConversations.value = false
  }
  if (selectedConversation?.messagesLoaded) await restoreConversationScroll(selectedConversation)
}

const loadContextWindow = async () => {
  contextWindowError.value = false
  try {
    const response: any = await chatContextWindow()
    if (!response?.data?.contextLength) throw new Error('上下文窗口配置无效')
    contextWindow.value = response.data.contextLength
  } catch {
    contextWindowError.value = true
    ElMessage.error('读取上下文窗口失败')
  }
}

const loadMessages = async (conversation: Conversation) => {
  if (conversation.messagesLoaded) {
    if (!loadingConversations.value) await restoreConversationScroll(conversation)
    void resumePendingMessages(conversation)
    return
  }
  loadingMessages.value = true
  historyError.value = ''
  let loaded = false
  try {
    const response: any = await chatMessages(conversation.id)
    if (!response) return
    conversation.messages = (response.data || []).map((item: any) => ({
      id: item.id,
      role: item.role,
      text: item.content,
      status: item.status,
      pending: item.status === 'STREAMING',
      error: item.status === 'FAILED',
      sources: item.sources || []
    }))
    conversation.messagesLoaded = true
    loaded = true
  } catch (error) {
    historyError.value = error instanceof Error ? error.message : '加载聊天记录失败'
  } finally {
    loadingMessages.value = false
  }
  if (loaded) {
    if (!loadingConversations.value) await restoreConversationScroll(conversation)
    void resumePendingMessages(conversation)
  }
}

const selectConversation = async (id: number) => {
  saveScrollPosition()
  pendingScrollRestoreId.value = id
  activeConversationId.value = id
  const conversation = conversations.value.find(item => item.id === id)
  if (conversation) {
    await loadMessages(conversation)
    await restoreConversationScroll(conversation)
  }
}

const createConversation = async () => {
  if (creatingConversation.value) return
  creatingConversation.value = true
  historyError.value = ''
  try {
    const response: any = await chatConversationCreate()
    if (!response) return
    const conversation: Conversation = {
      id: response.data.id,
      title: response.data.title,
      messages: [],
      messagesLoaded: true
    }
    conversations.value.unshift(conversation)
    pendingScrollRestoreId.value = conversation.id
    activeConversationId.value = conversation.id
    draft.value = ''
    await restoreConversationScroll(conversation)
  } catch (error) {
    historyError.value = error instanceof Error ? error.message : '创建对话失败'
  } finally {
    creatingConversation.value = false
  }
}

const deleteConversation = async (id: number) => {
  const index = conversations.value.findIndex(conversation => conversation.id === id)
  if (index < 0) return
  historyError.value = ''
  try {
    const response: any = await chatConversationDelete(id)
    if (!response) return
  } catch (error) {
    historyError.value = error instanceof Error ? error.message : '删除对话失败'
    return
  }
  const wasActive = activeConversationId.value === id
  conversations.value.splice(index, 1)
  conversationScrollPositions.delete(id)
  conversationFollowingBottom.delete(id)
  if (wasActive) {
    const next = conversations.value[0]
    if (next) await selectConversation(next.id)
    else activeConversationId.value = null
  }
}

const safeSourceUrl = (value: string) => {
  try {
    const url = new URL(value)
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.href : ''
  } catch {
    return ''
  }
}

const displayMessageText = (message: ChatMessage) =>
  message.role === 'assistant' ? message.text.trimStart() : message.text

const renderMarkdown = (text: string) => DOMPurify.sanitize(markdown.render(text))
const loadingMessageText = '正在检索知识库并整理回答'
const resetSlashCommandSelection = () => {
  activeSlashCommandIndex.value = 0
  slashCommandsDismissed.value = false
}

const handleComposerKeydown = (event: KeyboardEvent) => {
  if (showSlashCommands.value && visibleSlashCommands.value.length) {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault()
      const direction = event.key === 'ArrowDown' ? 1 : -1
      const count = visibleSlashCommands.value.length
      activeSlashCommandIndex.value =
        (activeSlashCommandIndex.value + direction + count) % count
      return
    }
    if (event.key === 'Escape') {
      event.preventDefault()
      slashCommandsDismissed.value = true
      return
    }
  }
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    void handleComposerEnter()
  }
}

const handleComposerEnter = async () => {
  const input = draft.value.trim()
  const match = input.match(/^\/([a-z]+)(?:\s+(.*))?$/i)
  if (match) {
    const command = slashCommands.find(item => item.name === match[1].toLowerCase())
    if (command) {
      await runSlashCommand(command.name, match[2] || '')
      return
    }
  }
  if (showSlashCommands.value) {
    const selected = visibleSlashCommands.value[activeSlashCommandIndex.value]
    if (selected) {
      await runSlashCommand(selected.name)
      return
    }
  }
  await sendMessage()
}

const runSlashCommand = async (name: string, argument = '') => {
  switch (name) {
    case 'help':
      draft.value = ''
      slashCommandsDismissed.value = true
      ElMessage.info('可用命令：/new 新对话、/clear 清空当前对话、/retry 重试上一个问题、/topk 1–20 设置检索数量。')
      return
    case 'new':
      draft.value = ''
      slashCommandsDismissed.value = true
      await createConversation()
      return
    case 'clear': {
      const conversation = activeConversation.value
      if (!conversation) {
        draft.value = ''
        slashCommandsDismissed.value = true
        ElMessage.info('当前没有可清空的对话')
        return
      }
      if (!await confirm('清空当前对话的所有消息？此操作无法撤销。', {
        title: '清空对话',
        confirmText: '清空',
        danger: true
      })) return
      try {
        const response = await clearChatMessages(conversation.id)
        if (!response) throw new Error('清空对话请求未成功')
        conversation.messages = []
        conversation.title = '新对话'
        conversation.messagesLoaded = true
        draft.value = ''
        slashCommandsDismissed.value = true
        await scrollToBottom()
      } catch {
        ElMessage.error('清空对话失败，请稍后重试')
      }
      return
    }
    case 'retry': {
      const lastQuestion = [...(activeConversation.value?.messages || [])]
        .reverse()
        .find(message => message.role === 'user')?.text
      if (!lastQuestion) {
        draft.value = ''
        slashCommandsDismissed.value = true
        ElMessage.warning('当前对话没有可重试的问题')
        return
      }
      draft.value = lastQuestion
      slashCommandsDismissed.value = true
      await sendMessage()
      return
    }
    case 'topk': {
      const value = Number(argument.trim())
      if (!Number.isInteger(value) || value < 1 || value > 20) {
        ElMessage.warning('用法：/topk 1–20')
        return
      }
      topK.value = value
      draft.value = ''
      slashCommandsDismissed.value = true
      ElMessage.success(`检索数量已设置为 ${value}`)
      return
    }
    default:
      return
  }
}

const saveScrollPosition = () => {
  const conversationId = activeConversationId.value
  const messageList = messageListRef.value
  if (conversationId === null || !messageList || pendingScrollRestoreId.value === conversationId) return
  conversationScrollPositions.set(conversationId, messageList.scrollTop)
  conversationFollowingBottom.set(
    conversationId,
    messageList.scrollHeight - messageList.clientHeight - messageList.scrollTop <= 40
  )
}

const restoreConversationScroll = async (conversation: Conversation) => {
  if (activeConversationId.value !== conversation.id) return
  await nextTick()
  const messageList = messageListRef.value
  if (!messageList || activeConversationId.value !== conversation.id) return
  const savedPosition = conversationScrollPositions.get(conversation.id)
  messageList.scrollTop = savedPosition ?? messageList.scrollHeight
  await nextTick()
  if (activeConversationId.value !== conversation.id) return
  conversationScrollPositions.set(conversation.id, messageList.scrollTop)
  conversationFollowingBottom.set(
    conversation.id,
    messageList.scrollHeight - messageList.clientHeight - messageList.scrollTop <= 40
  )
  if (pendingScrollRestoreId.value === conversation.id) pendingScrollRestoreId.value = null
}

const scrollToBottom = async (
  conversationId = activeConversationId.value,
  force = true
) => {
  await nextTick()
  const messageList = messageListRef.value
  if (
    !messageList
    || conversationId === null
    || activeConversationId.value !== conversationId
    || (!force && conversationFollowingBottom.get(conversationId) === false)
  ) return
  messageList.scrollTop = messageList.scrollHeight
  conversationScrollPositions.set(conversationId, messageList.scrollTop)
  conversationFollowingBottom.set(conversationId, true)
}

const consumeChatStream = async (response: Response, message: ChatMessage, conversationId: number) => {
  if (!response.ok || !response.body) {
    const body = await response.text()
    let errorMessage = body || `请求失败（${response.status}）`
    try {
      const errorBody = JSON.parse(body)
      errorMessage = errorBody.msg || errorBody.message || errorMessage
    } catch {
      // Keep the response text when the server does not return JSON.
    }
    throw new Error(errorMessage)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let completed = false
  let connectedMessageId: number | undefined
  const handleEvent = (block: string) => {
    let event = ''
    let data = ''
    for (const line of block.split(/\r?\n/)) {
      if (line.startsWith('event:')) event = line.slice(6).trim()
      else if (line.startsWith('data:')) data += line.slice(5).trim()
    }
    if (!event || !data) return
    const payload = JSON.parse(data)
    if (event === 'start') {
      message.id = payload.id
      message.status = payload.status
      message.pending = payload.status === 'STREAMING'
      connectedMessageId = payload.id
      connectedStreamIds.add(payload.id)
    } else if (event === 'token') {
      message.text += payload
    } else if (event === 'complete') {
      message.id = payload.id
      message.text = payload.content ?? message.text
      message.status = payload.status
      message.pending = false
      message.error = payload.status === 'FAILED'
      message.sources = payload.sources || []
      completed = true
    } else if (event === 'error') {
      message.status = 'FAILED'
      message.pending = false
      message.error = true
      if (!message.text) message.text = payload || '回答生成失败，请稍后重试。'
      completed = true
    }
  }
  try {
    while (true) {
      const { value, done } = await reader.read()
      buffer += decoder.decode(value, { stream: !done })
      const blocks = buffer.split(/\r?\n\r?\n/)
      buffer = blocks.pop() || ''
      blocks.forEach(handleEvent)
      if (done) break
      await scrollToBottom(conversationId, false)
    }
    if (buffer.trim()) handleEvent(buffer)
    if (!completed) throw new Error('流式连接中断')
  } finally {
    if (connectedMessageId) connectedStreamIds.delete(connectedMessageId)
  }
}

const resumeMessageStream = async (conversation: Conversation, message: ChatMessage) => {
  if (!message.id || message.status !== 'STREAMING') return
  if (connectedStreamIds.has(message.id)) return
  const existing = streamConnections.get(message.id)
  if (existing) return existing

  const task = (async () => {
    let retries = 0
    while (message.status === 'STREAMING' && retries < 8) {
      try {
        const response = await resumeChatMessageStream(
          conversation.id,
          message.id!,
          message.text.length
        )
        await consumeChatStream(response, message, conversation.id)
        if (message.status !== 'STREAMING') return
      } catch {
        retries++
        if (retries >= 8) {
          ElMessage.warning('流式连接暂时中断，请重新打开会话继续接收。')
          return
        }
        await new Promise(resolve => window.setTimeout(resolve, Math.min(500 * 2 ** retries, 5000)))
      }
    }
  })()
  streamConnections.set(message.id, task)
  try {
    await task
  } finally {
    streamConnections.delete(message.id)
  }
}

const resumePendingMessages = async (conversation: Conversation) => {
  await Promise.all(conversation.messages
    .filter(message => message.role === 'assistant' && message.status === 'STREAMING')
    .map(message => resumeMessageStream(conversation, message)))
}

const sendMessage = async () => {
  const question = draft.value.trim()
  if (!question || sending.value) return

  sending.value = true
  if (!activeConversation.value) await createConversation()
  const conversation = activeConversation.value
  if (!conversation) {
    sending.value = false
    return
  }

  if (conversation.title === '新对话') {
    conversation.title = question.length > 80 ? question.slice(0, 80) : question
  }
  conversation.messages.push({ role: 'user', text: question, status: 'SUCCESS' })
  const responseMessage = reactive<ChatMessage>({
    role: 'assistant',
    text: '',
    status: 'STREAMING',
    pending: true
  })
  conversation.messages.push(responseMessage)
  conversation.messagesLoaded = true
  draft.value = ''
  await scrollToBottom()

  try {
    const response = await streamChatMessage(conversation.id, { question, topK: topK.value })
    await consumeChatStream(response, responseMessage, conversation.id)
  } catch (error) {
    if (responseMessage.id && responseMessage.status === 'STREAMING') {
      void resumeMessageStream(conversation, responseMessage)
    } else if (!responseMessage.text) {
      responseMessage.text = error instanceof Error ? `请求失败：${error.message}` : '请求失败，请稍后重试。'
      responseMessage.pending = false
      responseMessage.status = 'FAILED'
      responseMessage.error = true
    }
  } finally {
    sending.value = false
    await scrollToBottom(conversation.id)
  }
}

onMounted(() => {
  void loadConversations()
  void loadContextWindow()
})
onActivated(() => {
  const conversation = activeConversation.value
  if (conversation) void restoreConversationScroll(conversation)
})
</script>

<style scoped>
.ai-chat-page {
  display: grid;
  grid-template-columns: 252px minmax(0, 1fr);
  height: calc(100vh - 128px);
  min-height: 520px;
  overflow: hidden;
  border: 1px solid var(--chrome-line);
  border-radius: 16px;
  background: var(--surface);
  box-shadow: var(--shadow-soft);
  color: var(--ink-900);
}

.chat-sidebar {
  display: flex;
  min-height: 0;
  flex-direction: column;
  padding: 22px 14px 14px;
  border-right: 1px solid var(--chrome-line);
  background: var(--surface-soft);
}

.sidebar-heading { display: flex; align-items: center; gap: 11px; padding: 0 7px 22px; }
.brand-mark, .welcome-icon {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  color: #fff;
  background: linear-gradient(145deg, #18b9aa, #087e83);
  box-shadow: 0 7px 18px rgba(var(--teal-shadow-rgb), .2);
}
.brand-mark { width: 38px; height: 38px; border-radius: 12px; font-size: 19px; }
.sidebar-heading strong, .sidebar-heading span { display: block; }
.sidebar-heading strong { color: var(--ink-950); font-size: 15px; font-weight: 700; }
.sidebar-heading span { margin-top: 3px; color: var(--ink-500); font-size: 11px; }
.new-chat-button { width: 100%; height: 40px; justify-content: flex-start; gap: 8px; border-radius: 9px; }
.history-heading { display: flex; align-items: center; gap: 8px; margin: 26px 8px 10px; color: var(--ink-500); font-size: 11px; font-weight: 600; }
.history-heading span { display: grid; min-width: 18px; height: 18px; place-items: center; border-radius: 9px; background: var(--chrome-hover); color: var(--ink-700); font-size: 10px; }
.conversation-list { display: flex; min-height: 0; flex-direction: column; gap: 4px; overflow: auto; }
.conversation-item { display: flex; align-items: center; gap: 9px; width: 100%; min-width: 0; padding: 10px 9px; border: 0; border-radius: 8px; background: transparent; color: var(--ink-700); text-align: left; cursor: pointer; transition: background .16s, color .16s; }
.conversation-item:hover, .conversation-item.is-active { background: var(--tint); color: var(--teal-ink); }
.conversation-item > .el-icon { flex: 0 0 auto; font-size: 15px; }
.conversation-title { flex: 1; overflow: hidden; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.delete-conversation { display: grid; flex: 0 0 auto; width: 24px; height: 24px; place-items: center; border-radius: 6px; color: var(--ink-500); opacity: 0; }
.conversation-item:hover .delete-conversation, .conversation-item.is-active .delete-conversation { opacity: 1; }
.delete-conversation:hover { background: var(--ctx-danger-bg); color: var(--ctx-danger-ink); }
.history-empty { padding: 12px 8px; color: var(--ink-500); font-size: 11px; }
.history-error { display: grid; gap: 5px; padding: 8px; color: var(--action-danger-ink); font-size: 10px; overflow-wrap: anywhere; }
.history-error button { justify-self: start; padding: 0; border: 0; background: none; color: var(--teal-ink); font-size: 10px; cursor: pointer; }
.sidebar-footer { display: flex; align-items: center; gap: 7px; margin-top: auto; padding: 13px 7px 2px; border-top: 1px solid var(--chrome-line); color: var(--ink-500); font-size: 10px; }
.sidebar-footer .el-icon { color: var(--teal); font-size: 14px; }

.chat-main { display: flex; min-width: 0; min-height: 0; flex-direction: column; }
.chat-header { display: flex; min-height: 72px; align-items: center; justify-content: space-between; gap: 14px; padding: 15px 26px; border-bottom: 1px solid var(--chrome-line); }
.header-actions { display: flex; flex: 0 0 auto; align-items: center; gap: 10px; }
.ai-settings-button { flex: 0 0 auto; border-radius: 20px; font-size: 11px; }
.ai-settings-button .el-icon { margin-right: 4px; }
:global(.ai-settings-drawer .el-drawer__body) { overflow: auto; padding: 18px; }
.mobile-new-chat, .mobile-conversations { display: none; }
.chat-header h1 { margin: 0; overflow: hidden; color: var(--ink-950); font-size: 15px; font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }
.chat-header p { margin: 5px 0 0; color: var(--ink-500); font-size: 11px; }
.model-badge { display: flex; flex: 0 0 auto; align-items: center; gap: 7px; padding: 7px 10px; border: 1px solid var(--chrome-line); border-radius: 20px; color: var(--ink-700); font-size: 10px; }
.model-badge span { width: 7px; height: 7px; border-radius: 50%; background: #35b98c; box-shadow: 0 0 0 3px rgba(53, 185, 140, .12); }
.message-list { flex: 1; min-height: 0; overflow: auto; padding: 20px clamp(18px, 7vw, 88px); }
.history-loading { display: flex; align-items: center; gap: 6px; padding: 8px; color: var(--ink-500); font-size: 10px; }
.loading-history { display: flex; min-height: 100%; flex-direction: column; align-items: center; justify-content: center; gap: 9px; color: var(--ink-500); font-size: 12px; }
.history-loading-indicator { display: grid; width: 48px; height: 48px; margin-bottom: 4px; place-items: center; border: 1px solid var(--chrome-line); border-radius: 15px; background: var(--surface); color: var(--teal); font-size: 21px; box-shadow: var(--shadow-soft); }
.loading-history strong { color: var(--ink-800); font-size: 13px; font-weight: 650; }
.loading-history .loading-dots { margin: -4px 0 0; color: var(--teal); }
.welcome-state { display: flex; width: min(100%, 620px); min-height: 100%; flex-direction: column; align-items: center; justify-content: center; margin: 0 auto; padding: 16px 0 26px; text-align: center; }
.welcome-icon { width: 52px; height: 52px; margin-bottom: 17px; border-radius: 17px; font-size: 25px; }
.welcome-eyebrow { margin: 0 0 9px; color: var(--teal-ink-light); font-size: 9px; font-weight: 700; letter-spacing: 2px; }
.welcome-state h2 { margin: 0; color: var(--ink-950); font-size: clamp(20px, 3vw, 26px); font-weight: 650; letter-spacing: -.5px; }
.welcome-description { max-width: 410px; margin: 11px 0 26px; color: var(--ink-500); font-size: 12px; line-height: 1.7; }
.suggestion-list { display: grid; width: 100%; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 9px; }
.suggestion-card { display: flex; min-height: 54px; align-items: center; justify-content: space-between; gap: 10px; padding: 11px 13px; border: 1px solid var(--chrome-line); border-radius: 9px; background: var(--surface); color: var(--ink-700); text-align: left; cursor: pointer; transition: border-color .16s, background .16s, transform .16s; }
.suggestion-card:hover { transform: translateY(-1px); border-color: var(--teal); background: var(--tint); color: var(--teal-ink); }
.suggestion-card span { font-size: 11px; line-height: 1.5; }
.suggestion-card .el-icon { flex: 0 0 auto; color: var(--teal); font-size: 14px; }
.message-thread { display: flex; max-width: 760px; flex-direction: column; gap: 28px; margin: 0 auto; }
.message-row { display: flex; align-items: flex-start; gap: 12px; }
.message-user { justify-content: flex-end; }
.message-avatar { display: grid; flex: 0 0 auto; width: 30px; height: 30px; place-items: center; border: 1px solid var(--chrome-line); border-radius: 10px; background: var(--surface-soft); color: var(--ink-700); font-size: 15px; }
.message-assistant .message-avatar { border: 0; background: linear-gradient(145deg, #18b9aa, #087e83); color: #fff; }
.message-content { min-width: 0; flex: 1; padding-top: 2px; }
.message-user .message-avatar { order: 2; }
.message-user .message-content { flex: 0 1 auto; max-width: min(78%, 560px); }
.message-author { margin-bottom: 7px; color: var(--ink-700); font-size: 11px; font-weight: 650; }
.message-text { color: var(--ink-900); font-size: 13px; line-height: 1.8; white-space: pre-wrap; overflow-wrap: anywhere; }
.message-markdown { white-space: normal; }
.message-markdown :deep(:first-child) { margin-top: 0; }
.message-markdown :deep(:last-child) { margin-bottom: 0; }
.message-markdown :deep(p) { margin: 0 0 12px; }
.message-markdown :deep(h1), .message-markdown :deep(h2), .message-markdown :deep(h3),
.message-markdown :deep(h4), .message-markdown :deep(h5), .message-markdown :deep(h6) {
  margin: 16px 0 8px;
  color: var(--ink-950);
  line-height: 1.45;
}
.message-markdown :deep(ul), .message-markdown :deep(ol) { margin: 8px 0 12px; padding-left: 24px; }
.message-markdown :deep(blockquote) { margin: 10px 0; padding: 2px 12px; border-left: 3px solid var(--teal); color: var(--ink-600); }
.message-markdown :deep(pre) { overflow-x: auto; margin: 10px 0; padding: 12px; border-radius: 8px; background: var(--surface-soft); }
.message-markdown :deep(pre code) { padding: 0; background: transparent; }
.message-markdown :deep(code) { padding: 2px 5px; border-radius: 4px; background: var(--surface-soft); font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: .92em; }
.message-markdown :deep(a) { color: var(--teal-ink); text-decoration: underline; }
.message-markdown :deep(table) { display: block; max-width: 100%; overflow-x: auto; border-collapse: collapse; }
.message-markdown :deep(th), .message-markdown :deep(td) { padding: 6px 10px; border: 1px solid var(--chrome-line); text-align: left; }
.message-user .message-content { padding: 10px 14px; border-radius: 12px 4px 12px 12px; background: var(--surface-soft); }
.message-user .message-author { margin-bottom: 3px; text-align: right; }
.message-text.is-pending { color: var(--ink-500); }
.loading-dots { display: inline-flex; align-items: center; gap: 3px; margin-left: 4px; vertical-align: baseline; }
.loading-dots i {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: currentColor;
  opacity: .35;
  animation: loading-dot-bounce 1s ease-in-out infinite;
}
.loading-dots i:nth-child(2) { animation-delay: .16s; }
.loading-dots i:nth-child(3) { animation-delay: .32s; }
@keyframes loading-dot-bounce {
  0%, 60%, 100% { transform: translateY(0); opacity: .35; }
  30% { transform: translateY(-4px); opacity: 1; }
}
@media (prefers-reduced-motion: reduce) {
  .loading-dots i { animation: none; opacity: 1; }
}
.message-text.is-error { color: var(--action-danger-ink); }
.message-status-failed { display: inline-block; margin-top: 6px; color: var(--action-danger-ink); font-size: 10px; }
.thinking-icon { margin-right: 5px; vertical-align: -2px; animation: spin 1.2s linear infinite; }
.source-list { display: grid; gap: 7px; max-width: 570px; margin-top: 15px; }
.source-heading { display: flex; align-items: center; gap: 6px; margin-bottom: 2px; color: var(--ink-700); font-size: 10px; font-weight: 650; }
.source-heading .el-icon { color: var(--teal); }
.source-heading span { color: var(--ink-500); font-weight: 400; }
.source-card { display: flex; min-width: 0; align-items: center; gap: 11px; padding: 10px 12px; border: 1px solid var(--chrome-line); border-radius: 8px; background: var(--surface); color: inherit; text-decoration: none; transition: border-color .16s, background .16s; }
.source-card[href]:hover { border-color: var(--teal); background: var(--surface-soft); }
.source-card.is-disabled { cursor: default; }
.source-index { color: var(--teal); font-size: 10px; font-weight: 700; }
.source-copy { display: grid; min-width: 0; flex: 1; gap: 4px; }
.source-copy strong, .source-copy small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.source-copy strong { color: var(--ink-900); font-size: 11px; font-weight: 600; }
.source-copy small { color: var(--ink-500); font-size: 9px; }
.source-card > .el-icon { color: var(--ink-500); font-size: 13px; }

.composer-wrap { width: min(100% - 48px, 760px); margin: 0 auto; padding: 8px 0 13px; }
.composer { position: relative; padding: 11px 13px 9px; border: 1px solid var(--line-strong); border-radius: 13px; background: var(--surface); box-shadow: 0 5px 18px rgba(var(--teal-shadow-soft-rgb), .06); transition: border-color .18s, box-shadow .18s; }
.composer:focus-within { border-color: var(--teal); box-shadow: 0 0 0 3px var(--focus-ring), 0 5px 18px rgba(var(--teal-shadow-soft-rgb), .06); }
.slash-command-menu {
  position: absolute;
  z-index: 5;
  right: 0;
  bottom: calc(100% + 10px);
  left: 0;
  display: grid;
  max-height: min(320px, 50vh);
  gap: 3px;
  overflow: auto;
  padding: 7px;
  border: 1px solid var(--chrome-line);
  border-radius: 12px;
  background: var(--surface);
  box-shadow: var(--shadow-soft);
}
.slash-command-option {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 10px;
  padding: 9px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--ink-800);
  text-align: left;
  cursor: pointer;
}
.slash-command-option:hover, .slash-command-option.is-active { background: var(--chrome-hover); }
.slash-command-icon { display: grid; width: 30px; height: 30px; flex: 0 0 auto; place-items: center; border-radius: 8px; background: var(--surface-soft); color: var(--teal-ink); }
.slash-command-copy { display: grid; min-width: 0; flex: 1; gap: 3px; }
.slash-command-copy strong { color: var(--ink-900); font-size: 12px; font-weight: 650; }
.slash-command-copy small { overflow: hidden; color: var(--ink-500); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.slash-command-option kbd { flex: 0 0 auto; color: var(--ink-500); font-family: inherit; font-size: 10px; }
.composer :deep(.el-textarea__inner) { min-height: 35px !important; padding: 4px 5px; border: 0; border-radius: 0; background: transparent; box-shadow: none; color: var(--ink-900); font-size: 12px; line-height: 1.6; }
.composer :deep(.el-textarea__inner:focus) { box-shadow: none; }
.composer-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 10px; padding-top: 7px; }
.composer-options { display: flex; min-width: 0; flex: 1; align-items: center; gap: 18px; }
.retrieval-control, .send-tools { display: flex; align-items: center; gap: 8px; }
.retrieval-control { color: var(--ink-500); font-size: 10px; }
.retrieval-control :deep(.el-select) { width: 78px; }
.retrieval-control :deep(.el-input__wrapper) { padding: 0 7px; box-shadow: none; background: var(--surface-soft); }
.retrieval-control :deep(.el-input__inner) { height: 25px; font-size: 10px; }
.context-usage { display: flex; min-width: 150px; flex: 1; flex-direction: column; gap: 5px; }
.context-usage-copy { display: flex; justify-content: space-between; gap: 8px; color: var(--ink-500); font-size: 10px; }
.context-usage-copy strong { color: var(--ink-700); font-size: 10px; font-weight: 550; white-space: nowrap; }
.context-usage :deep(.el-progress-bar__outer) { background: var(--chrome-hover); }
.context-usage-warning { color: var(--action-danger-ink); font-size: 9px; }
.context-usage-error { color: var(--action-danger-ink); font-size: 10px; }
.keyboard-hint { color: var(--ink-500); font-size: 9px; }
.send-button { height: 32px; padding: 0 12px; border-radius: 8px; font-size: 11px; }
.send-button .el-icon { margin-right: 5px; }
.disclaimer { margin: 7px 0 0; color: var(--ink-500); font-size: 9px; text-align: center; }
@keyframes spin { to { transform: rotate(360deg); } }

@media (max-width: 800px) {
  .ai-chat-page { grid-template-columns: 205px minmax(0, 1fr); height: calc(100vh - 105px); min-height: 460px; }
  .chat-sidebar { padding: 18px 10px 12px; }
  .message-list { padding-right: 22px; padding-left: 22px; }
  .composer-wrap { width: calc(100% - 36px); }
}
@media (max-width: 600px) {
  .ai-chat-page { grid-template-columns: minmax(0, 1fr); height: calc(100dvh - 84px); min-height: 420px; border-radius: 12px; }
  .chat-sidebar { display: none; }
  .chat-header { min-height: 62px; padding: 12px 15px; }
  .chat-header h1 { max-width: 52vw; font-size: 13px; }
  .chat-header p { font-size: 10px; }
  .model-badge { padding: 6px 8px; font-size: 9px; }
  .ai-settings-button { width: 29px; height: 29px; padding: 0 6px; }
  .ai-settings-button span { display: none; }
  .mobile-new-chat { display: inline-flex; height: 29px; padding: 0 8px; border-radius: 7px; font-size: 10px; }
  .mobile-new-chat .el-icon { margin-right: 3px; }
  .mobile-conversations { display: flex; gap: 6px; overflow-x: auto; padding: 8px 12px; border-bottom: 1px solid var(--chrome-line); scrollbar-width: none; }
  .mobile-conversations::-webkit-scrollbar { display: none; }
  .mobile-conversation { max-width: 150px; flex: 0 0 auto; overflow: hidden; padding: 6px 10px; border: 1px solid var(--chrome-line); border-radius: 16px; background: var(--surface); color: var(--ink-700); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; cursor: pointer; }
  .mobile-conversation.is-active { border-color: var(--teal); background: var(--tint); color: var(--teal-ink); }
  .composer-options { flex-wrap: wrap; gap: 9px 14px; }
  .context-usage { min-width: 135px; }
  .message-list { padding: 16px 14px; }
  .suggestion-list { grid-template-columns: 1fr; }
  .suggestion-card { min-height: 44px; }
  .message-thread { gap: 22px; }
  .composer-wrap { width: calc(100% - 24px); padding-bottom: 9px; }
  .keyboard-hint { display: none; }
}
</style>
