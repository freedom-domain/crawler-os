<template>
  <el-card>
    <template #header><span>角色管理</span></template>

    <el-form :inline="true">
      <el-form-item>
        <el-button type="success" @click="openCreate" :icon="Plus">新增角色</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="list" v-loading="loading" stripe resizable>
      <el-table-column prop="id" label="ID" min-width="60"  resizable />
      <el-table-column prop="name" label="角色名称" min-width="140"  resizable />
      <el-table-column prop="code" label="角色编码" min-width="140"  resizable />
      <el-table-column prop="description" label="描述"  resizable />
      <el-table-column prop="status" label="状态" min-width="80" resizable>
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'">
            {{ row.status === 1 ? '启用' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right" resizable>
        <template #default="{ row }">
          <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
          <el-button type="warning" link @click="openAssign(row)">分配权限</el-button>
          <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 角色新增/编辑 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑角色' : '新增角色'" width="480px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="角色名称">
          <el-input v-model="form.name" placeholder="请输入角色名称" />
        </el-form-item>
        <el-form-item label="角色编码">
          <el-input v-model="form.code" placeholder="请输入角色编码" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" placeholder="请输入描述" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveForm">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分配权限 -->
    <el-dialog v-model="assignVisible" title="分配权限" width="480px">
      <div class="tree-toolbar">
        <el-button size="small" @click="checkAll">全选</el-button>
        <el-button size="small" @click="uncheckAll">取消全选</el-button>
      </div>
      <el-tree
        ref="treeRef"
        :data="permTree"
        show-checkbox
        node-key="id"
        default-expand-all
        :props="{ label: 'name', children: 'children' }"
      >
        <template #default="{ node, data }">
          <span class="permission-tree-node">
            <el-icon class="tree-icon"><component :is="getIcon(data)" /></el-icon>
            <span>{{ node.label }}</span>
          </span>
        </template>
      </el-tree>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveAssign">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  roleList, roleCreate, roleUpdate, roleDelete,
  rolePermissions, roleAssignPermissions, permissionTree
} from '@/api'
import { Plus, User, Avatar, Lock, PriceTag, Connection, List, Search, Folder, Setting, Menu as MenuIcon } from '@element-plus/icons-vue'

const list = ref<any[]>([])
const loading = ref(false)
const saving = ref(false)

const formVisible = ref(false)
const form = ref<any>({ id: null, name: '', code: '', description: '', status: 1 })

const assignVisible = ref(false)
const permTree = ref<any[]>([])
const currentRoleId = ref<number | null>(null)
const treeRef = ref<any>()

const iconMap: Record<string, any> = {
  user: User,
  User,
  role: Avatar,
  Avatar,
  permission: Lock,
  Lock,
  dict: PriceTag,
  PriceTag,
  spider: Connection,
  Connection,
  task: List,
  List,
  search: Search,
  Search,
  file: Folder,
  Folder,
  system: Setting,
  Setting
}

const getIcon = (item: any) => iconMap[item.icon] || iconMap[item.code] || MenuIcon

const loadData = async () => {
  loading.value = true
  try {
    const res: any = await roleList()
    list.value = res.data || []
  } finally {
    loading.value = false
  }
}

const openCreate = () => {
  form.value = { id: null, name: '', code: '', description: '', status: 1 }
  formVisible.value = true
}

const openEdit = (row: any) => {
  form.value = { id: row.id, name: row.name, code: row.code, description: row.description, status: row.status }
  formVisible.value = true
}

const saveForm = async () => {
  if (!form.value.name || !form.value.code) {
    ElMessage.warning('角色名称和编码不能为空')
    return
  }
  saving.value = true
  try {
    if (form.value.id) {
      await roleUpdate(form.value.id, form.value)
    } else {
      await roleCreate(form.value)
    }
    ElMessage.success('保存成功')
    formVisible.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

const handleDelete = (row: any) => {
  ElMessageBox.confirm(`确定删除角色「${row.name}」吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await roleDelete(row.id)
      ElMessage.success('删除成功')
      loadData()
    }).catch(() => {})
}

const openAssign = async (row: any) => {
  currentRoleId.value = row.id
  const [treeRes, permRes]: any = await Promise.all([permissionTree(), rolePermissions(row.id)])
  permTree.value = treeRes.data || []
  assignVisible.value = true
  // 等待树渲染后回显已选权限
  setTimeout(() => {
    const checked = permRes.data || []
    checked.forEach((id: number) => treeRef.value?.setChecked(id, true, false))
  }, 100)
}

const getAllIds = (nodes: any[]): number[] => {
  let ids: number[] = []
  for (const node of nodes) {
    ids.push(node.id)
    if (node.children && node.children.length > 0) {
      ids = ids.concat(getAllIds(node.children))
    }
  }
  return ids
}

const checkAll = () => {
  const allIds = getAllIds(permTree.value)
  allIds.forEach(id => treeRef.value?.setChecked(id, true, false))
}

const uncheckAll = () => {
  treeRef.value?.setCheckedKeys([])
}

const saveAssign = async () => {
  if (!currentRoleId.value) return
  saving.value = true
  try {
    const checked = treeRef.value?.getCheckedKeys() || []
    const halfChecked = treeRef.value?.getHalfCheckedKeys() || []
    await roleAssignPermissions({ roleId: currentRoleId.value, permissionIds: [...checked, ...halfChecked] })
    ElMessage.success('分配成功')
    assignVisible.value = false
  } finally {
    saving.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.permission-tree-node { display: inline-flex; align-items: center; gap: 8px; color: #243b53; }
.tree-icon { color: #16a6a3; font-size: 16px; }
.tree-toolbar { margin-bottom: 12px; display: flex; gap: 8px; }
</style>
