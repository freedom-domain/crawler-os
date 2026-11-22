<template>
  <div
    ref="chatPageRef"
    class="ai-chat-page"
    :class="{
      'sidebar-collapsed': sidebarCollapsed,
      'ai-chat-page--standalone': isStandaloneWindow,
      'is-resizing-sidebar': isResizingSidebar
    }"
    :style="{ '--chat-sidebar-width': `${sidebarWidth}px` }"
  >
    <aside ref="chatSidebarRef" class="chat-sidebar">
      <div class="sidebar-heading">
        <div class="brand-mark"><el-icon><MagicStick /></el-icon></div>
        <div>
          <strong>智能体</strong>
          <span>知识库问答</span>
        </div>
      </div>

      <el-button
        class="new-chat-button"
        type="primary"
        :loading="creatingConversation"
        title="开始新对话"
        aria-label="开始新对话"
        @click="createConversation"
      >
        <el-icon><Plus /></el-icon>
        <span class="new-chat-label">开始新对话</span>
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
          <el-tooltip
            :content="conversation.title"
            placement="right"
            effect="light"
            popper-class="conversation-title-tooltip"
            :show-after="350"
          >
            <span class="conversation-title">{{ conversation.title }}</span>
          </el-tooltip>
          <span
            class="rename-conversation"
            role="button"
            tabindex="0"
            title="重命名对话"
            aria-label="重命名对话"
            @click.stop="renameConversation(conversation)"
            @keydown.enter.stop="renameConversation(conversation)"
            @keydown.space.prevent.stop="renameConversation(conversation)"
          ><el-icon><EditPen /></el-icon></span>
          <span
            class="delete-conversation"
            role="button"
            tabindex="0"
            title="删除对话"
            aria-label="删除对话"
            @click.stop="deleteConversation(conversation.id)"
            @keydown.enter.stop="deleteConversation(conversation.id)"
            @keydown.space.prevent.stop="deleteConversation(conversation.id)"
          ><el-icon><Delete /></el-icon></span>
        </button>
      </div>
      <div v-else-if="loadingConversations" class="history-loading" role="status">
        <el-icon class="thinking-icon"><Loading /></el-icon>
        <span>正在加载历史对话</span>
        <span class="loading-dots" aria-hidden="true"><i /><i /><i /></span>
      </div>
      <div v-else class="history-empty">对话会显示在这里</div>

    </aside>

    <div
      v-if="!sidebarCollapsed"
      class="sidebar-resize-handle"
      role="separator"
      aria-label="调整聊天历史栏宽度"
      aria-orientation="vertical"
      :aria-valuemin="160"
      :aria-valuemax="sidebarMaxWidth"
      :aria-valuenow="Math.round(sidebarWidth)"
      tabindex="0"
      @pointerdown="startSidebarResize"
      @keydown.left.prevent="resizeSidebarByKeyboard(-1)"
      @keydown.right.prevent="resizeSidebarByKeyboard(1)"
    />

    <section class="chat-main">
      <header class="chat-header">
        <div class="chat-heading">
          <button
            type="button"
            class="sidebar-toggle"
            :aria-expanded="!sidebarCollapsed"
            :aria-label="sidebarCollapsed ? '展开左侧栏' : '收起左侧栏'"
            :title="sidebarCollapsed ? '展开左侧栏' : '收起左侧栏'"
            @click="sidebarCollapsed = !sidebarCollapsed"
          >
            <el-icon>
              <ArrowRight v-if="sidebarCollapsed" />
              <ArrowLeft v-else />
            </el-icon>
          </button>
          <div class="chat-heading-copy">
            <div class="chat-title-row">
              <h1>{{ activeConversation?.title || '新对话' }}</h1>
              <el-button
                v-if="activeConversation"
                class="rename-active-conversation"
                link
                aria-label="重命名当前对话"
                title="重命名当前对话"
                @click="renameConversation(activeConversation)"
              >
                <el-icon><EditPen /></el-icon>
              </el-button>
            </div>
          </div>
        </div>
        <div class="header-actions">
          <button
            v-if="isStandaloneWindow"
            type="button"
            class="standalone-theme-button"
            :title="themeModeTitle"
            :aria-label="themeModeTitle"
            @click="toggleTheme"
          >
            <el-icon :size="18"><component :is="themeIcon" /></el-icon>
          </button>
          <el-select
            v-model="selectedAssistantId"
            class="assistant-selector"
            size="small"
            placeholder="选择智能体"
            popper-class="assistant-select-popper"
            @change="switchAssistant"
            v-if="!effectiveAssistantId"
          >
            <el-option
              v-for="assistant in assistants"
              :key="assistant.id"
              :label="assistant.name"
              :value="String(assistant.id)"
              :disabled="!assistant.enabled"
            />
          </el-select>
          <el-tooltip v-if="!isStandaloneWindow" content="管理智能体" placement="bottom">
            <el-button
              class="assistant-manage-button"
              size="small"
              :icon="Setting"
              aria-label="管理智能体"
              @click="openAssistantManagement"
            />
          </el-tooltip>
          <el-tooltip v-if="!isStandaloneWindow" content="在独立窗口中打开当前对话" placement="bottom">
            <el-button
              class="standalone-chat-button"
              size="small"
              :icon="Link"
              aria-label="在独立窗口中打开当前对话"
              @click="openChatInWindow"
            />
          </el-tooltip>
          <el-button
            v-if="sidebarCollapsed"
            class="collapsed-new-chat"
            size="small"
            :loading="creatingConversation"
            title="开始新对话"
            aria-label="开始新对话"
            @click="createConversation"
          >
            <el-icon><Plus /></el-icon>
          </el-button>
          <el-button class="mobile-new-chat" size="small" :loading="creatingConversation" @click="createConversation">
            <el-icon><Plus /></el-icon>新对话
          </el-button>
        </div>
      </header>

      <div v-if="conversations.length > 0" class="mobile-conversations">
        <button
          v-for="conversation in conversations"
          :key="conversation.id"
          type="button"
          class="mobile-conversation"
          :class="{ 'is-active': conversation.id === activeConversationId }"
          @click="selectConversation(conversation.id)"
        >
          <el-tooltip
            :content="conversation.title"
            placement="top"
            effect="light"
            popper-class="conversation-title-tooltip"
            :show-after="350"
          >
            <span>{{ conversation.title }}</span>
          </el-tooltip>
        </button>
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
          <h2>{{ activeAssistant?.welcomeMessage || '你好，有什么可以帮你？' }}</h2>
          <p class="welcome-description">{{ activeAssistant?.description || '我会从已收录的网页内容中检索信息，并附上答案来源。' }}</p>
          <p v-if="suggestionsLoading || !chatModelsLoaded" class="suggestion-feedback" role="status">
            正在生成推荐问题…
          </p>
          <div v-else-if="suggestionError" class="suggestion-feedback suggestion-error" role="alert">
            <span>{{ suggestionError }}</span>
            <el-button link type="primary" @click="loadChatSuggestions">重试</el-button>
          </div>
          <div v-else-if="suggestions.length" class="suggestion-list">
            <button
              v-for="suggestion in suggestions"
              :key="suggestion"
              type="button"
              class="suggestion-card"
              :disabled="sending"
              @click="sendSuggestedQuestion(suggestion)"
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
              <details
                v-if="message.role === 'assistant' && (message.reasoningText || message.waitDurationMs != null || message.thinkingDurationMs != null)"
                class="reasoning-block"
                :open="message.pending"
              >
                <summary>
                  <span class="reasoning-summary-title">模型处理时间</span>
                  <span class="reasoning-summary-content">
                    <span v-if="message.waitDurationMs != null" class="reasoning-duration">
                      首响应等待 {{ formatDuration(message.waitDurationMs) }}
                    </span>
                    <span v-if="message.thinkingDurationMs != null" class="reasoning-duration">
                      实际思考 {{ formatDuration(message.thinkingDurationMs) }}
                    </span>
                    <span v-if="message.reasoningText" class="reasoning-duration">
                      {{ formatTokenCount(countCharacters(message.reasoningText)) }} 字
                    </span>
                    <span v-if="message.reasoningText" class="reasoning-duration">
                      约 {{ formatTokenCount(estimateTokens(message.reasoningText)) }} Token
                    </span>
                    <span
                      v-if="message.thinkingDurationMs && message.thinkingDurationMs > 0 && message.reasoningText"
                      class="reasoning-duration"
                    >
                      {{ formatTokenRate(message.reasoningText, message.thinkingDurationMs) }}
                    </span>
                  </span>
                </summary>
                <div class="reasoning-content message-markdown" v-html="renderMarkdown(message.reasoningText || '')" />
              </details>
              <div
                v-if="message.role === 'assistant' && message.pending && !message.text"
                class="message-text is-pending-indicator"
              >
                {{ loadingMessageText }}
                <span class="loading-dots" aria-hidden="true">
                  <i v-for="dot in 3" :key="dot" />
                </span>
              </div>
              <div
                v-else-if="message.role === 'assistant'"
                class="message-text message-markdown"
                :class="{ 'is-error': message.error }"
              >
                <div
                  v-html="renderAnswerMarkdown(message)"
                  @click="handleAnswerCitationClick($event, message)"
                />
                <span v-if="message.pending" class="loading-dots" aria-hidden="true">
                  <i v-for="dot in 3" :key="dot" />
                </span>
              </div>
              <div v-else class="message-text">
                <div class="user-message-bubble">
                  <el-icon v-if="message.pending" class="thinking-icon"><Loading /></el-icon>
                  {{ message.text }}
                </div>
              </div>
              <el-button
                v-if="message.role === 'user' && message.text"
                class="copy-user-message-button"
                link
                aria-label="复制消息"
                title="复制消息"
                @click="copyMessage(message.text, '消息已复制')"
              >
                <el-icon><DocumentCopy /></el-icon>
              </el-button>
              <div v-if="message.role === 'assistant' && message.status === 'SUCCESS'" class="message-stats">
                <span v-if="message.generationDurationMs !== undefined">
                  回答生成 {{ formatDuration(message.generationDurationMs) }}
                </span>
                <span>{{ formatTokenCount(countCharacters(message.text)) }} 字</span>
                <span>约 {{ formatTokenCount(estimateTokens(message.text)) }} Token</span>
                <span v-if="message.durationMs !== undefined">
                  总耗时 {{ formatDuration(message.durationMs) }}
                </span>
                <span v-if="message.generationDurationMs && message.generationDurationMs > 0">
                  {{ formatTokenRate(message.text, message.generationDurationMs) }}
                </span>
                <el-button
                  class="copy-message-button"
                  link
                  aria-label="复制回答"
                  @click="copyMessage(message.text)"
                >
                  <el-icon><DocumentCopy /></el-icon>
                </el-button>
              </div>
              <div
                v-if="message.role === 'assistant' && message.status === 'STREAMING' && message.generationStartedAt !== undefined"
                class="message-stats"
              >
                <span>总耗时 {{ formatDuration(Math.max(0, elapsedClock - message.generationStartedAt)) }}</span>
              </div>
              <div v-if="message.status === 'FAILED'" class="failed-message-actions">
                <span class="message-status-failed">回答失败</span>
                <el-button
                  class="retry-message-button"
                  link
                  type="primary"
                  :disabled="sending"
                  @click="retryFailedMessage(message)"
                >
                  <el-icon><Refresh /></el-icon>
                  重发
                </el-button>
              </div>
              <span v-if="message.status === 'CANCELLED'" class="message-status-cancelled">已中断生成</span>
              <div v-if="message.sources?.length && !hasInlineSourceCitations(message)" class="source-list">
                <div class="source-heading"><el-icon><Link /></el-icon>参考来源 <span>{{ message.sources.length }}</span></div>
                <button
                  v-for="(source, sourceIndex) in message.sources"
                  :key="source.id || `${source.url}-${sourceIndex}`"
                  type="button"
                  class="source-card"
                  @click="openSourcePreview(source)"
                >
                  <span class="source-index">{{ String(sourceIndex + 1).padStart(2, '0') }}</span>
                  <span class="source-copy">
                    <strong>{{ source.title || source.url || '未命名来源' }}</strong>
                    <small>来源类型：{{ sourceTypeLabel(source) }}</small>
                  </span>
                  <el-icon><ArrowRight /></el-icon>
                </button>
              </div>
            </div>
          </article>
        </div>
      </div>

      <div class="composer-wrap">
        <p class="assistant-description">
          {{ activeAssistant?.description || '向已收录的内容提问，答案附带相关来源' }}
        </p>
        <el-button
          v-if="showScrollToBottom && activeConversation?.messages.length"
          class="scroll-to-bottom"
          circle
          aria-label="回到底部"
          title="回到底部"
          @click="scrollToBottom(activeConversationId, true, 'smooth')"
        >
          <el-icon><ArrowDown /></el-icon>
        </el-button>
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
            placeholder="输入问题，输入 / 查看 AI 命令… · Enter 发送 · Shift + Enter 换行"
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
            </div>
            <div
              v-if="contextWindow > 0"
              class="context-usage"
            >
              <el-popover trigger="click" placement="top" :width="260" popper-class="context-usage-popover">
                <template #reference>
                  <button
                    type="button"
                    class="context-usage-trigger"
                    aria-label="上下文用量，点击查看详情"
                    title="点击查看上下文用量"
                  >
                    <el-progress
                      type="circle"
                      :percentage="contextUsagePercentage"
                      :width="40"
                      :stroke-width="4"
                      :color="contextUsageColor"
                      :show-text="false"
                    />
                    <span class="context-usage-percent">{{ contextUsagePercentage }}%</span>
                  </button>
                </template>
                <div class="context-usage-details">
                  <strong>上下文用量</strong>
                  <span>约 {{ formatTokenCount(estimatedHistoryTokens) }} / {{ formatTokenCount(contextWindow) }} Token</span>
                  <small>按当前会话成功的历史消息估算，不含本次问题、系统提示和检索资料。</small>
                  <span v-if="estimatedHistoryTokens > contextWindow" class="context-usage-warning">
                    历史消息估算已超过当前模型上下文长度
                  </span>
                </div>
              </el-popover>
            </div>
            <div class="send-tools">
              <el-select
                v-model="selectedChatModelId"
                class="chat-model-selector"
                size="small"
                popper-class="chat-model-select-popper"
                aria-label="聊天模型"
              >
                <el-option
                  v-for="model in chatModels"
                  :key="model.id"
                  :label="model.defaultModel ? `${model.name}（默认）` : model.name"
                  :value="String(model.id)"
                />
              </el-select>
              <el-button
                v-if="activeStreamingMessage?.id && activeConversation"
                class="send-button stop-button"
                type="danger"
                link
                :loading="cancelingMessageIds.has(activeStreamingMessage.id)"
                :disabled="cancelingMessageIds.has(activeStreamingMessage.id)"
                aria-label="中断生成"
                title="中断生成"
                @click="stopMessageGeneration(activeConversation.id, activeStreamingMessage)"
              >
                <span v-if="!cancelingMessageIds.has(activeStreamingMessage.id)" class="stop-square-icon" aria-hidden="true" />
              </el-button>
              <el-button
                v-else
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

    <el-dialog
      v-model="sourcePreviewVisible"
      class="source-preview-dialog"
      :fullscreen="sourcePreviewFullscreen"
      width="min(1000px, 94vw)"
      append-to-body
      @closed="handleSourcePreviewClosed"
    >
      <template #header>
        <div class="source-dialog-header">
          <strong class="source-dialog-title">{{ selectedSource?.title || '参考来源分块预览' }}</strong>
          <div class="source-dialog-actions">
            <el-button
              link
              :aria-label="sourcePreviewFullscreen ? '还原预览窗口' : '最大化预览窗口'"
              :title="sourcePreviewFullscreen ? '还原' : '最大化'"
              @click="sourcePreviewFullscreen = !sourcePreviewFullscreen"
            >
              <el-icon><component :is="sourcePreviewFullscreen ? Minus : FullScreen" /></el-icon>
            </el-button>
          </div>
        </div>
      </template>
      <div
        v-if="selectedSource"
        v-loading="sourcePreviewLoading"
        class="source-preview"
        :class="{ 'source-preview--fullscreen': sourcePreviewFullscreen }"
      >
        <div class="source-preview-meta">
          <el-tag size="small" effect="light">{{ sourceTypeLabel(selectedSource) }}</el-tag>
          <span v-if="selectedSourceChunk">{{ selectedSourceChunk.characterCount }} 字符</span>
          <el-button
            v-if="selectedSourceChunk"
            type="primary"
            link
            :loading="sourceOriginalLoading"
            @click="openSourceOriginalPreview"
          >
            预览原文
          </el-button>
        </div>
        <el-alert
          v-if="sourcePreviewError"
          :title="sourcePreviewError"
          type="error"
          :closable="false"
          show-icon
        />
        <p v-else-if="selectedSourceChunk">{{ selectedSourceChunk.content || '该分块内容为空。' }}</p>
      </div>
    </el-dialog>

    <el-dialog
      v-model="sourceOriginalPreviewVisible"
      class="source-preview-dialog"
      :fullscreen="sourceOriginalFullscreen"
      width="min(1000px, 94vw)"
      append-to-body
      @closed="handleSourceOriginalClosed"
    >
      <template #header>
        <div class="source-dialog-header">
          <strong class="source-dialog-title">{{ selectedSource?.title || '原文预览' }}</strong>
          <div class="source-dialog-actions">
            <el-button
              link
              :aria-label="sourceOriginalFullscreen ? '还原预览窗口' : '最大化预览窗口'"
              :title="sourceOriginalFullscreen ? '还原' : '最大化'"
              @click="sourceOriginalFullscreen = !sourceOriginalFullscreen"
            >
              <el-icon><component :is="sourceOriginalFullscreen ? Minus : FullScreen" /></el-icon>
            </el-button>
          </div>
        </div>
      </template>
      <div
        v-if="selectedSource"
        v-loading="sourceOriginalLoading"
        class="source-preview"
        :class="{ 'source-preview--fullscreen': sourceOriginalFullscreen }"
      >
        <div class="source-preview-meta">
          <el-tag size="small" effect="light">{{ sourceTypeLabel(selectedSource) }}</el-tag>
          <div class="source-preview-actions">
            <button
              v-if="sourceDocumentImages.length"
              type="button"
              class="source-image-open-button"
              @click="openSourceImagePreview"
            >
              <el-icon><Picture /></el-icon>
              <span>图片预览</span>
              <span class="source-image-count">{{ sourceDocumentImages.length }}</span>
              <el-icon class="source-image-open-icon"><ArrowRight /></el-icon>
            </button>
            <button
              v-if="selectedSource.sourceType !== 'UPLOAD'"
              type="button"
              class="source-image-open-button"
              @click="openSourceSnapshotPreview"
            >
              <el-icon><Document /></el-icon>
              <span>查看源码和快照</span>
              <el-icon class="source-image-open-icon"><ArrowRight /></el-icon>
            </button>
          </div>
        </div>
        <div v-if="sourcePreviewUrl" class="source-url-row">
          <span class="source-url-label">来源链接</span>
          <a
            :href="sourcePreviewUrl"
            target="_blank"
            rel="noopener noreferrer"
            :title="sourcePreviewUrl"
          >
            {{ sourcePreviewUrl }}
            <el-icon><Link /></el-icon>
          </a>
        </div>
        <el-alert
          v-if="sourceOriginalError"
          :title="sourceOriginalError"
          type="error"
          :closable="false"
          show-icon
        />
        <div v-if="sourceDocumentImages.length" class="source-image-grid">
          <el-image
            v-for="(image, index) in sourceDocumentImages"
            :key="`${image}-${index}`"
            :src="sourceImageUrl(image)"
            :preview-src-list="sourceDocumentImages.map(sourceImageUrl)"
            :initial-index="index"
            fit="contain"
            preview-teleported
            class="source-image-preview"
          />
        </div>
        <p v-if="sourceDocumentContent">{{ sourceDocumentContent }}</p>
        <p v-else-if="!sourceDocumentImages.length && !sourceOriginalError">暂无原文内容。</p>
      </div>
    </el-dialog>

    <ContentPreviewDialog
      v-model:visible="sourceSnapshotVisible"
      :title="sourceSnapshotTitle"
      :html="sourceSnapshotHtml"
      :source="sourceSnapshotSource"
      :loading="sourceSnapshotLoading"
      :source-url="sourceSnapshotUrl"
      v-model:localize="sourceSnapshotLocalize"
      v-model:show-source="sourceSnapshotShowSource"
      @localize-change="toggleSourceSnapshotLocalization"
      @closed="closeSourceSnapshot"
    />

  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onActivated, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useThemeStore } from '@/stores/theme'
