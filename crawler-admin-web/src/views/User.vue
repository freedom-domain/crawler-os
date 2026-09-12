<template>
  <el-card>
    <template #header><span>用户管理</span></template>

    <el-form :inline="true">
      <el-form-item>
        <el-input v-model="keyword" placeholder="搜索用户名" clearable @keyup.enter="loadData" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="loadData" :icon="Search">查询</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="username" label="用户名" width="140" />
      <el-table-column prop="nickname" label="昵称" width="140" />
      <el-table-column prop="email" label="邮箱" />
      <el-table-column prop="phone" label="手机" width="140" />
      <el-table-column prop="roleName" label="角色" width="120">
        <template #default="{ row }">
          <el-tag>{{ row.roleName || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">
            {{ row.status === 1 ? '启用' : '禁用' }}
          </el-tag>
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
import { userPage } from '@/api'
import { Search } from '@element-plus/icons-vue'

const list = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)
const keyword = ref('')

const loadData = async () => {
  loading.value = true
  try {
    const res: any = await userPage({ current: page.value, size: size.value, keyword: keyword.value })
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>
