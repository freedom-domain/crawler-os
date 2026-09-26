<template>
  <div class="dashboard-page">
    <section class="dashboard-hero">
      <div class="hero-copy">
        <p class="eyebrow">CRAWLEROS / CONTROL ROOM</p>
        <p class="intro-copy">采集任务、内容资产与服务状态，都在这里保持清晰。</p>
        <div class="hero-status"><span class="status-pulse"></span>系统运行正常 <b>·</b> 实时同步中</div>
      </div>
      <div class="hero-orbit" aria-hidden="true">
        <div class="orbit-ring ring-one"></div>
        <div class="orbit-ring ring-two"></div>
        <div class="orbit-core"><el-icon :size="30"><DataAnalysis /></el-icon></div>
      </div>
    </section>

    <section class="metric-grid">
      <article v-for="(stat, index) in statCards" :key="stat.label" v-loading="statLoading(index)" element-loading-text="加载中..." class="metric-card" :class="`metric-${stat.tone}`">
        <div class="metric-top"><span class="metric-index">0{{ index + 1 }}</span><el-icon :size="20"><component :is="stat.icon" /></el-icon></div>
        <div class="stat-label">{{ stat.label }}</div>
        <div class="stat-value">{{ stat.value }}</div>
        <div class="metric-rule"><span></span></div>
      </article>
    </section>

    <section class="dashboard-grid">
      <div v-loading="recentTaskLoading" element-loading-text="加载中..." class="panel task-panel">
        <div class="panel-heading">
          <div><p class="panel-kicker">ACTIVITY STREAM</p><h2>最近任务</h2></div>
          <span class="panel-count">{{ recentTasks.length }} 条记录</span>
        </div>
        <el-table :data="recentTasks" size="small" resizable border>
          <el-table-column prop="spiderName" label="爬虫名称" min-width="150" resizable />
          <el-table-column prop="status" label="状态" min-width="100" resizable>
            <template #default="{ row }"><el-tag :type="statusTag(row.status)">{{ row.status }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="successCount" label="成功" min-width="80" resizable />
          <el-table-column prop="failCount" label="失败" min-width="80" resizable />
          <el-table-column prop="createTime" label="创建时间" min-width="180" resizable />
        </el-table>
        <div v-if="!recentTasks.length" class="panel-empty">暂无最近任务</div>
      </div>

      <div class="panel service-panel">
        <div class="panel-heading"><div><p class="panel-kicker">INFRASTRUCTURE</p><h2>服务状态</h2></div><span class="healthy"><i></i> ALL SYSTEMS GO</span></div>
        <div class="system-status">
          <div v-for="service in services" :key="service" class="status-item"><span class="service-dot"></span><span>{{ service }}</span><el-tag type="success" effect="plain">正常</el-tag></div>
        </div>
        <div class="service-footer"><span>平台服务健康度</span><strong>100%</strong></div>
        <div class="health-bar"><span></span></div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, onActivated } from 'vue'
import { spiderPage, taskStats, recentTaskList, searchContent } from '@/api'
import { Connection, List, CircleCheck, DataBoard, DataAnalysis } from '@element-plus/icons-vue'

const stats = ref({ spiderCount: 0, taskCount: 0, successRate: 0, dataCount: 0 })
const recentTasks = ref<any[]>([])
const spiderLoading = ref(false)
const taskLoading = ref(false)
const recentTaskLoading = ref(false)
const dataLoading = ref(false)
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

const statLoading = (index: number) => {
  if (index === 0) return spiderLoading.value
  if (index === 1 || index === 2) return taskLoading.value
  return dataLoading.value
}

const loadSpiderStats = async () => {
  spiderLoading.value = true
  try {
    const spiderRes: any = await spiderPage({ current: 1, size: 1 })
    stats.value.spiderCount = spiderRes?.data?.total ?? spiderRes?.data?.page?.totalElements ?? 0
  } catch (e) {
    console.error(e)
  } finally {
    spiderLoading.value = false
  }
}

