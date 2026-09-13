<template>
  <div>
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card>
          <div class="stat-card">
            <div class="stat-icon" style="background:#409EFF">
              <el-icon :size="28"><Connection /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.spiderCount }}</div>
              <div class="stat-label">总爬虫数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <div class="stat-card">
            <div class="stat-icon" style="background:#67C23A">
              <el-icon :size="28"><List /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.taskCount }}</div>
              <div class="stat-label">今日任务</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <div class="stat-card">
            <div class="stat-icon" style="background:#E6A23C">
              <el-icon :size="28"><CircleCheck /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.successRate }}%</div>
              <div class="stat-label">成功率</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card>
          <div class="stat-card">
            <div class="stat-icon" style="background:#F56C6C">
              <el-icon :size="28"><DataBoard /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ stats.dataCount }}</div>
              <div class="stat-label">数据量</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" style="margin-top:20px">
      <el-col :span="16">
        <el-card>
          <template #header><span>最近任务</span></template>
          <el-table :data="recentTasks" size="small">
            <el-table-column prop="spiderName" label="爬虫名称" />
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusTag(row.status)">{{ row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="successCount" label="成功" width="80" />
            <el-table-column prop="failCount" label="失败" width="80" />
            <el-table-column prop="createTime" label="创建时间" width="180" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card>
          <template #header><span>系统状态</span></template>
          <div class="system-status">
            <div class="status-item"><el-tag type="success">MySQL</el-tag></div>
            <div class="status-item"><el-tag type="success">Elasticsearch</el-tag></div>
            <div class="status-item"><el-tag type="success">MinIO</el-tag></div>
            <div class="status-item"><el-tag type="success">Kafka</el-tag></div>
            <div class="status-item"><el-tag type="success">Redis</el-tag></div>
            <div class="status-item"><el-tag type="success">Nacos</el-tag></div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { spiderPage, taskPage, searchContent } from '@/api'
import { Connection, List, CircleCheck, DataBoard } from '@element-plus/icons-vue'

const stats = ref({ spiderCount: 0, taskCount: 0, successRate: 0, dataCount: 0 })
const recentTasks = ref<any[]>([])

const statusTag = (status: string) => {
  const map: Record<string, string> = {
    RUNNING: 'primary', SUCCESS: 'success', FAILED: 'danger',
    PENDING: 'warning', CANCELED: 'info'
  }
  return map[status] || 'info'
}

onMounted(async () => {
  try {
    const spiderRes: any = await spiderPage({ current: 1, size: 1 })
    stats.value.spiderCount = spiderRes.data?.total || 0

    const taskRes: any = await taskPage({ current: 1, size: 5 })
    recentTasks.value = taskRes.data?.records || []
    stats.value.taskCount = taskRes.data?.total || 0
    const all = taskRes.data?.records || []
    const total = all.reduce((s: number, t: any) => s + (t.successCount || 0) + (t.failCount || 0), 0)
    const success = all.reduce((s: number, t: any) => s + (t.successCount || 0), 0)
    stats.value.successRate = total > 0 ? Math.round(success / total * 100) : 0

    const searchRes: any = await searchContent({ current: 1, size: 1 })
    stats.value.dataCount = searchRes.data?.totalElements || 0
  } catch (e) {
    console.error(e)
  }
})
</script>

<style scoped>
.stat-card { display: flex; align-items: center; gap: 16px; }
.stat-icon {
  width: 56px; height: 56px; border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  color: #fff;
}
.stat-value { font-size: 28px; font-weight: bold; color: #333; }
.stat-label { font-size: 14px; color: #999; margin-top: 4px; }
.system-status .status-item { margin-bottom: 12px; }
</style>
