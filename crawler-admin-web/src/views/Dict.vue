<template>
  <div class="dict-page">
    <!-- 左侧：分类列表 -->
    <div class="panel-left">
      <div class="panel-header">
        <span class="panel-title">分类</span>
        <el-button type="primary" size="small" :icon="Plus" @click="showCreateParent">新增</el-button>
      </div>
      <div class="search-box">
        <el-input v-model="keyword" placeholder="搜索分类" clearable :prefix-icon="Search" size="small" />
        <el-button size="small" @click="keyword = ''">重置</el-button>
      </div>
      <div class="category-list" v-loading="loading">
        <div
          v-for="cat in filteredCategories"
          :key="cat.id"
          class="category-item"
          :class="{ active: selectedId === cat.id }"
          @click="selectCategory(cat)"
        >
          <div class="cat-info">
            <span class="cat-name">{{ cat.label }}</span>
            <span class="cat-count">{{ (cat.children || []).length }}</span>
          </div>
          <div class="cat-actions" @click.stop>
            <el-button size="small" text :icon="Edit" @click="showEdit(cat)" />
            <el-button size="small" text type="danger" :icon="Delete" @click="handleDelete(cat)" />
          </div>
        </div>
        <div v-if="!filteredCategories.length && !loading" class="empty-tip">暂无分类</div>
      </div>
    </div>

    <!-- 右侧：子项管理 -->
    <div class="panel-right">
      <template v-if="selectedCategory">
        <div class="panel-header">
          <div class="right-header-left">
            <span class="panel-title">{{ selectedCategory.label }}</span>
            <el-tag size="small" type="info">{{ (selectedCategory.children || []).length }} 项</el-tag>
          </div>
          <el-button type="primary" size="small" :icon="Plus" @click="showCreateChild">添加子项</el-button>
        </div>

        <div class="child-table-wrap" v-loading="loading">
          <draggable
            v-model="childList"
            handle=".drag-handle"
            @end="onDragEnd"
          >
            <template #item="{ element }">
              <div class="child-row">
                <span class="drag-handle">⠿</span>
                <span class="child-sort">{{ element.sort }}</span>
                <span class="child-name">{{ element.label }}</span>
                <span class="child-value">{{ element.value || '-' }}</span>
                <el-switch
                  v-model="element.status"
                  :active-value="1"
                  :inactive-value="0"
                  size="small"
                  @change="handleStatusChange(element)"
                />
                <span class="child-time">{{ element.createTime }}</span>
                <div class="child-actions">
                  <el-button size="small" text @click="showEdit(element)">编辑</el-button>
                  <el-button size="small" text type="danger" @click="handleDelete(element)">删除</el-button>
                </div>
              </div>
            </template>
          </draggable>
          <div v-if="!childList.length && !loading" class="child-empty">
            <el-empty description="暂无子项" :image-size="60" />
          </div>
        </div>
      </template>
      <template v-else>
        <div class="placeholder">
          <el-empty description="请选择左侧分类" :image-size="100" />
        </div>
      </template>
    </div>

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
import { ElMessage, ElMessageBox } from 'element-plus'
import draggable from 'vuedraggable'
import { dictTree, dictCreate, dictUpdate, dictDelete } from '@/api'
import { Plus, Search, Edit, Delete } from '@element-plus/icons-vue'

const treeData = ref<any[]>([])
const loading = ref(false)
const keyword = ref('')
const selectedId = ref<number | null>(null)
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const isEdit = ref(false)
const form = ref({ parentId: 0, label: '', value: '', sort: 0, status: 1 })
const childList = ref<any[]>([])

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
      (c.children || []).some((ch: any) => ch.label?.toLowerCase().includes(kw))
  )
})

const dialogTitle = computed(() => {
  if (isEdit.value) return '编辑'
  return form.value.parentId === 0 ? '新增分类' : '新增子项'
})

const loadData = async () => {
  loading.value = true
  try {
    const res: any = await dictTree()
    treeData.value = res.data || []
    // 默认选中第一个
    if (!selectedId.value && treeData.value.length) {
      selectedId.value = treeData.value[0].id
    }
  } finally {
    loading.value = false
  }
}