const loadTaskStats = async () => {
  taskLoading.value = true
  try {
    const response: any = await taskStats()
    const total = response?.data?.total ?? 0
    const success = response?.data?.success ?? 0
    stats.value.taskCount = total
    stats.value.successRate = total > 0 ? Math.round(success / total * 100) : 0
  } catch (e) {
    console.error(e)
  } finally {
    taskLoading.value = false
  }
}

const loadDataStats = async () => {
  dataLoading.value = true
  try {
    const searchRes: any = await searchContent({ current: 1, size: 1 })
    stats.value.dataCount = searchRes?.data?.total ?? searchRes?.data?.totalElements ?? searchRes?.data?.page?.totalElements ?? 0
  } catch (e) {
    console.error(e)
  } finally {
    dataLoading.value = false
  }
}

const loadRecentTasks = async () => {
  recentTaskLoading.value = true
  try {
    const response: any = await recentTaskList(5)
    recentTasks.value = response?.data || []
  } catch (e) {
    console.error(e)
    recentTasks.value = []
  } finally {
    recentTaskLoading.value = false
  }
}

const loadDashboard = () => {
  recentTasks.value = []
  void Promise.all([loadSpiderStats(), loadTaskStats(), loadRecentTasks(), loadDataStats()])
}

onActivated(loadDashboard)
</script>

