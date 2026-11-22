<template>
  <div class="ai-models-page">
    <div class="page-header">
      <div>
        <h2>模型管理</h2>
        <p>统一管理向量模型和大语言模型配置。</p>
      </div>
    </div>
    <el-tabs :model-value="activeTab" @tab-change="changeTab">
      <el-tab-pane label="大语言模型" name="chat" lazy>
        <AiChatModels embedded />
      </el-tab-pane>
      <el-tab-pane label="向量模型" name="vector" lazy>
        <AiVectorModels embedded />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AiChatModels from '@/views/AiChatModels.vue'
import AiVectorModels from '@/views/AiVectorModels.vue'

const route = useRoute()
const router = useRouter()
const activeTab = computed(() => route.query.tab === 'vector' ? 'vector' : 'chat')

const changeTab = (tab: string | number) => {
  const query = { ...route.query }
  if (tab === 'vector') delete query.tab
  else query.tab = String(tab)
  void router.replace({ query })
}
</script>

<style scoped>
.ai-models-page { margin: 16px; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.page-header h2 { margin: 0 0 6px; color: var(--ink-900); }
.page-header p { margin: 0; color: var(--ink-500); }
</style>
