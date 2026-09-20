<template>
  <div class="dashboard-page">
    <section class="dashboard-intro">
      <div>
        <p class="eyebrow">CRAWLEROS / OVERVIEW</p>
        <h1>数据运营总览</h1>
        <p class="intro-copy">掌握采集任务、内容资产与系统服务的实时状态。</p>
      </div>
      <div class="intro-mark"><el-icon :size="25"><DataAnalysis /></el-icon><span>LIVE</span></div>
    </section>
    <el-row :gutter="20">
      <el-col v-for="stat in statCards" :key="stat.label" :xs="24" :sm="12" :lg="6">
        <el-card>
          <div class="stat-card" :class="stat.tone">
            <div class="stat-icon"><el-icon :size="25"><component :is="stat.icon" /></el-icon></div>
            <div class="stat-info">
              <div class="stat-label">{{ stat.label }}</div>
              <div class="stat-value">{{ stat.value }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="dashboard-grid">
      <el-col :xs="24" :lg="16">
        <el-card>
          <template #header><div class="section-heading"><span>最近任务</span><small>LAST 5 RUNS</small></div></template>
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
      <el-col :xs="24" :lg="8">
        <el-card>
          <template #header><div class="section-heading"><span>系统状态</span><small class="healthy"><i></i> ALL SYSTEMS GO</small></div></template>
          <div class="system-status">
            <div v-for="service in services" :key="service" class="status-item"><i></i><span>{{ service }}</span><el-tag type="success" effect="plain">正常</el-tag></div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { spiderPage, taskPage, searchContent } from '@/api'
import { Connection, List, CircleCheck, DataBoard, DataAnalysis } from '@element-plus/icons-vue'

const stats = ref({ spiderCount: 0, taskCount: 0, successRate: 0, dataCount: 0 })
const recentTasks = ref<any[]>([])
const services = ['MySQL', 'Elasticsearch', 'MinIO', 'Kafka', 'Redis', 'Nacos']
const statCards = [
  { label: '总爬虫数', tone: 'teal', icon: Connection, get value() { return stats.value.spiderCount } },
  { label: '今日任务', tone: 'blue', icon: List, get value() { return stats.value.taskCount } },
  { label: '成功率', tone: 'orange', icon: CircleCheck, get value() { return `${stats.value.successRate}%` } },
  { label: '数据总量', tone: 'red', icon: DataBoard, get value() { return stats.value.dataCount } }
]

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
    stats.value.spiderCount = spiderRes?.data?.total || 0

    const taskRes: any = await taskPage({ current: 1, size: 5 })
    recentTasks.value = taskRes?.data?.records || []
    stats.value.taskCount = taskRes?.data?.total || 0
    const all = taskRes?.data?.records || []
    const total = all.reduce((s: number, t: any) =>
      s + (t.htmlSuccessCount || 0) + (t.htmlFailCount || 0) + (t.imageSuccessCount || 0) + (t.imageFailCount || 0), 0)
    const success = all.reduce((s: number, t: any) =>
      s + (t.htmlSuccessCount || 0) + (t.imageSuccessCount || 0), 0)
    stats.value.successRate = total > 0 ? Math.round(success / total * 100) : 0

    const searchRes: any = await searchContent({ current: 1, size: 1 })
    stats.value.dataCount = searchRes?.data?.totalElements || 0
  } catch (e) {
    console.error(e)
  }
})
</script>

<style scoped>
.dashboard-intro { display: flex; align-items: flex-end; justify-content: space-between; margin-bottom: 24px; }
.eyebrow { margin-bottom: 8px; color: #16a6a3; font-size: 11px; font-weight: 800; letter-spacing: 1.8px; }
.dashboard-intro h1 { color: #172b4d; font-size: 25px; letter-spacing: -.4px; }
.intro-copy { margin-top: 8px; color: #829ab1; font-size: 14px; }
.intro-mark { display: flex; align-items: center; gap: 8px; color: #16a6a3; font-size: 11px; font-weight: 800; letter-spacing: 1.5px; }
.intro-mark span { padding: 5px 8px; border-radius: 5px; background: #d9f5ef; }
.stat-card { display: flex; align-items: center; gap: 16px; min-height: 76px; }
.stat-icon {
  width: 56px; height: 56px; border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  color: #fff;
}
.stat-card.teal .stat-icon { color: #0f9f9a; background: #e1f5f3; }
.stat-card.blue .stat-icon { color: #4776b5; background: #e7effb; }
.stat-card.orange .stat-icon { color: #c78335; background: #fff1dd; }
.stat-card.red .stat-icon { color: #d96555; background: #fce9e6; }
.stat-value { margin-top: 5px; font-size: 28px; font-weight: 800; color: #102a43; line-height: 1; }
.stat-label { color: #829ab1; font-size: 13px; }
.dashboard-grid { margin-top: 20px; }
.section-heading { display: flex; align-items: center; justify-content: space-between; }
.section-heading small { color: #9fb3c8; font-size: 10px; font-weight: 800; letter-spacing: 1px; }
.section-heading .healthy { color: #16a6a3; }
.healthy i, .status-item i { display: inline-block; width: 7px; height: 7px; margin-right: 5px; border-radius: 50%; background: #16a6a3; }
.system-status { display: grid; grid-template-columns: 1fr 1fr; gap: 4px 20px; }
.status-item { display: flex; align-items: center; min-height: 38px; color: #486581; font-size: 13px; }
.status-item > i { margin-right: 9px; }
.status-item .el-tag { margin-left: auto; font-size: 11px; }
@media (max-width: 600px) {
  .dashboard-intro { align-items: flex-start; }
  .intro-mark { display: none; }
  .system-status { grid-template-columns: 1fr; }
}
</style>