<style scoped>
.dashboard-page { max-width: 1500px; margin: 0 auto; }
.dashboard-hero { position: relative; display: flex; align-items: center; justify-content: space-between; min-height: 238px; margin-bottom: 22px; padding: 34px 46px; overflow: hidden; border-radius: 18px; color: #f5fffd; background: #123b47; box-shadow: 0 18px 34px rgba(18, 59, 71, .18); }
.dashboard-hero::after { content: ''; position: absolute; width: 430px; height: 430px; right: 10%; top: -230px; border: 1px solid rgba(114, 224, 200, .18); border-radius: 50%; box-shadow: 0 0 0 32px rgba(114, 224, 200, .04), 0 0 0 64px rgba(114, 224, 200, .03); }
.hero-copy { position: relative; z-index: 1; }
.eyebrow, .panel-kicker { margin: 0 0 10px; color: #72e0c8; font-size: 10px; font-weight: 800; letter-spacing: 2px; }
.dashboard-hero h1 { margin: 0; color: #fff; font-size: 34px; font-weight: 850; letter-spacing: -.8px; }
.intro-copy { margin: 12px 0 18px; color: #b9d6d5; font-size: 14px; }
.hero-status { display: flex; align-items: center; gap: 8px; color: #d6f3e9; font-size: 12px; }
.hero-status b { color: #72e0c8; }
.status-pulse, .service-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; background: #72e0c8; box-shadow: 0 0 0 5px rgba(114, 224, 200, .12); }
.hero-orbit { position: relative; z-index: 1; width: 150px; height: 150px; margin-right: 12%; }
.orbit-ring { position: absolute; inset: 0; border: 1px solid rgba(114, 224, 200, .27); border-radius: 50%; transform: rotate(-22deg) skewX(-15deg); }
.ring-two { inset: 18px -18px; transform: rotate(52deg) skewX(12deg); border-color: rgba(237, 118, 95, .4); }
.orbit-core { position: absolute; inset: 48px; display: grid; place-items: center; border-radius: 50%; color: #123b47; background: #72e0c8; box-shadow: 0 0 0 10px rgba(114, 224, 200, .1), 0 10px 24px rgba(0, 0, 0, .2); }
.metric-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; margin-bottom: 22px; }
.metric-card { position: relative; min-height: 142px; padding: 18px 20px; overflow: hidden; border: 1px solid #dfe7ee; border-radius: 14px; background: #fff; box-shadow: 0 7px 18px rgba(23, 43, 77, .05); }
.metric-card::after { content: ''; position: absolute; width: 90px; height: 90px; right: -32px; bottom: -36px; border-radius: 50%; background: var(--metric-soft); }
.metric-top { display: flex; justify-content: space-between; align-items: center; color: var(--metric-color); }
.metric-index { color: #9fb3c8; font-size: 10px; font-weight: 800; letter-spacing: 1px; }
.stat-label { margin-top: 18px; color: #657b91; font-size: 12px; }
.stat-value { margin-top: 4px; color: #172b4d; font-size: 29px; font-weight: 850; line-height: 1; }
.metric-rule { width: 72px; height: 3px; margin-top: 14px; overflow: hidden; border-radius: 3px; background: #edf2f4; }
.metric-rule span { display: block; width: 65%; height: 100%; background: var(--metric-color); }
.metric-teal { --metric-color: #0f9f9a; --metric-soft: #d9f5ef; }.metric-blue { --metric-color: #4776b5; --metric-soft: #e7effb; }.metric-orange { --metric-color: #c78335; --metric-soft: #fff1dd; }.metric-red { --metric-color: #d96555; --metric-soft: #fce9e6; }
.dashboard-grid { display: grid; grid-template-columns: minmax(0, 1.65fr) minmax(300px, .9fr); gap: 22px; }
.panel { min-width: 0; padding: 24px; border: 1px solid #dfe7ee; border-radius: 14px; background: #fff; box-shadow: 0 7px 18px rgba(23, 43, 77, .05); }
.panel-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 20px; }
.panel-heading h2 { margin: 0; color: #172b4d; font-size: 19px; font-weight: 800; }
.panel-kicker { margin-bottom: 6px; color: #9fb3c8; font-size: 9px; letter-spacing: 1.6px; }
.panel-count { color: #8993a4; font-size: 12px; }
.healthy { color: #0f9f9a; font-size: 10px; font-weight: 800; letter-spacing: 1px; white-space: nowrap; }.healthy i { display: inline-block; width: 7px; height: 7px; margin-right: 5px; border-radius: 50%; background: #0f9f9a; }
.task-panel :deep(.el-table) { border-radius: 8px; }.task-panel :deep(.el-table th.el-table__cell) { background: #f5f9fa; }
.panel-empty { padding: 34px 0; color: #8993a4; text-align: center; font-size: 13px; }
.system-status { display: grid; grid-template-columns: 1fr 1fr; gap: 5px 18px; }
.status-item { display: flex; align-items: center; min-height: 42px; color: #486581; font-size: 13px; }.status-item .el-tag { margin-left: auto; font-size: 10px; }.service-dot { width: 7px; height: 7px; margin-right: 10px; box-shadow: none; }
.service-footer { display: flex; justify-content: space-between; margin-top: 22px; padding-top: 18px; border-top: 1px solid #edf2f4; color: #8993a4; font-size: 12px; }.service-footer strong { color: #0f9f9a; font-size: 18px; }.health-bar { height: 5px; margin-top: 10px; overflow: hidden; border-radius: 5px; background: #e7f5f2; }.health-bar span { display: block; width: 100%; height: 100%; background: #0f9f9a; }
@media (max-width: 1000px) { .metric-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }.dashboard-grid { grid-template-columns: 1fr; }.hero-orbit { margin-right: 4%; } }
@media (max-width: 600px) { .dashboard-hero { min-height: 210px; padding: 26px 24px; }.dashboard-hero h1 { font-size: 27px; }.hero-orbit { display: none; }.metric-grid { grid-template-columns: 1fr 1fr; gap: 10px; }.metric-card { min-height: 126px; padding: 14px; }.stat-value { font-size: 24px; }.panel { padding: 17px 14px; }.system-status { grid-template-columns: 1fr; }.panel-heading { margin-bottom: 14px; } }
</style>