import { ElMessage, ElMessageBox } from 'element-plus'
import MarkdownIt from 'markdown-it'
import DOMPurify from 'dompurify'
import {
  cancelChatMessage, chatConversationCreate, chatConversationDelete, chatConversationList,
  chatConversationRename, chatMessages, clearChatMessages, generateChatSuggestions, getChatAssistants, getChatModels,
  getChatSourceDocument, getChatSourceDocumentChunks, resumeChatMessageStream, searchDetail, streamChatMessage
} from '@/api'
import ContentPreviewDialog from '@/components/ContentPreviewDialog.vue'
import { resolvePreviewHtml } from '@/utils/previewHtml'
import {
  ArrowDown, ArrowLeft, ArrowRight, ChatDotRound, Cpu, Delete, DocumentCopy, EditPen, FullScreen, Help, Link, Loading, MagicStick, Minus, Moon, Plus,
  Document, Picture, Promotion, Refresh, Setting, Sunny, UserFilled
} from '@element-plus/icons-vue'
import { confirm } from '@/utils/confirm'

interface SourceReference {
  id: string
  chunkId?: string
  knowledgeBaseId?: string
  title: string
  url: string
  spiderName: string
  sourceType: string
  score: number
  snippet: string
}

interface SourceChunk {
  chunkId: string
  index: number
  content: string
  characterCount: number
}

