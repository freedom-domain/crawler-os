<template>
  <el-card>
    <template #header><span>数据搜索</span></template>

    <el-form :inline="true" @submit.prevent="loadData">
      <el-form-item>
        <el-input v-model="keyword" placeholder="搜索关键词" clearable style="width:300px" @keyup.enter="loadData" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadData" :icon="Search">搜索</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="title" label="标题" width="240" show-overflow-tooltip />
      <el-table-column label="内容摘要" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.content?.substring(0, 100) }}
        </template>
      </el-table-column>
      <el-table-column prop="url" label="来源URL" width="240" show-overflow-tooltip />
      <el-table-column prop="spiderName" label="爬虫" width="120" />
      <el-table-column label="抓取时间" width="180">
        <template #default="{ row }">
          {{ formatTime(row.crawlTime) }}
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      style="margin-top:16px; justify-content:flex-end"
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      layout="total, prev, pager, next"
      @change="loadData"
    />
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { searchContent } from '@/api'
import { Search } from '@element-plus/icons-vue'

const list = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const keyword = ref('')

const formatTime = (ts: number) => {
  if (!ts) return ''
  const d = new Date(ts)
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

const loadData = async () => {
  loading.value = true
  try {
    const res: any = await searchContent({ current: page.value, size: size.value, keyword: keyword.value })
    list.value = res.data?.content || []
    total.value = res.data?.totalElements || 0
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>
