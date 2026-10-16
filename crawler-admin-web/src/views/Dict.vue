<template>
  <div class="dict-page">
    <div class="category-panel">
      <div class="panel-header">
        <div class="category-heading">
          <span class="panel-title">字典分类</span>
          <span class="category-summary">{{ filteredCategories.length }} 个分类</span>
        </div>
        <el-button type="primary" size="small" :icon="Plus" @click="showCreateParent">新增</el-button>
      </div>
      <div class="search-box">
        <el-input v-model="keyword" placeholder="搜索分类" clearable :prefix-icon="Search" size="small" />
        <el-button size="small" @click="keyword = ''">重置</el-button>
      </div>
      <div class="table-scroll-wrapper category-table-wrap">
        <el-table :data="pagedCategories" v-loading="loading" stripe border resizable>
          <el-table-column prop="id" label="ID" width="75" resizable />
          <el-table-column prop="label" label="名称" min-width="140" show-overflow-tooltip resizable />
          <el-table-column prop="value" label="值" min-width="140" show-overflow-tooltip resizable>
            <template #default="{ row }">{{ row.value || '-' }}</template>
          </el-table-column>
          <el-table-column prop="sort" label="排序" width="85" align="center" resizable>
            <template #default="{ row }">{{ row.sort ?? 0 }}</template>
          </el-table-column>
          <el-table-column label="子项数" width="90" align="center" resizable>
            <template #default="{ row }">{{ (row.children || []).length }}</template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="90" align="center" resizable>
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">
                {{ row.status === 1 ? '启用' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="创建时间" min-width="170" resizable>
            <template #default="{ row }">{{ formatDateTime(row.createTime) || '-' }}</template>
          </el-table-column>
          <el-table-column prop="updateTime" label="更新时间" min-width="170" resizable>
            <template #default="{ row }">{{ formatDateTime(row.updateTime) || '-' }}</template>
          </el-table-column>
          <el-table-column label="操作" width="70" fixed="right" align="center" resizable>
            <template #default="{ row }">
              <TableRowActions
                :items="getCategoryActions()"
                @command="command => handleCategoryAction(command, row)"
              />
            </template>
          </el-table-column>
          <template #empty>暂无分类</template>
        </el-table>
      </div>
      <el-pagination
        v-model:current-page="categoryPage"
        v-model:page-size="categoryPageSize"
        :total="filteredCategories.length"
        :page-sizes="[10, 15, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
      />
    </div>

    <el-dialog
      v-model="childrenDialogVisible"
      :title="`${managingCategory?.label || '分类'} · 子项管理`"
      width="min(900px, calc(100vw - 32px))"
      destroy-on-close
    >
      <div class="children-dialog-toolbar">
        <span class="children-count">{{ childList.length }} 个子项 · 拖动可调整顺序</span>
        <el-button type="primary" size="small" :icon="Plus" @click="showCreateChild">添加子项</el-button>
      </div>
      <div class="child-table-wrap" v-loading="loading">
        <div v-if="childList.length" class="child-table-header" aria-hidden="true">
          <span></span>
          <span>排序</span>
          <span>名称</span>
          <span>值</span>
          <span>状态</span>
          <span>创建时间</span>
          <span>操作</span>
        </div>
        <draggable v-model="childList" handle=".drag-handle" @end="onDragEnd">
          <template #item="{ element }">
            <div class="child-row">
              <span class="drag-handle">⠿</span>
              <span class="child-sort">{{ element.sort }}</span>
              <span class="child-name">{{ element.label }}</span>
              <span class="child-value">{{ element.value || '-' }}</span>
              <el-tag size="small" :type="element.status === 1 ? 'success' : 'info'">
                {{ element.status === 1 ? '启用' : '禁用' }}
              </el-tag>
              <span class="child-time">{{ formatDateTime(element.createTime) }}</span>
              <div class="child-actions">
                <TableRowActions
                  :items="getChildActions()"
                  @command="command => handleChildAction(command, element)"
                />
              </div>
            </div>
          </template>
        </draggable>
        <div v-if="!childList.length && !loading" class="child-empty">
          <el-empty description="暂无子项" :image-size="60" />
        </div>
      </div>
    </el-dialog>

    <!-- 弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="440px" destroy-on-close>
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称" required>
          <el-input v-model="form.label" placeholder="如：科技、财经" />
        </el-form-item>
        <el-form-item label="值">
          <el-input v-model="form.value" placeholder="可选，默认与名称相同" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="form.sort" :min="0" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" active-text="启用" inactive-text="禁用" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { confirm } from '@/utils/confirm'
import draggable from 'vuedraggable'
import { dictTree, dictCreate, dictUpdate, dictDelete } from '@/api'
import { Plus, Search, Edit, Delete, FolderOpened } from '@element-plus/icons-vue'
import { formatDateTime } from '@/utils/dateTime'
import TableRowActions, { type TableRowAction } from '@/components/TableRowActions.vue'

const treeData = ref<any[]>([])
const loading = ref(false)
const keyword = ref('')
const categoryPage = ref(1)
const categoryPageSize = ref(10)
const selectedId = ref<number | null>(null)
const dialogVisible = ref(false)
const childrenDialogVisible = ref(false)
const editingId = ref<number | null>(null)
const isEdit = ref(false)
const form = ref({ parentId: 0, label: '', value: '', sort: 0, status: 1 })
const childList = ref<any[]>([])
const managingCategory = ref<any>(null)

const selectedCategory = computed(() =>
  treeData.value.find((c: any) => c.id === selectedId.value) || null
)

// 当选中分类变化时，同步子项列表
watch(selectedCategory, (cat) => {
  childList.value = cat ? [...(cat.children || [])] : []
}, { immediate: true })

const onDragEnd = () => {
  childList.value.forEach((c: any, idx: number) => {
    c.sort = idx
  })
  saveChildOrder()
}

const saveChildOrder = async () => {
  try {
    for (const c of childList.value) {
      await dictUpdate(c.id, { sort: c.sort })
    }
    ElMessage.success('排序已保存')
    loadData()
  } catch {
    ElMessage.error('排序保存失败')
  }
}

const filteredCategories = computed(() => {
  if (!keyword.value) return treeData.value
  const kw = keyword.value.toLowerCase()
  return treeData.value.filter(
    (c: any) =>
      c.label?.toLowerCase().includes(kw) ||
    c.value?.toLowerCase().includes(kw) ||
    (c.children || []).some((ch: any) => ch.label?.toLowerCase().includes(kw))
  )
})

const pagedCategories = computed(() => {
  const start = (categoryPage.value - 1) * categoryPageSize.value
  return filteredCategories.value.slice(start, start + categoryPageSize.value)
})

watch([keyword, categoryPageSize], () => {
  categoryPage.value = 1
})

const dialogTitle = computed(() => {
  if (isEdit.value) return '编辑'
  return form.value.parentId === 0 ? '新增分类' : '新增子项'
})

const loadData = async () => {
  loading.value = true
  try {
    const res: any = await dictTree({ includeDisabled: true })
    treeData.value = res.data || []
    const lastPage = Math.max(1, Math.ceil(filteredCategories.value.length / categoryPageSize.value))
    if (categoryPage.value > lastPage) {
      categoryPage.value = lastPage
    }
    if (managingCategory.value) {
      managingCategory.value = treeData.value.find((c: any) => c.id === managingCategory.value.id) || null
    }
    // 默认选中第一个
    if (!selectedId.value && treeData.value.length) {
      selectedId.value = treeData.value[0].id
    }
  } finally {
    loading.value = false
  }
}

const openChildrenDialog = (cat: any) => {
  selectedId.value = cat.id
  managingCategory.value = cat
  childrenDialogVisible.value = true
}

const getCategoryActions = (): TableRowAction[] => [
  { command: 'children', label: '管理子项', icon: FolderOpened },
  { command: 'edit', label: '编辑分类', icon: Edit },
  { command: 'delete', label: '删除分类', icon: Delete, divided: true, danger: true }
]

const handleCategoryAction = (command: string, cat: any) => {
  if (command === 'children') openChildrenDialog(cat)
  if (command === 'edit') showEdit(cat)
  if (command === 'delete') handleDelete(cat)
}

const getChildActions = (): TableRowAction[] => [
  { command: 'edit', label: '编辑', icon: Edit },
  { command: 'delete', label: '删除', icon: Delete, divided: true, danger: true }
]

const handleChildAction = (command: string, row: any) => {
  if (command === 'edit') showEdit(row)
  if (command === 'delete') handleDelete(row)
}

const showCreateParent = () => {
  isEdit.value = false
  editingId.value = null
  form.value = { parentId: 0, label: '', value: '', sort: 0, status: 1 }
  dialogVisible.value = true
}

const showCreateChild = () => {
  if (!managingCategory.value) return
  isEdit.value = false
  editingId.value = null
  form.value = {
    parentId: managingCategory.value.id,
    label: '',
    value: '',
    sort: childList.value.length,
    status: 1
  }
  dialogVisible.value = true
}

const showEdit = (row: any) => {
  isEdit.value = true
  editingId.value = row.id
  form.value = {
    parentId: row.parentId ?? 0,
    label: row.label,
    value: row.value || '',
    sort: row.sort ?? 0,
    status: row.status ?? 1
  }
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!form.value.label) {
    ElMessage.warning('请填写名称')
    return
  }
  try {
    if (isEdit.value) {
      await dictUpdate(editingId.value!, { ...form.value })
      ElMessage.success('更新成功')
    } else {
      await dictCreate({ ...form.value })
      ElMessage.success('创建成功')
      // 新建分类后自动选中
      if (form.value.parentId === 0) {
        await loadData()
        if (treeData.value.length) {
          selectedId.value = treeData.value[treeData.value.length - 1].id
        }
      }
    }
    dialogVisible.value = false
    loadData()
  } catch {
    ElMessage.error('操作失败')
  }
}

const handleDelete = async (row: any) => {
  const isParent = row.parentId === 0
  const hasChildren = isParent && (row.children || []).length > 0
  const msg = hasChildren
    ? `「${row.label}」下存在 ${row.children.length} 个子项，删除分类不会级联删除子项。确定继续？`
    : `确定删除「${row.label}」?`
  try {
    if (!await confirm(msg, { title: '删除确认', danger: true })) return
  } catch {
    return
  }
  try {
    await dictDelete(row.id)
    ElMessage.success('删除成功')
    if (selectedId.value === row.id) {
      selectedId.value = null
    }
    loadData()
  } catch {
    ElMessage.error('删除失败')
  }
}

onMounted(loadData)
</script>

<style scoped>
.dict-page {
  height: calc(100vh - 140px);
  min-height: 400px;
}

.category-panel {
  width: 100%;
  height: 100%;
  background: #fff;
  border-radius: 14px;
  border: 1px solid #dfe9eb;
  box-shadow: 0 8px 24px rgba(23, 43, 77, .06);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.category-heading {
  display: flex;
  align-items: baseline;
  gap: 10px;
  min-width: 0;
}

.category-summary,
.children-count {
  color: var(--ink-500);
  font-size: 12px;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  min-height: 58px;
  padding: 12px 16px;
  border-bottom: 1px solid #e5eef0;
  background: linear-gradient(135deg, #f7fbfb, #f2f8f8);
}

.panel-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--ink-950);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-bottom: 1px solid #edf2f4;
}

.search-box :deep(.el-input) {
  min-width: 0;
}

.category-table-wrap {
  flex: 1;
  min-height: 0;
  padding: 10px 16px 0;
}

.child-table-wrap {
  max-height: min(60vh, 560px);
  overflow: auto;
  padding: 0 2px 8px;
}

.children-dialog-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.child-table-header,
.child-row {
  display: grid;
  grid-template-columns: 20px 38px minmax(150px, 1.4fr) minmax(120px, 1fr) 72px minmax(145px, 1fr) 108px;
  align-items: center;
  gap: 10px;
  min-width: 740px;
  box-sizing: border-box;
}

.child-table-header {
  position: sticky;
  top: 0;
  z-index: 1;
  min-height: 42px;
  padding: 0 12px;
  color: var(--ink-500);
  background: #fff;
  border-bottom: 1px solid #e9eff1;
  font-size: 12px;
  font-weight: 600;
}

.child-table-header > :last-child,
.child-row > :last-child {
  position: sticky;
  right: 0;
  z-index: 1;
}

.child-table-header > :last-child {
  background: #fff;
  text-align: center;
}

.child-table-header > :nth-child(5) {
  text-align: center;
}

.child-row > :nth-child(5) {
  justify-self: center;
}

.child-row > :last-child {
  background: var(--surface-soft);
}

.child-row:hover > :last-child {
  background: #f2f9f7;
}

.child-row {
  min-height: 54px;
  padding: 8px 12px;
  margin-top: 8px;
  background: var(--surface-soft);
  border: 1px solid #edf2f4;
  border-radius: 10px;
  transition: background-color .18s ease, border-color .18s ease, box-shadow .18s ease;
}

.child-row:hover {
  background: #f2f9f7;
  border-color: #bfe5de;
  box-shadow: 0 2px 8px rgba(16, 110, 102, .05);
}

.drag-handle {
  cursor: grab;
  color: #a8bcc4;
  font-size: 16px;
  text-align: center;
  user-select: none;
}

.drag-handle:active {
  cursor: grabbing;
}

.child-sort {
  width: 26px;
  height: 24px;
  line-height: 24px;
  text-align: center;
  background: #e8f0f2;
  border-radius: 4px;
  font-size: 12px;
  color: var(--ink-700);
}

.child-name {
  min-width: 0;
  font-size: 14px;
  font-weight: 500;
  color: var(--ink-900);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.child-value {
  min-width: 0;
  font-size: 13px;
  color: var(--ink-500);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.child-time {
  font-size: 12px;
  color: var(--ink-500);
  white-space: nowrap;
}

.child-actions {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 100%;
  white-space: nowrap;
}

.child-empty {
  padding: 20px 0;
}

@media (max-width: 680px) {
  .dict-page {
    height: auto;
    min-height: 0;
  }

  .children-dialog-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