interface ChatMessage {
  id?: number
  role: 'user' | 'assistant'
  text: string
  status?: 'SUCCESS' | 'FAILED' | 'STREAMING' | 'CANCELLED'
  pending?: boolean
  error?: boolean
  reasoningText?: string
  sources?: SourceReference[]
  generationStartedAt?: number
  durationMs?: number
  waitDurationMs?: number
  thinkingDurationMs?: number
  generationDurationMs?: number
}

interface Conversation {
  id: number
  title: string
  messages: ChatMessage[]
  messagesLoaded: boolean
}

const suggestions = ref<string[]>([])
const suggestionsLoading = ref(false)
const suggestionError = ref('')
let suggestionRequestId = 0
let requestedSuggestionKey = ''
const conversations = ref<Conversation[]>([])
const sidebarCollapsed = ref(false)
const sidebarWidth = ref(224)
const isResizingSidebar = ref(false)
const chatPageRef = ref<HTMLElement | null>(null)
const chatSidebarRef = ref<HTMLElement | null>(null)
let sidebarResizeStartX = 0
let sidebarResizeStartWidth = 0
const sidebarMaxWidth = computed(() => {
  const pageWidth = chatPageRef.value?.clientWidth || 1
  return Math.max(160, pageWidth * 0.45)
})
const resizeSidebarTo = (width: number) => {
  sidebarWidth.value = Math.max(160, Math.min(sidebarMaxWidth.value, width))
}
const resizeSidebar = (event: PointerEvent) => {
  resizeSidebarTo(sidebarResizeStartWidth + event.clientX - sidebarResizeStartX)
}
const stopSidebarResize = () => {
  isResizingSidebar.value = false
  window.removeEventListener('pointermove', resizeSidebar)
  window.removeEventListener('pointerup', stopSidebarResize)
  window.removeEventListener('pointercancel', stopSidebarResize)
}
const startSidebarResize = (event: PointerEvent) => {
  if (!chatSidebarRef.value || event.button !== 0) return
  event.preventDefault()
  sidebarResizeStartX = event.clientX
  sidebarResizeStartWidth = chatSidebarRef.value.getBoundingClientRect().width
  isResizingSidebar.value = true
  window.addEventListener('pointermove', resizeSidebar)
  window.addEventListener('pointerup', stopSidebarResize)
  window.addEventListener('pointercancel', stopSidebarResize)
}
const resizeSidebarByKeyboard = (deltaPercent: number) => {
  const pageWidth = chatPageRef.value?.clientWidth || 1
  resizeSidebarTo(sidebarWidth.value + pageWidth * deltaPercent / 100)
}
const selectedSource = ref<SourceReference | null>(null)
const sourcePreviewVisible = ref(false)
const sourcePreviewFullscreen = ref(false)
const sourceOriginalPreviewVisible = ref(false)
const sourceOriginalFullscreen = ref(false)
const sourcePreviewLoading = ref(false)
const sourcePreviewError = ref('')
const sourceOriginalLoading = ref(false)
const sourceOriginalError = ref('')
const sourceDocumentContent = ref('')
const sourceDocumentUrl = ref('')
const sourceDocumentImages = ref<string[]>([])
const sourceSnapshotVisible = ref(false)
const sourceSnapshotLoading = ref(false)
const sourceSnapshotTitle = ref('')
const sourceSnapshotHtml = ref('')
const sourceSnapshotSource = ref('')
const sourceSnapshotUrl = ref('')
const sourceSnapshotLocalize = ref(true)
const sourceSnapshotShowSource = ref(false)
const sourceChunks = ref<SourceChunk[]>([])
const selectedSourceChunk = ref<SourceChunk | null>(null)
const sourcePreviewUrl = computed(() =>
  safeSourceUrl(sourceDocumentUrl.value) || safeSourceUrl(selectedSource.value?.url || '')
)
let sourcePreviewRequestId = 0
let sourceSnapshotRequestId = 0
const assistants = ref<any[]>([])
const chatModels = ref<any[]>([])
const chatModelsLoaded = ref(false)
const selectedAssistantId = ref<string | number | undefined>(undefined)
const selectedChatModelId = ref<string | number | undefined>(undefined)
const route = useRoute()
const router = useRouter()
const themeStore = useThemeStore()
const isStandaloneWindow = computed(() => route.name === 'AiChatWindow')
const themeIcon = computed(() => themeStore.mode === 'light' ? Moon : Sunny)
const themeModeTitle = computed(() =>
  themeStore.mode === 'light' ? '浅色，点击切换为纯黑' : '纯黑，点击切换为浅色'
)
const toggleTheme = () => themeStore.choose(themeStore.mode === 'light' ? 'black' : 'light')
const props = defineProps<{ assistantId?: string | number; embedded?: boolean }>()
const routeAssistantId = computed(() =>
  typeof route.params.assistantId === 'string' ? route.params.assistantId : undefined
)
const effectiveAssistantId = computed(() =>
  props.assistantId !== undefined && props.assistantId !== ''
    ? String(props.assistantId)
    : routeAssistantId.value
)
const queryAssistantId = computed(() =>
  typeof route.query.assistantId === 'string' ? route.query.assistantId : undefined
)
const routeSelectedAssistantId = computed(() =>
  effectiveAssistantId.value || queryAssistantId.value
)
const routeConversationId = computed(() => {
  const value = route.query.conversationId
  if (typeof value !== 'string' || !/^[1-9]\d*$/.test(value)) return null
  const id = Number(value)
  return Number.isSafeInteger(id) ? id : null
})
const assistantsLoaded = ref(false)
const activeAssistant = computed(() => {
  const assistantId = routeSelectedAssistantId.value || selectedAssistantId.value
  return assistants.value.find((assistant: any) => String(assistant.id) === String(assistantId))
})
const activeConversationId = ref<number | null>(null)
const activeConversation = computed(() =>
  conversations.value.find(conversation => conversation.id === activeConversationId.value)
)
const activeStreamingMessage = computed(() =>
  activeConversation.value?.messages
    .slice()
    .reverse()
    .find(message => message.role === 'assistant' && message.status === 'STREAMING' && message.id)
)
const contextWindow = computed(() => {
  const model = chatModels.value.find((item: any) => String(item.id) === String(selectedChatModelId.value))
  const contextLength = Number(model?.contextLength)
  return Number.isFinite(contextLength) && contextLength > 0 ? contextLength : 0
})
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
const sourceTypeLabel = (source: SourceReference) => {
  if (source.sourceType === 'UPLOAD') return '直接上传文档'
  if (source.sourceType === 'ES_INDEX') return 'ES 索引'
  return source.sourceType || '未知'
}
const openSourcePreview = (source: SourceReference) => {
  const requestId = ++sourcePreviewRequestId
  selectedSource.value = source
  sourcePreviewVisible.value = true
  sourcePreviewError.value = ''
  sourceChunks.value = []
  selectedSourceChunk.value = null
  sourcePreviewLoading.value = true
  const conversationId = activeConversationId.value
  if (!conversationId) {
    sourcePreviewError.value = '无法确定来源所属对话'
    sourcePreviewLoading.value = false
    return
  }
  void getChatSourceDocumentChunks(
    conversationId, source.id, source.knowledgeBaseId, source.chunkId
  )
    .then((response: any) => {
      if (requestId !== sourcePreviewRequestId) return
      sourceChunks.value = response?.data || []
      if (sourceChunks.value.length === 1 && !source.chunkId) {
        sourcePreviewLoading.value = false
        sourcePreviewVisible.value = false
        void openSourceOriginalPreview()
        return
      }
      selectedSourceChunk.value = sourceChunks.value.find(chunk => chunk.chunkId === source.chunkId)
        || sourceChunks.value.find(chunk =>
          source.snippet && (chunk.content.startsWith(source.snippet) || source.snippet.startsWith(chunk.content))
        )
        || sourceChunks.value[0]
        || null
      if (!selectedSourceChunk.value) sourcePreviewError.value = '该来源暂无可预览的分块'
    })
    .catch((error: unknown) => {
      if (requestId !== sourcePreviewRequestId) return
      sourcePreviewError.value = error instanceof Error ? error.message : '读取分块失败'
    })
    .finally(() => {
      if (requestId === sourcePreviewRequestId) sourcePreviewLoading.value = false
    })
}
const openSourceOriginalPreview = async () => {
  const source = selectedSource.value
  const conversationId = activeConversationId.value
  if (!source || !conversationId) {
    sourceOriginalError.value = '无法确定来源所属对话'
    sourceOriginalPreviewVisible.value = true
    return
  }
  const requestId = ++sourcePreviewRequestId
  sourceOriginalLoading.value = true
  sourceOriginalError.value = ''
  sourceDocumentContent.value = ''
  sourceDocumentUrl.value = ''
  sourceDocumentImages.value = []
  sourceOriginalPreviewVisible.value = true
  try {
    const response: any = await getChatSourceDocument(
      conversationId, source.id, source.knowledgeBaseId
    )
    if (requestId !== sourcePreviewRequestId) return
    sourceDocumentContent.value = response?.data?.content || ''
    sourceDocumentUrl.value = response?.data?.url || ''
    sourceDocumentImages.value = Array.isArray(response?.data?.images)
      ? response.data.images.filter((image: unknown): image is string => typeof image === 'string' && image.trim().length > 0)
      : []
    if (!sourceDocumentContent.value && !sourceDocumentImages.value.length) {
      sourceOriginalError.value = '原文内容为空'
    }
  } catch (error) {
    if (requestId !== sourcePreviewRequestId) return
    sourceOriginalError.value = error instanceof Error ? error.message : '读取原文失败'
  } finally {
    if (requestId === sourcePreviewRequestId) sourceOriginalLoading.value = false
  }
}
const closeSourcePreview = () => {
  sourcePreviewRequestId++
  selectedSource.value = null
  sourcePreviewLoading.value = false
  sourcePreviewError.value = ''
  sourceChunks.value = []
  selectedSourceChunk.value = null
  sourceOriginalPreviewVisible.value = false
  sourceOriginalLoading.value = false
  sourceOriginalError.value = ''
  sourceDocumentContent.value = ''
  sourceDocumentUrl.value = ''
  sourceDocumentImages.value = []
  sourcePreviewFullscreen.value = false
  sourceOriginalFullscreen.value = false
}
const handleSourcePreviewClosed = () => {
  if (sourceOriginalPreviewVisible.value) return
  closeSourcePreview()
}
const handleSourceOriginalClosed = () => {
  sourceOriginalFullscreen.value = false
  if (!sourcePreviewVisible.value) closeSourcePreview()
}
const sourceImageUrl = (objectName: string) => {
  if (!objectName) return ''
  if (/^https?:\/\//i.test(objectName)) return objectName
  return `/api/file/image?objectName=${encodeURIComponent(objectName)}`
}
const openSourceImagePreview = () => {
  const source = selectedSource.value
  if (!source?.id || !sourceDocumentImages.value.length) return
  const query = new URLSearchParams({
    id: source.id,
    title: source.title || '图片预览'
  })
  const win = window.open(`/image-preview?${query.toString()}`, '_blank')
  if (!win) ElMessage.warning('浏览器阻止了新窗口，请允许弹出窗口后重试')
}
const openSourceSnapshotPreview = async () => {
  const source = selectedSource.value
  if (!source) return
  const requestId = ++sourceSnapshotRequestId
  sourceSnapshotVisible.value = true
  sourceSnapshotLoading.value = true
  sourceSnapshotTitle.value = source.title || '内容预览'
  sourceSnapshotHtml.value = ''
  sourceSnapshotSource.value = ''
  sourceSnapshotUrl.value = ''
  sourceSnapshotLocalize.value = true
  sourceSnapshotShowSource.value = false
  try {
    const response: any = await searchDetail(source.id)
    if (requestId !== sourceSnapshotRequestId) return
    const data = response?.data
    if (!data) throw new Error('未找到来源快照')
    const rawHtml = data.rawHtml || data.content || '无原始内容'
    sourceSnapshotSource.value = rawHtml
    sourceSnapshotUrl.value = safeSourceUrl(data.url || source.url)
    sourceSnapshotHtml.value = resolvePreviewHtml(
      rawHtml, sourceSnapshotUrl.value, sourceSnapshotLocalize.value
    )
  } catch (error) {
    if (requestId !== sourceSnapshotRequestId) return
    sourceSnapshotHtml.value = '加载失败'
    sourceSnapshotSource.value = '加载失败'
    ElMessage.error(error instanceof Error ? error.message : '读取源码和快照失败')
  } finally {
    if (requestId === sourceSnapshotRequestId) sourceSnapshotLoading.value = false
  }
}
const toggleSourceSnapshotLocalization = (enabled: string | number | boolean) => {
  if (!sourceSnapshotSource.value || sourceSnapshotSource.value === '加载失败') return
  sourceSnapshotHtml.value = resolvePreviewHtml(
    sourceSnapshotSource.value, sourceSnapshotUrl.value, Boolean(enabled)
  )
}
const closeSourceSnapshot = () => {
  sourceSnapshotRequestId++
  sourceSnapshotLoading.value = false
  sourceSnapshotHtml.value = ''
  sourceSnapshotSource.value = ''
  sourceSnapshotUrl.value = ''
}
const countCharacters = (value: string) => Array.from(value).length
const formatDuration = (durationMs: number) =>
  durationMs >= 1000 ? `${(durationMs / 1000).toFixed(1)} 秒` : `${Math.round(durationMs)} 毫秒`
const elapsedClock = ref(performance.now())
let elapsedClockTimer: number | undefined
const formatTokenRate = (content: string, durationMs: number) =>
  `约 ${(estimateTokens(content) * 1000 / durationMs).toFixed(1)} Token/秒`
const copyMessage = async (content: string, successMessage = '回答已复制') => {
  try {
    await navigator.clipboard.writeText(content)
    ElMessage.success(successMessage)
  } catch {
    ElMessage.error('复制失败，请检查浏览器剪贴板权限')
  }
}
const draft = ref('')
const topK = ref(5)
const retrievalOptions = computed(() => [...new Set([3, 5, 8, 10, topK.value])].sort((a, b) => a - b))
const sending = ref(false)
const cancelingMessageIds = ref(new Set<number>())
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
const showScrollToBottom = ref(false)
const conversationScrollPositions = new Map<number, number>()
const conversationFollowingBottom = new Map<number, boolean>()
const pendingScrollRestoreId = ref<number | null>(null)
const creatingConversation = ref(false)
const loadingConversations = ref(true)
const loadingMessages = ref(false)
const historyError = ref('')
let conversationLoadRequestId = 0
const markdown = new MarkdownIt({
  breaks: true,
  html: false,
  linkify: true,
  typographer: false
})
markdown.inline.ruler.before('link', 'source_citation', (state, silent) => {
  const match = /^\[(\d+)\]/.exec(state.src.slice(state.pos))
  if (!match) return false
  if (!silent) {
    const token = state.push('source_citation', '', 0)
    token.meta = { index: Number(match[1]) - 1 }
    token.content = match[0]
  }
  state.pos += match[0].length
  return true
})
markdown.renderer.rules.link_open = (tokens, index, options, environment, renderer) => {
  tokens[index].attrSet('target', '_blank')
  tokens[index].attrSet('rel', 'noopener noreferrer')
  return renderer.renderToken(tokens, index, options)
}
markdown.renderer.rules.source_citation = (tokens, index, _options, environment) => {
  const sourceIndex: unknown = tokens[index].meta?.index
  const sourceCount = Number(environment?.sourceCount) || 0
  if (typeof sourceIndex !== 'number'
      || !Number.isInteger(sourceIndex)
      || sourceIndex < 0
      || sourceIndex >= sourceCount) {
    return markdown.utils.escapeHtml(tokens[index].content)
  }
  return `<a class="source-citation" href="#" data-source-index="${sourceIndex}" aria-label="查看参考来源 ${sourceIndex + 1}" title="查看参考来源 ${sourceIndex + 1}">[${sourceIndex + 1}]</a>`
}

const loadConversations = async () => {
  const requestId = ++conversationLoadRequestId
  const assistantId = effectiveAssistantId.value
    || queryAssistantId.value
    || selectedAssistantId.value
  loadingConversations.value = true
  historyError.value = ''
  let selectedConversation: Conversation | undefined
  try {
    const response: any = await chatConversationList(assistantId)
    if (!response) return
    if (requestId !== conversationLoadRequestId) return
    conversations.value = (response.data || []).map((item: any) => ({
      id: item.id,
      title: item.title,
      messages: [],
      messagesLoaded: false
    }))
    selectedConversation = conversations.value.find(item => item.id === routeConversationId.value)
      || conversations.value.find(item => item.id === activeConversationId.value)
      || conversations.value[0]
    activeConversationId.value = selectedConversation?.id ?? null
    updateConversationQuery(selectedConversation?.id ?? null)
    if (selectedConversation) await loadMessages(selectedConversation)
  } catch (error) {
    if (requestId !== conversationLoadRequestId) return
    historyError.value = error instanceof Error ? error.message : '加载聊天记录失败'
  } finally {
    if (requestId === conversationLoadRequestId) loadingConversations.value = false
  }
  if (requestId === conversationLoadRequestId && selectedConversation?.messagesLoaded) {
    await restoreConversationScroll(selectedConversation)
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
      reasoningText: item.reasoningContent || '',
      status: item.status,
      pending: item.status === 'STREAMING',
      error: item.status === 'FAILED',
      sources: item.sources || [],
      durationMs: item.durationMs,
      waitDurationMs: item.waitDurationMs ?? undefined,
      thinkingDurationMs: item.thinkingDurationMs ?? undefined,
      generationDurationMs: item.generationDurationMs
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
  updateConversationQuery(id)
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
    const assistantId = effectiveAssistantId.value
      || queryAssistantId.value
      || selectedAssistantId.value
    const response: any = await chatConversationCreate(assistantId)
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
    updateConversationQuery(conversation.id)
    draft.value = ''
    await restoreConversationScroll(conversation)
  } catch (error) {
    historyError.value = error instanceof Error ? error.message : '创建对话失败'
  } finally {
    creatingConversation.value = false
  }
}

const switchAssistant = async (assistantId: string | number) => {
  const nextQuery = { ...route.query, assistantId: String(assistantId), conversationId: undefined, new: undefined }
  await router.replace({ query: nextQuery })
}

const updateConversationQuery = (conversationId: number | null) => {
  if (!isStandaloneWindow.value) return
  const query = { ...route.query, conversationId: conversationId ? String(conversationId) : undefined }
  void router.replace({ query })
}

const openChatInWindow = () => {
  const query: Record<string, string> = {}
  const assistantId = routeSelectedAssistantId.value || selectedAssistantId.value
  if (assistantId !== undefined && assistantId !== '') query.assistantId = String(assistantId)
  if (activeConversationId.value !== null) query.conversationId = String(activeConversationId.value)
  const { href } = router.resolve({ name: 'AiChatWindow', query })
  window.open(href, '_blank', 'popup=yes,width=1280,height=900,resizable=yes,scrollbars=yes,noopener,noreferrer')
}

const openAssistantManagement = () => {
  void router.push({ name: 'AiAssistants' })
}

const renameConversation = async (conversation: Conversation) => {
  let title: string
  try {
    const result = await ElMessageBox.prompt('请输入新的对话名称', '重命名对话', {
      inputValue: conversation.title,
      inputPlaceholder: '对话名称',
      confirmButtonText: '保存',
      cancelButtonText: '取消',
      inputValidator: value => {
        if (!value?.trim()) return '对话名称不能为空'
        return value.trim().length <= 160 || '对话名称不能超过 160 个字符'
      }
    })
    title = result.value.trim()
  } catch (error) {
    if (error === 'cancel' || error === 'close') return
    ElMessage.error(error instanceof Error ? error.message : '打开重命名窗口失败')
    return
  }
  if (!title || title === conversation.title) return
  historyError.value = ''
  try {
    const response: any = await chatConversationRename(conversation.id, title)
    if (!response) return
    conversation.title = response.data.title
    ElMessage.success('对话名称已更新')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '重命名对话失败')
  }
}

