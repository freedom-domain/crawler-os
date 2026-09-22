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
      <el-form-item>
        <el-button type="success" @click="openCreate" :icon="Plus">新增用户</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="list" v-loading="loading" stripe resizable border>
      <el-table-column prop="id" label="ID" min-width="60"  resizable />
      <el-table-column prop="username" label="用户名" min-width="140"  resizable />
      <el-table-column prop="nickname" label="昵称" min-width="140"  resizable />
      <el-table-column prop="email" label="邮箱"  resizable />
      <el-table-column prop="phone" label="手机" min-width="140"  resizable />
      <el-table-column prop="roleName" label="角色" min-width="120" resizable>
        <template #default="{ row }">
          <el-tag>{{ row.roleName || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" min-width="80" resizable>
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">
            {{ row.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right" resizable>
        <template #default="{ row }">
          <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
          <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
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

    <el-dialog v-model="editVisible" title="编辑用户" width="480px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" placeholder="请输入昵称" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="手机">
          <el-input v-model="form.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleId" placeholder="请选择角色" style="width:100%">
            <el-option v-for="r in roleList" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="重置密码">
          <el-input v-model="form.password" type="password" placeholder="留空则不修改" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="createVisible" title="新增用户" width="480px">
      <el-form :model="createForm" label-width="80px">
        <el-form-item label="用户名">
          <el-input v-model="createForm.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="createForm.password" type="password" placeholder="请输入密码" show-password />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="createForm.nickname" placeholder="请输入昵称" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="createForm.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="手机">
          <el-input v-model="createForm.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="createForm.roleId" placeholder="请选择角色" style="width:100%">
            <el-option v-for="r in roleList" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="saveCreate">创建</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { userPage, userCreate, userUpdate, userDelete, roles } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus } from '@element-plus/icons-vue'

const list = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(10)
const total = ref(0)
const keyword = ref('')

const editVisible = ref(false)
const saving = ref(false)
const createVisible = ref(false)
const creating = ref(false)
const roleList = ref<any[]>([])
const form = ref<any>({
  id: null,
  username: '',
  nickname: '',
  email: '',
  phone: '',
  roleId: null,
  status: 1,
  password: ''
})
const createForm = ref<any>({
  username: '',
  password: '',
  nickname: '',
  email: '',
  phone: '',
  roleId: null
})

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

const loadRoles = async () => {
  const res: any = await roles()
  roleList.value = res.data || []
}

const openEdit = (row: any) => {
  form.value = {
    id: row.id,
    username: row.username,
    nickname: row.nickname,
    email: row.email,
    phone: row.phone,
    roleId: row.roleId,
    status: row.status,
    password: ''
  }
  editVisible.value = true
}

const saveEdit = async () => {
  saving.value = true
  try {
    const payload: any = {
      username: form.value.username,
      nickname: form.value.nickname,
      email: form.value.email,
      phone: form.value.phone,
      roleId: form.value.roleId,
      status: form.value.status
    }
    if (form.value.password) {
      payload.password = form.value.password
    }
    await userUpdate(form.value.id, payload)
    ElMessage.success('保存成功')
    editVisible.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

const handleDelete = (row: any) => {
  ElMessageBox.confirm(`确定删除用户「${row.username}」吗？`, '提示', {
    type: 'warning'
  }).then(async () => {
    await userDelete(row.id)
    ElMessage.success('删除成功')
    loadData()
  }).catch(() => {})
}

const openCreate = () => {
  createForm.value = {
    username: '',
    password: '',
    nickname: '',
    email: '',
    phone: '',
    roleId: null
  }
  createVisible.value = true
}

const saveCreate = async () => {
  if (!createForm.value.username || !createForm.value.password) {
    ElMessage.warning('用户名和密码不能为空')
    return
  }
  creating.value = true
  try {
    await userCreate({
      username: createForm.value.username,
      password: createForm.value.password,
      nickname: createForm.value.nickname,
      email: createForm.value.email,
      phone: createForm.value.phone,
      roleId: createForm.value.roleId
    })
    ElMessage.success('创建成功')
    createVisible.value = false
    loadData()
  } finally {
    creating.value = false
  }
}

onMounted(() => {
  loadData()
  loadRoles()
})
</script>
