<template>
  <el-dialog
    v-model="visible"
    :title="instrumentType === 'FUND' ? '基金同步管理' : '股票同步管理'"
    width="min(620px, 94vw)"
  >
    <div class="dialog-intro">
      {{ instrumentType === 'FUND'
        ? '更新基金目录，或同步全部基金的历史行情；也可查看同步记录和配置行情 API。'
        : '更新 A 股目录，或同步全部股票的历史行情；也可查看同步记录和配置行情 API。' }}
    </div>
    <div class="sync-actions">
      <section class="sync-action">
        <div class="action-copy">
          <h3>目录数据</h3>
          <p>刷新可查询和管理的{{ instrumentType === 'FUND' ? '基金' : '股票' }}列表。</p>
        </div>
        <el-button type="primary" :icon="Refresh" :loading="catalogLoading" @click="syncCatalog">
          {{ instrumentType === 'FUND' ? '同步基金目录' : '同步股票目录' }}
        </el-button>
      </section>
      <section class="sync-action">
        <div class="action-copy">
          <h3>历史行情</h3>
          <p>为目录中的全部{{ instrumentType === 'FUND' ? '基金' : '股票' }}创建后台同步任务。</p>
        </div>
        <el-button :icon="DataLine" :loading="historyLoading" @click="startHistorySync">
          同步全部历史行情
        </el-button>
      </section>
    </div>
    <div class="history-actions">
      <el-button text :icon="List" :loading="recordsLoading" @click="openLatestRecords">同步记录</el-button>
      <el-button text :icon="Setting" @click="historyPanel?.openConcurrencySettings()">API 与并发设置</el-button>
    </div>

    <HistorySyncTaskPanel ref="historyPanel" :instrument-type="instrumentType" />
  </el-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { DataLine, List, Refresh, Setting } from '@element-plus/icons-vue'
import HistorySyncTaskPanel from '@/components/HistorySyncTaskPanel.vue'
import { fundCatalogSync, stockCatalogSync } from '@/api'
import { confirm } from '@/utils/confirm'

type InstrumentType = 'FUND' | 'STOCK'

const props = defineProps<{ instrumentType: InstrumentType }>()
const emit = defineEmits<{
  catalogSynced: []
}>()

const visible = ref(false)
const catalogLoading = ref(false)
const historyLoading = ref(false)
const recordsLoading = ref(false)
const historyPanel = ref<InstanceType<typeof HistorySyncTaskPanel> | null>(null)

const open = () => {
  visible.value = true
}

const syncCatalog = async () => {
  const catalogName = props.instrumentType === 'FUND' ? '基金' : '股票'
  if (!await confirm(
    `将从数据源重新获取并更新全部${catalogName}目录信息。确认开始同步？`,
    { title: `同步${catalogName}目录`, confirmText: '开始同步' }
  )) return

  catalogLoading.value = true
  try {
    if (props.instrumentType === 'FUND') {
      const response: any = await fundCatalogSync()
      const result = response.data
      ElMessage.success(`基金目录同步完成：共 ${result.total} 只（场内 ${result.onshore}，场外 ${result.offshore}）`)
    } else {
      const response: any = await stockCatalogSync()
      ElMessage.success(`股票目录同步完成：共 ${response.data.total.toLocaleString()} 只`)
    }
    emit('catalogSynced')
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '目录同步失败')
  } finally {
    catalogLoading.value = false
  }
}

const startHistorySync = async () => {
  historyLoading.value = true
  try {
    await historyPanel.value?.startTask()
  } finally {
    historyLoading.value = false
  }
}

const openLatestRecords = async () => {
  recordsLoading.value = true
  try {
    await historyPanel.value?.openLatest()
  } finally {
    recordsLoading.value = false
  }
}

defineExpose({ open })
</script>

<style scoped>
.dialog-intro {
  margin-bottom: 20px;
  color: var(--ink-500);
  font-size: 13px;
  line-height: 1.6;
}
.sync-actions {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.sync-action {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-fill-color-extra-light);
}
.action-copy {
  min-width: 0;
}
.action-copy h3 {
  margin: 0;
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
}
.action-copy p {
  margin: 5px 0 0;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.5;
}
.sync-action .el-button {
  flex: 0 0 auto;
  min-width: 152px;
  margin-left: 0;
}
.history-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 12px;
  margin-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.history-actions .el-button {
  margin-left: 0;
}
@media (max-width: 520px) {
  .sync-action {
    align-items: stretch;
    flex-direction: column;
    gap: 12px;
  }
  .sync-action .el-button {
    width: 100%;
  }
  .history-actions {
    justify-content: stretch;
  }
  .history-actions .el-button {
    flex: 1;
  }
}
</style>