const deleteConversation = async (id: number) => {
  const index = conversations.value.findIndex(conversation => conversation.id === id)
  if (index < 0) return
  const conversation = conversations.value[index]
  if (!await confirm(`确定删除对话“${conversation.title}”吗？此操作无法撤销。`, {
    title: '删除对话',
    confirmText: '删除',
    danger: true
  })) return
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
    else {
      activeConversationId.value = null
      updateConversationQuery(null)
    }
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
const renderAnswerMarkdown = (message: ChatMessage) => DOMPurify.sanitize(
  markdown.render(displayMessageText(message), { sourceCount: message.sources?.length || 0 })
)
const hasInlineSourceCitations = (message: ChatMessage) =>
  Boolean(message.sources?.length && [...message.text.matchAll(/\[(\d+)\]/g)]
    .some(([, number]) => Number(number) > 0 && Number(number) <= message.sources!.length))
const handleAnswerCitationClick = (event: MouseEvent, message: ChatMessage) => {
  const target = event.target
  const container = event.currentTarget
  if (!(target instanceof Element) || !(container instanceof HTMLElement)) return
  const citation = target.closest<HTMLAnchorElement>('.source-citation')
  if (!citation || !container.contains(citation)) return
  event.preventDefault()
  const source = message.sources?.[Number(citation.dataset.sourceIndex)]
  if (source) openSourcePreview(source)
}
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
  const isFollowingBottom =
    messageList.scrollHeight - messageList.clientHeight - messageList.scrollTop <= 40
  conversationScrollPositions.set(conversationId, messageList.scrollTop)
  conversationFollowingBottom.set(conversationId, isFollowingBottom)
  showScrollToBottom.value = !isFollowingBottom
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
  showScrollToBottom.value = !conversationFollowingBottom.get(conversation.id)
  if (pendingScrollRestoreId.value === conversation.id) pendingScrollRestoreId.value = null
}

