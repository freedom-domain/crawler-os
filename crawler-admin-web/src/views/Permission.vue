<template>
  <el-card>
    <el-form :inline="true">
      <el-form-item>
        <el-button type="success" @click="openCreate(null)" :icon="Plus">新增权限</el-button>
      </el-form-item>
    </el-form>

    <div class="table-scroll-wrapper">
    <el-table :data="tree" v-loading="loading" row-key="id" default-expand-all :tree-props="{ children: 'children' }" stripe :row-class-name="permissionRowClassName" resizable border>
      <el-table-column prop="name" label="权限名称" min-width="200"  resizable />
      <el-table-column label="图标" min-width="90" align="center" resizable>
        <template #default="{ row }">
          <el-icon v-if="row.icon" class="permission-icon"><component :is="iconLibrary[row.icon]" /></el-icon>
          <span v-else class="icon-empty">自动</span>
        </template>
      </el-table-column>
      <el-table-column prop="code" label="权限编码" min-width="200"  resizable />
      <el-table-column prop="type" label="类型" min-width="100" resizable>
        <template #default="{ row }">
          <el-tag :type="typeTag(row.type)">
            {{ typeLabel(row.type) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="path" label="路由路径" min-width="160"  resizable />
      <el-table-column prop="sort" label="排序" min-width="80"  resizable />
      <el-table-column label="操作" width="70" fixed="right" align="center" resizable>
        <template #default="{ row }">
          <TableRowActions :items="getRowActions(row)" @command="command => handleRowAction(command, row)" />
        </template>
      </el-table-column>
    </el-table>
    </div>

    <el-dialog v-model="formVisible" :title="form.id ? '编辑权限' : '新增权限'" width="480px">
      <el-form :model="form" label-width="80px">
        <el-form-item label="上级节点">
          <el-tree-select
            v-model="form.parentId"
            :data="parentTreeOptions"
            :props="{ value: 'id', label: 'name', children: 'children' }"
            check-strictly
            :render-after-expand="false"
            placeholder="请选择上级节点（不选则为顶级）"
            clearable
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="权限名称">
          <el-input v-model="form.name" placeholder="请输入权限名称" />
        </el-form-item>
        <el-form-item label="权限编码">
          <el-input v-model="form.code" placeholder="如 user:list" />
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="form.type">
            <el-radio :value="0">目录</el-radio>
            <el-radio :value="1">菜单</el-radio>
            <el-radio :value="2">按钮</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="路由路径" v-if="form.type === 1">
          <el-input v-model="form.path" placeholder="如 /user（仅菜单类型需要）" />
        </el-form-item>
        <el-form-item label="菜单图标" v-if="form.type !== 2">
          <div class="icon-picker-field">
            <button type="button" class="icon-picker-trigger" @click="openIconPicker">
              <el-icon v-if="selectedIcon"><component :is="selectedIcon.icon" /></el-icon>
              <span>{{ selectedIcon?.label || '选择菜单图标' }}</span>
              <el-icon class="picker-caret"><ArrowDown /></el-icon>
            </button>
          </div>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveForm">保存</el-button>
      </template>
    </el-dialog>
    <el-dialog
      v-model="iconDialogVisible"
      title="选择菜单图标"
      width="min(720px, calc(100vw - 32px))"
      top="4vh"
      append-to-body
      :close-on-click-modal="false"
      class="menu-icon-picker-dialog"
      @closed="resetIconPicker"
    >
      <div class="icon-picker-panel">
        <div class="icon-picker-heading">
          <div class="icon-picker-current">
            <el-icon v-if="selectedIcon" class="icon-picker-current-preview">
              <component :is="selectedIcon.icon" />
            </el-icon>
            <strong>{{ selectedIcon ? `当前选择：${selectedIcon.label}` : '请选择一个图标' }}</strong>
          </div>
          <span>{{ iconOptions.length }} 个图标</span>
        </div>
        <el-input
          v-model="iconSearch"
          class="icon-picker-search"
          placeholder="搜索图标名称"
          clearable
          @input="activeIconCategory = '全部'"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <div class="icon-category-list">
          <button
            v-for="category in iconCategories"
            :key="category"
            type="button"
            class="icon-category-button"
            :class="{ 'is-active': activeIconCategory === category }"
            @click="activeIconCategory = category"
          >{{ category }}</button>
        </div>
        <div v-if="visibleIconGroups.length" class="icon-picker-results">
          <section v-for="group in visibleIconGroups" :key="group.name" class="icon-category-group">
            <h4>{{ group.name }}</h4>
            <div class="icon-picker-grid">
              <button
                v-for="item in group.items"
                :key="item.value"
                type="button"
                class="icon-picker-option"
                :class="{ 'is-selected': form.icon === item.value }"
                :title="`${item.label} (${item.value})`"
                :aria-label="`选择${item.label}图标`"
                @click="selectMenuIcon(item.value)"
              >
                <el-icon><component :is="item.icon" /></el-icon>
                <span>{{ item.label }}</span>
              </button>
            </div>
          </section>
        </div>
        <el-empty v-else description="没有找到匹配的图标" :image-size="56" />
        <div class="icon-picker-footer">
          <span>{{ iconSearch ? `搜索结果 ${visibleIconCount} 个` : `当前分类 ${visibleIconCount} 个` }}</span>
          <el-button v-if="form.icon" link type="danger" @click="clearMenuIcon">清除当前图标</el-button>
        </div>
      </div>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { confirm } from '@/utils/confirm'
import { permissionTree, permissionCreate, permissionUpdate, permissionDelete } from '@/api'
import { menuIconCategories, menuIconLibrary, menuIconOptions } from '@/utils/menuIcons'
import { Plus, ArrowDown, ArrowRight, Edit, Delete, ArrowUp, Search } from '@element-plus/icons-vue'
import TableRowActions from '@/components/TableRowActions.vue'

const tree = ref<any[]>([])
const loading = ref(false)
const saving = ref(false)
const formVisible = ref(false)
const form = ref<any>({ id: null, name: '', code: '', type: 1, path: '', icon: '', sort: 0, parentId: 0 })
const iconDialogVisible = ref(false)
const iconSearch = ref('')
const activeIconCategory = ref('全部')

const iconOptions = menuIconOptions
const iconLibrary = menuIconLibrary
const iconCategories = ['全部', ...menuIconCategories]
const selectedIcon = computed(() => iconOptions.find(item => item.value === form.value.icon))
const visibleIconGroups = computed(() => {
  const search = iconSearch.value.trim().toLocaleLowerCase()
  return iconCategories
    .filter(category => category !== '全部')
    .filter(category => activeIconCategory.value === '全部' || category === activeIconCategory.value)
    .map(name => ({
      name,
      items: iconOptions.filter(item => item.category === name
        && (!search || item.label.toLocaleLowerCase().includes(search)
          || item.value.toLocaleLowerCase().includes(search)))
    }))
    .filter(group => group.items.length > 0)
})
const visibleIconCount = computed(() =>
  visibleIconGroups.value.reduce((count, group) => count + group.items.length, 0)
)
const openIconPicker = () => {
  iconDialogVisible.value = true
}
const selectMenuIcon = (value: string) => {
  form.value.icon = value
  iconDialogVisible.value = false
}
const clearMenuIcon = () => {
  form.value.icon = ''
  iconDialogVisible.value = false
}
const resetIconPicker = () => {
  iconSearch.value = ''
  activeIconCategory.value = '全部'
}
const getRowActions = (row: any) => [
  { command: 'create-child', label: '新增子项', icon: Plus },
  { command: 'edit', label: '编辑', icon: Edit },
  { command: 'delete', label: '删除', icon: Delete, danger: true },
  { command: 'move-up', label: '上移', icon: ArrowUp, disabled: isFirst(row), divided: true },
  { command: 'move-down', label: '下移', icon: ArrowDown, disabled: isLast(row) }
]

// 类型展示
const typeLabel = (type: number) => (type === 0 ? '目录' : type === 1 ? '菜单' : '按钮')
const typeTag = (type: number) => (type === 0 ? 'warning' : type === 1 ? 'primary' : 'info')
const permissionRowClassName = ({ row }: { row: any }) =>
  row.children?.length ? 'permission-parent-row' : 'permission-child-row'

const setLevel = (nodes: any[], level = 0) => {
  for (const node of nodes) {
    node.level = level
    if (node.children && node.children.length > 0) {
      setLevel(node.children, level + 1)
    }
  }
}

const loadData = async () => {
  loading.value = true
  try {
    const res: any = await permissionTree()
    tree.value = res.data || []
    setLevel(tree.value)
  } finally {
    loading.value = false
  }
}

const invalidateMenuCache = () => {
  for (let index = sessionStorage.length - 1; index >= 0; index--) {
    const key = sessionStorage.key(index)
    if (key?.startsWith('crawler-menu:')) {
      sessionStorage.removeItem(key)
    }
  }
  window.dispatchEvent(new Event('crawler-menu-cache-invalidated'))
}

const openCreate = (parent: any) => {
  form.value = {
    id: null,
    name: '',
    code: '',
    // 顶级默认目录，子级默认菜单
    type: parent ? 1 : 0,
    path: '',
    icon: '',
    sort: 0,
    parentId: parent ? parent.id : 0
  }
  formVisible.value = true
}

const openEdit = (row: any) => {
  form.value = {
    id: row.id,
    name: row.name,
    code: row.code,
    type: row.type,
    path: row.path,
    icon: row.icon || '',
    sort: row.sort,
    parentId: row.parentId
  }
  formVisible.value = true
}

// 构建父级节点选项树（编辑时排除自身及其子节点，防止循环引用）
const parentTreeOptions = computed(() => {
  const excludeId = form.value.id
  const containsNode = (node: any, targetId: number): boolean => {
    if (node.id === targetId) return true
    if (node.children) {
      return node.children.some((c: any) => containsNode(c, targetId))
    }
    return false
  }
  const findNode = (nodes: any[], targetId: number): any | null => {
    for (const node of nodes) {
      if (node.id === targetId) return node
      if (node.children) {
        const result = findNode(node.children, targetId)
        if (result) return result
      }
    }
    return null
  }
  const editingNode = excludeId ? findNode(tree.value, excludeId) : null
  const build = (nodes: any[]): any[] => {
    return nodes
      .filter(n => !editingNode || !containsNode(editingNode, n.id))
      .map(n => ({
        id: n.id,
        name: n.name,
        children: n.children ? build(n.children) : undefined
      }))
  }
  return build(tree.value)
})

const saveForm = async () => {
  if (!form.value.name || !form.value.code) {
    ElMessage.warning('权限名称和编码不能为空')
    return
  }
  saving.value = true
  try {
    const payload = { ...form.value }
    delete payload.id
    if (form.value.id) {
      await permissionUpdate(form.value.id, payload)
    } else {
      await permissionCreate(payload)
    }
    invalidateMenuCache()
    ElMessage.success('保存成功')
    formVisible.value = false
    loadData()
  } finally {
    saving.value = false
  }
}

const handleDelete = async (row: any) => {
  if (!await confirm(`确定删除权限「${row.name}」吗？`, { title: '删除确认', danger: true })) return
  try {
    await permissionDelete(row.id)
    invalidateMenuCache()
    ElMessage.success('删除成功')
    loadData()
  } catch {
    ElMessage.error('删除失败')
  }
}

const moveUp = async (row: any) => {
  const siblings = getSiblings(row)
  const idx = siblings.findIndex(s => s.id === row.id)
  if (idx <= 0) return
  // 交换位置
  const temp = siblings[idx - 1]
  siblings[idx - 1] = siblings[idx]
  siblings[idx] = temp
  // 更新排序
  for (let i = 0; i < siblings.length; i++) {
    if (siblings[i].sort !== i) {
      await permissionUpdate(siblings[i].id, { ...siblings[i], sort: i })
    }
  }
  invalidateMenuCache()
  ElMessage.success('已上移')
  loadData()
}

const moveDown = async (row: any) => {
  const siblings = getSiblings(row)
  const idx = siblings.findIndex(s => s.id === row.id)
  if (idx < 0 || idx >= siblings.length - 1) return
  // 交换位置
  const temp = siblings[idx + 1]
  siblings[idx + 1] = siblings[idx]
  siblings[idx] = temp
  // 更新排序
  for (let i = 0; i < siblings.length; i++) {
    if (siblings[i].sort !== i) {
      await permissionUpdate(siblings[i].id, { ...siblings[i], sort: i })
    }
  }
  invalidateMenuCache()
  ElMessage.success('已下移')
  loadData()
}

const handleRowAction = (command: string, row: any) => {
  if (command === 'create-child') openCreate(row)
  if (command === 'edit') openEdit(row)
  if (command === 'delete') handleDelete(row)
  if (command === 'move-up') moveUp(row)
  if (command === 'move-down') moveDown(row)
}

const getSiblings = (row: any): any[] => {
  const parentId = row.parentId
  if (!parentId || parentId === 0) {
    return tree.value
  }
  const findNode = (nodes: any[], targetId: number): any | null => {
    for (const node of nodes) {
      if (node.id === targetId) return node
      if (node.children && node.children.length > 0) {
        const result = findNode(node.children, targetId)
        if (result) return result
      }
    }
    return null
  }
  const parent = findNode(tree.value, parentId)
  return parent?.children || []
}

const isFirst = (row: any): boolean => {
  const siblings = getSiblings(row)
  return siblings.findIndex(s => s.id === row.id) <= 0
}

const isLast = (row: any): boolean => {
  const siblings = getSiblings(row)
  return siblings.findIndex(s => s.id === row.id) >= siblings.length - 1
}

onMounted(loadData)
</script>

<style scoped>
.permission-icon { color: var(--teal-bright); font-size: 18px; vertical-align: middle; }
.icon-empty { color: var(--icon-empty-ink); font-size: 12px; }
.icon-picker-field { width: 100%; }
.icon-picker-trigger {
  display: flex;
  width: 100%;
  height: 32px;
  align-items: center;
  gap: 8px;
  padding: 0 11px;
  border: 1px solid var(--chrome-line);
  border-radius: 4px;
  background: var(--surface);
  color: var(--ink-700);
  text-align: left;
  cursor: pointer;
}
.icon-picker-trigger:hover { border-color: var(--teal-bright); }
.icon-picker-trigger > span { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.icon-picker-trigger .picker-caret { flex: 0 0 auto; margin-left: auto; color: var(--ink-500); }
.icon-picker-panel {
  display: flex;
  height: min(600px, calc(100vh - 220px));
  height: min(600px, calc(100dvh - 220px));
  flex-direction: column;
  gap: 12px;
  overflow: hidden;
}
.icon-picker-heading { display: flex; align-items: center; justify-content: space-between; }
.icon-picker-current { display: flex; min-width: 0; align-items: center; gap: 8px; }
.icon-picker-current-preview { color: var(--teal-bright); font-size: 19px; }
.icon-picker-heading strong { color: var(--ink-900); font-size: 14px; }
.icon-picker-heading span, .icon-picker-footer > span { color: var(--ink-500); font-size: 12px; }
.icon-category-list { display: flex; flex-wrap: wrap; gap: 6px; padding-bottom: 10px; border-bottom: 1px solid var(--chrome-line); }
.icon-category-button {
  padding: 5px 10px;
  border: 1px solid var(--chrome-line);
  border-radius: 14px;
  background: var(--surface);
  color: var(--ink-700);
  cursor: pointer;
  font: inherit;
  font-size: 12px;
  transition: color .15s ease, border-color .15s ease, background .15s ease;
}
.icon-category-button:hover, .icon-category-button.is-active {
  border-color: var(--teal-bright);
  background: var(--chrome-hover);
  color: var(--teal-bright);
}
.icon-picker-results { min-height: 0; flex: 1 1 auto; overflow-y: auto; padding-right: 4px; }
.icon-category-group + .icon-category-group { margin-top: 14px; }
.icon-category-group h4 { margin: 0 0 8px; color: var(--ink-700); font-size: 12px; font-weight: 600; }
.icon-picker-grid { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 6px; }
.icon-picker-option {
  display: flex;
  min-width: 0;
  height: 62px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 5px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: transparent;
  color: var(--ink-700);
  cursor: pointer;
  transition: color .15s ease, border-color .15s ease, background .15s ease;
}
.icon-picker-option .el-icon { font-size: 20px; }
.icon-picker-option span { max-width: 100%; overflow: hidden; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.icon-picker-option:hover, .icon-picker-option.is-selected {
  border-color: var(--teal-bright);
  background: var(--chrome-hover);
  color: var(--teal-bright);
}
.icon-picker-footer { display: flex; align-items: center; justify-content: space-between; border-top: 1px solid var(--chrome-line); padding-top: 8px; }
@media (max-height: 680px) {
  .icon-picker-panel { gap: 8px; }
  .icon-picker-results { min-height: 80px; }
  .icon-picker-option { height: 54px; }
}
:deep(.permission-parent-row td:first-child .cell) {
  border-left: 3px solid var(--teal-bright);
  color: var(--permission-parent-ink);
  font-weight: 700;
}
:deep(.permission-child-row td:first-child .cell) {
  color: var(--permission-child-ink);
}
</style>