const selectCategory = (cat: any) => {
  selectedId.value = cat.id
}

const showCreateParent = () => {
  isEdit.value = false
  editingId.value = null
  form.value = { parentId: 0, label: '', value: '', sort: 0, status: 1 }
  dialogVisible.value = true
}

const showCreateChild = () => {
  if (!selectedCategory.value) return
  isEdit.value = false
  editingId.value = null
  form.value = {
    parentId: selectedCategory.value.id,
    label: '',
    value: '',
    sort: (selectedCategory.value.children || []).length,
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

const handleStatusChange = async (row: any) => {
  try {
    await dictUpdate(row.id, { status: row.status })
    ElMessage.success(row.status === 1 ? '已启用' : '已禁用')
  } catch {
    row.status = row.status === 1 ? 0 : 1
    ElMessage.error('状态更新失败')
  }
}

const handleDelete = async (row: any) => {
  const isParent = row.parentId === 0
  const hasChildren = isParent && (row.children || []).length > 0
  const msg = hasChildren
    ? `「${row.label}」下存在 ${row.children.length} 个子项，删除分类不会级联删除子项。确定继续？`
    : `确定删除「${row.label}」?`
  try {
    await ElMessageBox.confirm(msg, '警告', { type: 'warning' })
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
  display: flex;
  gap: 16px;
  height: calc(100vh - 140px);
  min-height: 400px;
}

/* 左侧面板 */
.panel-left {
  width: 280px;
  min-width: 240px;
  background: #fff;
  border-radius: 12px;
  border: 1px solid #dfe9eb;
  box-shadow: 0 6px 20px rgba(23, 43, 77, .05);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px;
  border-bottom: 1px solid #e5eef0;
  background: #f8fbfb;
}

.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.search-box {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-bottom: 1px solid #f0f0f0;
}

.search-box :deep(.el-input) {
  min-width: 0;
}

.category-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.category-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  margin-bottom: 4px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
}

.category-item:hover {
  background: #f5f7fa;
}

.category-item.active {
  background: #e2f6f3;
  border: 1px solid #a9e0d7;
  color: #087f7d;
}

.cat-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.cat-name {
  font-size: 14px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.category-item.active .cat-name {
  color: #409eff;
  font-weight: 500;
}

.cat-count {
  min-width: 20px;
  height: 20px;
  line-height: 20px;
  text-align: center;
  background: #e4e7ed;
  border-radius: 10px;
  font-size: 11px;
  color: #606266;
  padding: 0 6px;
}

.category-item.active .cat-count {
  background: #d9ecff;
  color: #409eff;
}

.cat-actions {
  display: flex;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.2s;
}

.category-item:hover .cat-actions {
  opacity: 1;
}

.empty-tip {
  text-align: center;
  color: #999;
  font-size: 13px;
  padding: 20px 0;
}

/* 右侧面板 */
.panel-right {
  flex: 1;
  background: #fff;
  border-radius: 8px;
  border: 1px solid #ebeef5;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.right-header-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.child-table-wrap {
  flex: 1;
  overflow-y: auto;
  padding: 0 16px 16px;
}

.child-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  margin-bottom: 4px;
  background: #fafafa;
  border: 1px solid #f0f0f0;
  border-radius: 6px;
  transition: all 0.2s;
}

.child-row:hover {
  background: #f5f7fa;
  border-color: #dcdfe6;
}

.drag-handle {
  cursor: grab;
  color: #c0c4cc;
  font-size: 14px;
  user-select: none;
}

.drag-handle:active {
  cursor: grabbing;
}

.child-sort {
  min-width: 22px;
  height: 22px;
  line-height: 22px;
  text-align: center;
  background: #e4e7ed;
  border-radius: 4px;
  font-size: 12px;
  color: #606266;
}

.child-name {
  flex: 1;
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.child-value {
  font-size: 13px;
  color: #909399;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.child-time {
  font-size: 12px;
  color: #b0b3b8;
  min-width: 140px;
}

.child-actions {
  display: flex;
  gap: 4px;
}

.child-empty {
  padding: 20px 0;
}

.placeholder {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