const scrollToBottom = async (
  conversationId = activeConversationId.value,
  force = true,
  behavior: ScrollBehavior = 'auto'
) => {
  await nextTick()
  const messageList = messageListRef.value
  if (
    !messageList
    || conversationId === null
    || activeConversationId.value !== conversationId
    || (!force && conversationFollowingBottom.get(conversationId) === false)
  ) return
  if (behavior === 'smooth') {
    messageList.scrollTo({ top: messageList.scrollHeight, behavior })
  } else {
    messageList.scrollTop = messageList.scrollHeight
  }
  conversationScrollPositions.set(conversationId, messageList.scrollTop)
  conversationFollowingBottom.set(conversationId, true)
  showScrollToBottom.value = false
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
    } else if (event === 'reasoning') {
      message.reasoningText = (message.reasoningText || '') + payload
    } else if (event === 'complete') {
      message.id = payload.id
      message.text = payload.content ?? message.text
      message.reasoningText = payload.reasoningContent ?? message.reasoningText
      message.status = payload.status
      message.pending = false
      message.error = payload.status === 'FAILED'
      message.sources = payload.sources || []
      message.waitDurationMs = payload.waitDurationMs ?? undefined
      if (payload.status === 'SUCCESS') {
        message.thinkingDurationMs = payload.thinkingDurationMs ?? undefined
        message.generationDurationMs = payload.generationDurationMs
        const elapsedMs = message.generationStartedAt === undefined
          ? undefined
          : performance.now() - message.generationStartedAt
        message.durationMs = typeof payload.durationMs === 'number'
          ? payload.durationMs
          : elapsedMs
        message.generationStartedAt = undefined
      }
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
          message.text.length,
          message.reasoningText?.length || 0
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

const stopMessageGeneration = async (conversationId: number, message: ChatMessage) => {
  if (!message.id || message.status !== 'STREAMING' || cancelingMessageIds.value.has(message.id)) return
  const messageId = message.id
  cancelingMessageIds.value.add(messageId)
  try {
    const response: any = await cancelChatMessage(conversationId, messageId)
    if (response?.data) {
      message.text = response.data.content ?? message.text
      message.reasoningText = response.data.reasoningContent ?? message.reasoningText
      message.status = response.data.status
      message.pending = response.data.status === 'STREAMING'
      message.sources = response.data.sources || []
    }
    if (message.status === 'CANCELLED') ElMessage.info('已中断生成')
    else ElMessage.info('回答已结束，无需中断')
  } catch {
    ElMessage.error('中断生成失败，请重试')
  } finally {
    cancelingMessageIds.value.delete(messageId)
  }
}

const retryFailedMessage = async (message: ChatMessage) => {
  const conversation = activeConversation.value
  if (!conversation || sending.value || message.status !== 'FAILED') return
  const messageIndex = conversation.messages.indexOf(message)
  const question = conversation.messages
    .slice(0, messageIndex)
    .reverse()
    .find(item => item.role === 'user')?.text
  if (!question?.trim()) {
    ElMessage.warning('找不到这条失败回答对应的问题')
    return
  }
  await sendMessage(question)
}

const loadChatSuggestions = async () => {
  if (!assistantsLoaded.value || !chatModelsLoaded.value
    || loadingConversations.value || activeConversation.value?.messages.length) return
  const assistantId = routeSelectedAssistantId.value || selectedAssistantId.value
  const modelId = selectedChatModelId.value
  const requestKey = `${assistantId ?? ''}:${modelId ?? ''}`
  if (requestedSuggestionKey === requestKey
    && (suggestionsLoading.value || suggestions.value.length > 0)) return
  const requestId = ++suggestionRequestId
  requestedSuggestionKey = requestKey
  suggestions.value = []
  suggestionsLoading.value = true
  suggestionError.value = ''
  try {
    const response: any = await generateChatSuggestions({
      assistantId,
      chatModelId: modelId
    })
    if (requestId !== suggestionRequestId) return
    suggestions.value = Array.isArray(response.data)
      ? response.data.filter((question: unknown): question is string =>
          typeof question === 'string' && question.trim().length > 0)
      : []
    if (!suggestions.value.length) suggestionError.value = '暂时无法生成推荐问题，请重试。'
  } catch {
    if (requestId === suggestionRequestId) {
      requestedSuggestionKey = ''
      suggestionError.value = '生成推荐问题失败，请检查聊天模型后重试。'
    }
  } finally {
    if (requestId === suggestionRequestId) suggestionsLoading.value = false
  }
}

const sendSuggestedQuestion = (question: string) => {
  if (sending.value) return
  draft.value = ''
  void sendMessage(question)
}

const sendMessage = async (questionOverride?: string) => {
  const question = (questionOverride ?? draft.value).trim()
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
    pending: true,
    generationStartedAt: performance.now()
  })
  conversation.messages.push(responseMessage)
  conversation.messagesLoaded = true
  if (questionOverride === undefined) draft.value = ''
  await scrollToBottom()

  try {
    const response = await streamChatMessage(conversation.id, {
      question,
      topK: topK.value,
      chatModelId: selectedChatModelId.value
    })
    responseMessage.id = Number(response.headers.get('X-Chat-Message-Id')) || undefined
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

watch(
  [
    assistantsLoaded,
    chatModelsLoaded,
    routeSelectedAssistantId,
    selectedAssistantId,
    selectedChatModelId,
    loadingConversations,
    () => activeConversation.value?.messages.length
  ],
  () => {
    if (loadingConversations.value || activeConversation.value?.messages.length) {
      suggestionRequestId++
      requestedSuggestionKey = ''
      suggestionsLoading.value = false
      return
    }
    void loadChatSuggestions()
  },
  { immediate: true }
)

onMounted(() => {
  elapsedClockTimer = window.setInterval(() => {
    elapsedClock.value = performance.now()
  }, 250)
  if (effectiveAssistantId.value) selectedAssistantId.value = effectiveAssistantId.value
  else if (queryAssistantId.value) selectedAssistantId.value = queryAssistantId.value
  void getChatModels().then((response: any) => {
    chatModels.value = (response.data || [])
      .filter((model: any) => model.enabled !== false)
      .map((model: any) => ({ ...model, id: String(model.id) }))
    if (chatModels.value.length && selectedChatModelId.value === undefined) {
      selectedChatModelId.value = (chatModels.value.find((model: any) => model.defaultModel)
        || chatModels.value[0]).id
    }
  }).catch(() => {
    ElMessage.error('加载聊天模型失败，请刷新页面重试')
  }).finally(() => {
    chatModelsLoaded.value = true
  })
  void getChatAssistants().then(async (response: any) => {
    assistants.value = (response.data || []).filter((assistant: any) => assistant.enabled)
    assistants.value = assistants.value.map((assistant: any) => ({
      ...assistant,
      id: String(assistant.id)
    }))
    assistantsLoaded.value = true
    await syncRouteAssistant(true)
  }).catch(() => {
    ElMessage.error('加载智能体失败，请刷新页面重试')
    loadingConversations.value = false
  })
})
onBeforeUnmount(() => {
  stopSidebarResize()
  suggestionRequestId++
  if (elapsedClockTimer !== undefined) window.clearInterval(elapsedClockTimer)
})
const syncRouteAssistant = async (createNew: boolean) => {
  const assistantId = routeSelectedAssistantId.value
  if (assistantId) {
    selectedAssistantId.value = String(assistantId)
  } else {
    if (!assistants.value.some((item: any) => String(item.id) === String(selectedAssistantId.value))) {
      selectedAssistantId.value = assistants.value[0]?.id
    }
  }
  saveScrollPosition()
  activeConversationId.value = null
  conversations.value = []
  await loadConversations()
  if (createNew && route.query.new === '1' && assistantId
    && routeSelectedAssistantId.value === assistantId) {
    await createConversation()
    if (route.query.new === '1' && routeSelectedAssistantId.value === assistantId) {
      const nextQuery = { ...route.query }
      delete nextQuery.new
      await router.replace({ query: nextQuery })
    }
  }
}
watch(
  [routeSelectedAssistantId, () => route.query.new],
  ([assistantId, newFlag], [previousAssistantId, previousNewFlag]) => {
    if (!assistantsLoaded.value) return
    const assistantChanged = assistantId !== previousAssistantId
    const createRequested = newFlag === '1'
      && (assistantChanged || previousNewFlag !== '1')
    if (assistantChanged || createRequested) {
      void syncRouteAssistant(createRequested)
    }
  }
)
onActivated(() => {
  const conversation = activeConversation.value
  if (conversation) void restoreConversationScroll(conversation)
})
</script>

<style scoped>
.ai-chat-page {
  position: relative;
  display: grid;
  grid-template-columns: var(--chat-sidebar-width) minmax(0, 1fr);
  transition: grid-template-columns .24s ease;
  height: calc(100vh - 136px);
  min-height: 520px;
  overflow: hidden;
  border: 1px solid var(--chrome-line);
  border-radius: 16px;
  background: var(--surface);
  box-shadow: var(--shadow-soft);
  color: var(--ink-900);
}
.ai-chat-page.ai-chat-page--standalone {
  height: 100vh;
  height: 100dvh;
  min-height: 0;
  border: 0;
  border-radius: 0;
}
.ai-chat-page.sidebar-collapsed { grid-template-columns: 0 minmax(0, 1fr); }
.ai-chat-page.is-resizing-sidebar,
.ai-chat-page.is-resizing-sidebar .chat-sidebar { transition: none; }

.chat-sidebar {
  display: flex;
  min-width: 0;
  min-height: 0;
  flex-direction: column;
  overflow: hidden;
  padding: 22px 14px 14px;
  border-right: 1px solid var(--chrome-line);
  background: var(--surface-soft);
  transition: opacity .2s ease, padding .24s ease, border-color .24s ease;
}
.sidebar-collapsed .chat-sidebar {
  padding-right: 0;
  padding-left: 0;
  border-right-color: transparent;
  opacity: 0;
  pointer-events: none;
}
.sidebar-collapsed .chat-main { grid-column: 2; }
.sidebar-resize-handle {
  position: absolute;
  z-index: 3;
  top: 0;
  bottom: 0;
  left: var(--chat-sidebar-width);
  width: 9px;
  transform: translateX(-50%);
  cursor: col-resize;
  touch-action: none;
}
.sidebar-resize-handle::after {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 50%;
  width: 1px;
  background: transparent;
  content: "";
  transition: background-color .16s ease, width .16s ease;
}
.sidebar-resize-handle:hover::after,
.sidebar-resize-handle:focus-visible::after,
.is-resizing-sidebar .sidebar-resize-handle::after {
  width: 2px;
  background: var(--teal);
}
.sidebar-resize-handle:focus-visible { outline: none; }

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
.new-chat-button { width: 100%; height: 40px; align-items: center; justify-content: flex-start; gap: 8px; border-radius: 9px; }
.new-chat-button :deep(.el-icon) { flex: 0 0 auto; margin: 0; }
.new-chat-label { display: inline-flex; align-items: center; margin: 0; line-height: 1; }
.history-heading { display: flex; align-items: center; gap: 8px; margin: 26px 8px 10px; color: var(--ink-500); font-size: 11px; font-weight: 600; }
.history-heading span { display: grid; min-width: 18px; height: 18px; place-items: center; border-radius: 9px; background: var(--chrome-hover); color: var(--ink-700); font-size: 10px; }
.conversation-list { display: flex; min-height: 0; flex-direction: column; gap: 4px; overflow: auto; }
.conversation-item { display: flex; align-items: center; gap: 9px; width: 100%; min-width: 0; padding: 10px 9px; border: 0; border-radius: 8px; background: transparent; color: var(--ink-700); text-align: left; cursor: pointer; transition: background .16s, color .16s; }
.conversation-item:hover, .conversation-item.is-active { background: var(--tint); color: var(--teal-ink); }
.conversation-item > .el-icon { flex: 0 0 auto; font-size: 15px; }
.conversation-title { flex: 1; overflow: hidden; font-size: 12px; text-overflow: ellipsis; white-space: nowrap; }
.conversation-item :deep(.el-tooltip) { min-width: 0; flex: 1; overflow: hidden; }
.rename-conversation, .delete-conversation { display: grid; flex: 0 0 auto; width: 24px; height: 24px; place-items: center; border-radius: 6px; color: var(--ink-500); opacity: 0; }
.conversation-item:hover .rename-conversation, .conversation-item:hover .delete-conversation,
.conversation-item.is-active .rename-conversation, .conversation-item.is-active .delete-conversation { opacity: 1; }
.rename-conversation:hover { background: var(--chrome-hover); color: var(--teal-ink); }
.delete-conversation:hover { background: var(--ctx-danger-bg); color: var(--ctx-danger-ink); }
.history-empty { padding: 12px 8px; color: var(--ink-500); font-size: 11px; }
.history-error { display: grid; gap: 5px; padding: 8px; color: var(--action-danger-ink); font-size: 10px; overflow-wrap: anywhere; }
.history-error button { justify-self: start; padding: 0; border: 0; background: none; color: var(--teal-ink); font-size: 10px; cursor: pointer; }
.sidebar-footer { display: flex; align-items: center; gap: 7px; margin-top: auto; padding: 13px 7px 2px; border-top: 1px solid var(--chrome-line); color: var(--ink-500); font-size: 10px; }
.sidebar-footer .el-icon { color: var(--teal); font-size: 14px; }

.chat-main { display: flex; min-width: 0; min-height: 0; flex-direction: column; }
.chat-header { display: flex; min-height: 72px; align-items: center; justify-content: space-between; gap: 14px; padding: 15px 26px; border-bottom: 1px solid var(--chrome-line); }
.chat-header > div:first-child { min-width: 0; flex: 1; }
.chat-heading { display: flex; align-items: center; gap: 10px; }
.chat-heading-copy { min-width: 0; flex: 1; }
.chat-title-row { display: flex; min-width: 0; align-items: center; gap: 4px; }
.sidebar-toggle { display: grid; width: 32px; height: 32px; flex: 0 0 auto; place-items: center; transform: translateX(-8px); border: 1px solid var(--line-strong); border-radius: 8px; background: var(--surface-soft); color: var(--teal-ink); box-shadow: var(--shadow-soft); cursor: pointer; transition: background .16s, color .16s, border-color .16s; }
.sidebar-toggle .el-icon { font-size: 17px; font-weight: 700; }
.sidebar-toggle:hover { border-color: var(--teal); background: var(--tint); color: var(--teal-ink); }
.sidebar-toggle:focus-visible { outline: 2px solid var(--focus-ring); outline-offset: 2px; }
.chat-title-row h1 { min-width: 0; }
.rename-active-conversation { flex: 0 0 auto; padding: 4px; color: var(--ink-500); }
.rename-active-conversation:hover { color: var(--teal-ink); }
.header-actions { display: flex; flex: 0 0 auto; align-items: center; gap: 10px; }
.assistant-manage-button,
.standalone-chat-button,
.collapsed-new-chat {
  box-sizing: border-box;
  width: 32px;
  height: 32px;
  padding: 0;
  font-size: 0;
  border-radius: 8px;
  border-color: var(--chrome-line);
  background: var(--surface);
  color: var(--ink-700);
}
.collapsed-new-chat { flex: 0 0 32px; }
.assistant-manage-button :deep(.el-icon),
.standalone-chat-button :deep(.el-icon),
.collapsed-new-chat :deep(.el-icon) {
  margin: 0;
  font-size: 16px;
}
.assistant-manage-button:hover,
.assistant-manage-button:focus-visible {
  border-color: var(--line-strong);
  background: var(--chrome-hover);
  color: var(--teal-ink);
}
.standalone-theme-button {
  display: grid;
  width: 32px;
  height: 32px;
  flex: 0 0 auto;
  place-items: center;
  border: 1px solid var(--chrome-line);
  border-radius: 8px;
  background: var(--surface);
  color: var(--ink-700);
  cursor: pointer;
}
.standalone-theme-button:hover,
.standalone-theme-button:focus-visible {
  border-color: var(--line-strong);
  background: var(--chrome-hover);
  color: var(--teal-ink);
}
.standalone-theme-button:focus-visible { outline: 2px solid var(--focus-ring); outline-offset: 2px; }
.assistant-selector { width: 220px; }
.assistant-selector :deep(.el-select__wrapper) {
  min-height: 32px;
  border-radius: 8px;
  background: var(--surface-soft);
  box-shadow: 0 0 0 1px var(--chrome-line) inset;
  transition: background-color .16s ease, box-shadow .16s ease;
}
.assistant-selector :deep(.el-select__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--line-strong) inset;
}
.assistant-selector :deep(.el-select__wrapper.is-focused) {
  background: var(--surface);
  box-shadow: 0 0 0 1px var(--teal) inset, 0 0 0 3px var(--focus-ring);
}
.assistant-selector :deep(.el-select__placeholder),
.assistant-selector :deep(.el-select__selected-item) {
  color: var(--ink-700);
  font-size: 12px;
}
.assistant-selector :deep(.el-select__selection), .assistant-selector :deep(.el-select__selected-item) { min-width: 0; }
.assistant-selector :deep(.el-select__selected-item) { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
:global(.assistant-select-popper) {
  min-width: 220px;
  max-width: 360px;
  padding: 6px;
  border: 1px solid var(--chrome-line);
  border-radius: 10px;
  background: var(--surface);
  box-shadow: var(--shadow-soft);
}
:global(.assistant-select-popper .el-select-dropdown__list) {
  margin: 0;
}
:global(.assistant-select-popper .el-select-dropdown__item) {
  min-height: 36px;
  margin: 2px 0;
  padding: 0 10px;
  border-radius: 7px;
  color: var(--ink-700);
  font-size: 12px;
  line-height: 36px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
:global(.assistant-select-popper .el-select-dropdown__item:hover),
:global(.assistant-select-popper .el-select-dropdown__item.is-hovering) {
  background: var(--chrome-hover);
  color: var(--teal-ink);
}
:global(.assistant-select-popper .el-select-dropdown__item.is-selected) {
  background: var(--tint);
  color: var(--teal-ink);
  font-weight: 650;
}
:global(.assistant-select-popper .el-select-dropdown__item.is-disabled) {
  color: var(--ink-500);
  opacity: .65;
}
:global(.conversation-title-tooltip.el-popper) {
  max-width: min(360px, calc(100vw - 32px));
  padding: 9px 12px;
  border: 1px solid var(--chrome-line);
  border-radius: 9px;
  background: var(--surface);
  box-shadow: var(--shadow-soft);
}
:global(.conversation-title-tooltip .el-popper__content) {
  color: var(--ink-800);
  font-size: 12px;
  line-height: 1.6;
  overflow-wrap: anywhere;
  white-space: normal;
}
:global(.conversation-title-tooltip .el-popper__arrow::before) {
  border-color: var(--chrome-line);
  background: var(--surface);
}
.mobile-new-chat, .mobile-conversations { display: none; }
.chat-header h1 { margin: 0; overflow: hidden; color: var(--ink-950); font-size: 15px; font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }
.assistant-description { margin: 0 0 8px; color: var(--ink-500); font-size: 11px; line-height: 1.5; text-align: center; }
.model-badge { display: flex; flex: 0 0 auto; align-items: center; gap: 7px; padding: 7px 10px; border: 1px solid var(--chrome-line); border-radius: 20px; color: var(--ink-700); font-size: 10px; }
.model-badge span { width: 7px; height: 7px; border-radius: 50%; background: #35b98c; box-shadow: 0 0 0 3px rgba(53, 185, 140, .12); }
.message-list { flex: 1; min-height: 0; overflow: auto; padding: 24px clamp(20px, 6vw, 96px); }
.composer-wrap { position: relative; }
.scroll-to-bottom { position: absolute; z-index: 2; bottom: calc(100% + 8px); left: 50%; width: 36px; height: 36px; transform: translateX(-50%); border-color: var(--chrome-line); background: var(--surface); color: var(--teal-ink); box-shadow: var(--shadow-soft); }
.el-button.scroll-to-bottom:not(.is-text):not(.is-link):hover { transform: translateX(-50%); }
.scroll-to-bottom:hover { border-color: var(--teal); background: var(--tint); color: var(--teal-ink); }
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
.suggestion-feedback { display: flex; align-items: center; gap: 8px; margin: 0; color: var(--ink-500); font-size: 12px; }
.suggestion-error { color: var(--el-color-danger); }
.suggestion-card { display: flex; min-height: 54px; align-items: center; justify-content: space-between; gap: 10px; padding: 11px 13px; border: 1px solid var(--chrome-line); border-radius: 9px; background: var(--surface); color: var(--ink-700); text-align: left; cursor: pointer; transition: border-color .16s, background .16s, transform .16s; }
.suggestion-card:hover { transform: translateY(-1px); border-color: var(--teal); background: var(--tint); color: var(--teal-ink); }
.suggestion-card:disabled { opacity: .6; cursor: not-allowed; transform: none; }
.suggestion-card span { font-size: 11px; line-height: 1.5; }
.suggestion-card .el-icon { flex: 0 0 auto; color: var(--teal); font-size: 14px; }
.message-thread { display: flex; max-width: 900px; flex-direction: column; gap: 30px; margin: 0 auto; }
.message-row { display: flex; align-items: flex-start; gap: 12px; }
.message-user { justify-content: flex-end; }
.message-avatar { display: grid; flex: 0 0 auto; width: 30px; height: 30px; place-items: center; border: 1px solid var(--chrome-line); border-radius: 10px; background: var(--surface-soft); color: var(--ink-700); font-size: 15px; }
.message-assistant .message-avatar { border: 0; background: linear-gradient(145deg, #18b9aa, #087e83); color: #fff; }
.message-content { min-width: 0; flex: 1; padding-top: 2px; }
.reasoning-block { margin: 0 0 12px; padding: 9px 12px; border: 1px solid var(--chrome-line); border-radius: 8px; background: var(--surface-soft); color: var(--ink-900); }
.reasoning-block summary { color: var(--ink-700); font-size: 11px; font-weight: 650; cursor: pointer; }
.reasoning-summary-content { display: inline-flex; flex-wrap: wrap; align-items: center; gap: 4px 12px; margin-left: 12px; }
.reasoning-duration { color: var(--ink-500); font-size: 10px; font-weight: 400; }
.reasoning-content { padding-top: 8px; color: var(--ink-900); font-size: 12px; line-height: 1.7; }

.message-user .message-avatar { order: 2; }
.message-user .message-content { flex: 0 1 auto; max-width: min(78%, 680px); }
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
.message-user .message-content { padding: 0; }
.message-user .user-message-bubble { padding: 10px 14px; border-radius: 12px 4px 12px 12px; background: var(--surface-soft); }
.message-user .message-author { margin-bottom: 3px; text-align: right; }
.copy-user-message-button { display: flex; width: 20px; height: 20px; margin: 4px 4px 0 auto; padding: 0; color: var(--ink-500); font-size: 12px; }
.copy-user-message-button:hover { color: var(--teal-ink); }
.message-text.is-pending-indicator { color: var(--ink-500); }
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
.message-stats { display: flex; flex-wrap: wrap; gap: 5px 12px; margin-top: 7px; color: var(--ink-500); font-size: 10px; }
.copy-message-button { height: auto; margin: 0; color: var(--ink-500); font-size: 10px; }
.copy-message-button:hover { color: var(--teal-ink); }
.failed-message-actions { display: flex; align-items: center; gap: 8px; }
.message-status-failed { display: inline-block; margin-top: 6px; color: var(--action-danger-ink); font-size: 10px; }
.retry-message-button { margin-top: 5px; font-size: 10px; }
.message-status-cancelled { display: inline-block; margin-top: 6px; color: var(--ink-700); font-size: 10px; }
.thinking-icon { margin-right: 5px; vertical-align: -2px; animation: spin 1.2s linear infinite; }
.source-list { display: grid; gap: 7px; max-width: 570px; margin-top: 15px; }
.source-heading { display: flex; align-items: center; gap: 6px; margin-bottom: 2px; color: var(--ink-700); font-size: 10px; font-weight: 650; }
.source-heading .el-icon { color: var(--teal); }
.source-heading span { color: var(--ink-500); font-weight: 400; }
.message-markdown :deep(.source-citation) { display: inline-block; margin: 0 2px; color: var(--teal-ink); font-size: .82em; font-weight: 650; text-decoration: none; vertical-align: super; }
.message-markdown :deep(.source-citation:hover) { text-decoration: underline; }
.source-card { display: flex; width: 100%; min-width: 0; align-items: center; gap: 11px; padding: 10px 12px; border: 1px solid var(--chrome-line); border-radius: 8px; background: var(--surface); color: inherit; text-align: left; cursor: pointer; transition: border-color .16s, background .16s; }
.source-card:hover, .source-card:focus-visible { border-color: var(--teal); background: var(--surface-soft); }
.source-card:focus-visible { outline: 2px solid var(--focus-ring); outline-offset: 2px; }
.source-index { color: var(--teal); font-size: 10px; font-weight: 700; }
.source-copy { display: grid; min-width: 0; flex: 1; gap: 4px; }
.source-copy strong, .source-copy small { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.source-copy strong { color: var(--ink-900); font-size: 11px; font-weight: 600; }
.source-copy small { color: var(--ink-500); font-size: 9px; }
.source-card > .el-icon { color: var(--ink-500); font-size: 13px; }
.source-preview { display: grid; max-height: min(80vh, 820px); gap: 14px; overflow: auto; }
.source-preview-dialog:deep(.el-dialog.is-fullscreen) { display: flex; flex-direction: column; }
.source-preview-dialog:deep(.el-dialog.is-fullscreen .el-dialog__header) { flex: 0 0 auto; }
.source-preview-dialog:deep(.el-dialog.is-fullscreen .el-dialog__body) { width: 100%; flex: 1; min-height: 0; box-sizing: border-box; overflow: hidden; }
.source-preview--fullscreen { width: 100%; height: 100%; max-height: none; min-width: 0; min-height: 0; box-sizing: border-box; grid-template-columns: minmax(0, 1fr); grid-template-rows: auto minmax(0, 1fr); overflow: hidden; }
.source-dialog-header { display: flex; min-width: 0; align-items: center; gap: 16px; padding-right: 100px; }
.source-dialog-title { min-width: 0; overflow: hidden; color: var(--ink-950); font-size: 16px; text-overflow: ellipsis; white-space: nowrap; }
.source-dialog-actions { position: absolute; top: 0; right: 48px; display: grid; width: 48px; height: 48px; place-items: center; }
.source-dialog-actions :deep(.el-button) { display: inline-flex; width: 48px; height: 48px; margin: 0; padding: 0; line-height: 1; }
.source-dialog-actions :deep(.el-icon) { font-size: 16px; }
.source-preview-meta { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.source-preview-actions { display: flex; align-items: center; gap: 12px; }
.source-url-row { display: flex; min-width: 0; align-items: baseline; gap: 10px; padding: 9px 11px; border: 1px solid var(--chrome-line); border-radius: 8px; background: var(--surface-soft); }
.source-url-label { flex: 0 0 auto; color: var(--ink-500); font-size: 11px; font-weight: 600; }
.source-url-row a { display: inline-flex; min-width: 0; align-items: center; gap: 5px; color: var(--teal-ink); font-size: 12px; text-decoration: none; overflow-wrap: anywhere; }
.source-url-row a:hover { text-decoration: underline; }
.source-image-open-button { display: inline-flex; height: 32px; align-items: center; gap: 7px; padding: 0 10px; border: 1px solid var(--teal-tint-border); border-radius: 8px; background: var(--teal-tint-bg); color: var(--teal-tint-text); font: inherit; font-size: 12px; font-weight: 600; cursor: pointer; transition: border-color .16s, background .16s, box-shadow .16s; }
.source-image-open-button:hover { border-color: var(--teal); background: var(--teal-tint-bg-strong); color: var(--teal-tint-text-strong, var(--teal-tint-text)); box-shadow: 0 0 0 2px var(--focus-ring), 0 3px 10px rgba(var(--teal-shadow-soft-rgb), .12); }
.source-image-open-button:focus-visible { outline: 2px solid var(--focus-ring); outline-offset: 2px; }
.source-image-count { display: grid; min-width: 20px; height: 20px; place-items: center; padding: 0 5px; border-radius: 6px; background: var(--teal-tint-chip); font-size: 10px; }
.source-image-open-icon { margin-left: 1px; opacity: .7; }
.source-preview-meta a { display: inline-flex; min-width: 0; align-items: center; gap: 5px; color: var(--teal-ink); font-size: 12px; text-decoration: none; overflow-wrap: anywhere; }
.source-preview-meta a span { min-width: 0; overflow-wrap: anywhere; }
.source-preview-meta a:hover { text-decoration: underline; }
.source-image-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 12px; }
.source-image-preview { width: 100%; height: 180px; border: 1px solid var(--chrome-line); border-radius: 8px; background: var(--surface-soft); cursor: zoom-in; }
.source-preview p { max-height: min(65vh, 640px); overflow: auto; margin: 0; color: var(--ink-800); font-size: 13px; line-height: 1.8; white-space: pre-wrap; overflow-wrap: anywhere; }
.source-preview--fullscreen p { width: 100%; height: 100%; max-height: none; min-width: 0; min-height: 0; box-sizing: border-box; }

.composer-wrap { width: min(100% - 48px, 900px); margin: 0 auto; padding: 8px 0 16px; }
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
.composer-toolbar { display: grid; grid-template-columns: auto minmax(0, 1fr) auto; align-items: center; gap: 10px; padding-top: 7px; }
.composer-options { display: contents; }
.retrieval-control, .send-tools { display: flex; align-items: center; gap: 8px; }
.retrieval-control { color: var(--ink-500); font-size: 10px; }
.retrieval-control :deep(.el-select) { width: 78px; }
.send-tools { gap: 6px; }
.send-tools :deep(.chat-model-selector) { width: 180px; flex: 0 1 180px; }
.send-tools :deep(.chat-model-selector .el-select__wrapper) {
  min-height: 32px;
  padding: 0 9px;
  border-radius: 8px;
  background: var(--surface-soft);
  box-shadow: 0 0 0 1px var(--chrome-line) inset;
  transition: background-color .16s ease, box-shadow .16s ease;
}
.send-tools :deep(.chat-model-selector .el-select__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--line-strong) inset;
}
.send-tools :deep(.chat-model-selector .el-select__wrapper.is-focused) {
  background: var(--surface);
  box-shadow: 0 0 0 1px var(--teal) inset, 0 0 0 3px var(--focus-ring);
}
.send-tools :deep(.chat-model-selector .el-select__selection) {
  min-width: 0;
}
.send-tools :deep(.chat-model-selector .el-select__placeholder),
.send-tools :deep(.chat-model-selector .el-select__selected-item) {
  display: block;
  min-width: 0;
  overflow: hidden;
  color: var(--ink-700);
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.retrieval-control :deep(.el-select__wrapper) {
  min-height: 32px;
  padding: 0 8px;
  border-radius: 8px;
  background: var(--surface-soft);
  box-shadow: 0 0 0 1px var(--chrome-line) inset;
  transition: background-color .16s ease, box-shadow .16s ease;
}
.retrieval-control :deep(.el-select__wrapper:hover) {
  box-shadow: 0 0 0 1px var(--line-strong) inset;
}
.retrieval-control :deep(.el-select__wrapper.is-focused) {
  background: var(--surface);
  box-shadow: 0 0 0 1px var(--teal) inset, 0 0 0 3px var(--focus-ring);
}
.retrieval-control :deep(.el-select__selected-item) {
  color: var(--ink-700);
  font-size: 10px;
}
.context-usage { display: flex; min-width: 0; align-items: center; justify-content: center; }
.context-usage-trigger { position: relative; display: grid; width: 44px; height: 44px; place-items: center; padding: 2px; border: 0; border-radius: 50%; background: transparent; cursor: pointer; }
.context-usage-trigger:focus-visible { outline: 2px solid var(--focus-ring); outline-offset: 2px; }
.context-usage-percent { position: absolute; inset: 0; display: grid; place-items: center; color: var(--ink-700); font-size: 9px; line-height: 1; pointer-events: none; }
.context-usage-details { display: grid; gap: 8px; color: var(--ink-700); font-size: 12px; }
.context-usage-details strong { color: var(--ink-900); font-size: 13px; }
.context-usage-details small { color: var(--ink-500); font-size: 11px; line-height: 1.5; }
.context-usage-warning { color: var(--action-danger-ink); font-size: 11px; }
.keyboard-hint { color: var(--ink-500); font-size: 9px; }
.send-button { width: 64px; height: 32px; padding: 0 12px; border-radius: 8px; font-size: 11px; }
.send-button .el-icon { margin-right: 5px; }
.stop-button { padding: 0; background: var(--el-color-danger-light-9); color: var(--el-color-danger); }
.stop-button:hover { background: var(--el-color-danger-light-8); color: var(--el-color-danger); }
.stop-button:focus-visible { outline: 2px solid var(--focus-ring); outline-offset: 2px; }
.stop-square-icon { display: block; width: 12px; height: 12px; margin: auto; border-radius: 2px; background: currentColor; }
.disclaimer { margin: 7px 0 0; color: var(--ink-500); font-size: 9px; text-align: center; }
@keyframes spin { to { transform: rotate(360deg); } }

@media (max-width: 800px) {
  .ai-chat-page { height: calc(100vh - 112px); min-height: 460px; }
  .chat-sidebar { padding: 18px 10px 12px; }
  .message-list { padding-right: 22px; padding-left: 22px; }
  .composer-wrap { width: calc(100% - 36px); }
}
@media (min-width: 801px) and (max-width: 1100px) {
  .ai-chat-page { height: calc(100vh - 112px); }
}
@media (max-width: 600px) {
  .ai-chat-page { grid-template-columns: minmax(0, 1fr); height: calc(100dvh - 84px); min-height: 420px; border-radius: 12px; }
  .ai-chat-page.sidebar-collapsed { grid-template-columns: minmax(0, 1fr); }
  .ai-chat-page.ai-chat-page--standalone { grid-template-columns: minmax(0, 1fr); }
  .chat-sidebar { display: none; }
  .sidebar-resize-handle { display: none; }
  .sidebar-collapsed .chat-main { grid-column: 1; }
  .sidebar-toggle { display: none; }
  .chat-header { display: grid; min-height: 0; grid-template-columns: minmax(0, 1fr); gap: 8px; padding: 12px 15px; }
  .chat-heading { min-width: 0; }
  .header-actions { width: 100%; min-width: 0; gap: 6px; }
  .assistant-manage-button :deep(.el-icon),
  .standalone-chat-button :deep(.el-icon) { font-size: 14px; }
  .collapsed-new-chat { display: none; }
  .assistant-selector { width: auto; min-width: 0; flex: 1 1 110px; }
  .assistant-description { margin-bottom: 6px; font-size: 10px; }
  .model-badge { display: none; }
  .mobile-new-chat {
    display: inline-flex;
    flex: 0 0 auto;
    box-sizing: border-box;
    height: 29px;
    padding: 0 8px;
    border-radius: 7px;
    font-size: 10px;
    white-space: nowrap;
  }
  .mobile-new-chat .el-icon { margin-right: 3px; }
  .mobile-conversations { display: flex; gap: 6px; overflow-x: auto; padding: 8px 12px; border-bottom: 1px solid var(--chrome-line); scrollbar-width: none; }
  .mobile-conversations::-webkit-scrollbar { display: none; }
  .mobile-conversation { max-width: 150px; flex: 0 0 auto; overflow: hidden; padding: 6px 10px; border: 1px solid var(--chrome-line); border-radius: 16px; background: var(--surface); color: var(--ink-700); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; cursor: pointer; }
  .mobile-conversation :deep(.el-tooltip), .mobile-conversation :deep(.el-tooltip__trigger) { display: block; max-width: 100%; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .mobile-conversation.is-active { border-color: var(--teal); background: var(--tint); color: var(--teal-ink); }
  .composer-toolbar { grid-template-columns: minmax(0, 1fr) auto; gap: 6px; }
  .retrieval-control { display: grid; grid-column: 1 / -1; grid-row: 1; grid-template-columns: auto minmax(0, 1fr) auto minmax(0, 1.2fr); min-width: 0; gap: 5px; white-space: nowrap; }
  .retrieval-control :deep(.el-select) { width: 100%; min-width: 0; }
  .send-tools :deep(.chat-model-selector) { width: 144px; flex-basis: 144px; }
  .retrieval-control :deep(.el-select__wrapper) { min-width: 0; padding: 0 5px; }
  .retrieval-control :deep(.el-select__selected-item) { font-size: 9px; }
  .context-usage { grid-column: 1; grid-row: 2; justify-content: flex-start; }
  .send-tools { grid-column: 2; grid-row: 2; justify-content: flex-end; }
  .reasoning-summary-content { display: flex; width: 100%; box-sizing: border-box; gap: 4px 10px; margin-top: 6px; margin-left: 0; }
  .reasoning-duration { min-width: 0; overflow-wrap: anywhere; }
  .message-list { padding: 16px 14px; }
  .suggestion-list { grid-template-columns: 1fr; }
  .suggestion-card { min-height: 44px; }
  .message-thread { gap: 22px; }
  .composer-wrap { width: calc(100% - 24px); padding-bottom: 9px; }
  .keyboard-hint { display: none; }
}
@media (max-width: 380px) {
  .chat-header { padding-right: 10px; padding-left: 10px; }
  .header-actions { gap: 4px; }
  .assistant-manage-button { width: 29px; }
  .mobile-new-chat { padding-right: 6px; padding-left: 6px; }
  .send-tools :deep(.chat-model-selector) { width: 128px; flex-basis: 128px; }
  .retrieval-control { grid-template-columns: auto minmax(0, .8fr) auto minmax(0, 1.2fr); gap: 4px; font-size: 9px; }
  .composer { padding-right: 9px; padding-left: 9px; }
}
</style>
