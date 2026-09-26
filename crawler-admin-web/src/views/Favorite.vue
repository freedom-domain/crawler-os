<template>
  <el-card>
    <el-form :inline="true" @submit.prevent>
      <el-form-item>
        <el-input v-model="title" clearable placeholder="标题" style="width: 200px" @keyup.enter="refresh" @clear="refresh" />
      </el-form-item>
      <el-form-item>
        <el-input v-model="url" clearable placeholder="来源 URL" style="width: 200px" @keyup.enter="refresh" @clear="refresh" />
      </el-form-item>
      <el-form-item>
        <el-select v-model="spiderName" clearable filterable placeholder="爬虫" style="width: 160px" @change="refresh">
          <el-option v-for="spider in spiderOptions" :key="spider.id" :label="spider.name" :value="spider.name" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-select v-model="tag" clearable filterable placeholder="标签" style="width: 160px" @change="refresh" @clear="clearTagFilter">
          <el-option v-for="option in tagOptions" :key="option.id" :label="option.label" :value="option.label" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Refresh" :loading="loading" @click="refresh">刷新</el-button>
        <el-button :disabled="loading" @click="resetFilters">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="list" v-loading="loading" stripe resizable border>
      <el-table-column prop="title" label="标题" min-width="420" show-overflow-tooltip resizable>
        <template #default="{ row }">{{ row.title || '无标题' }}</template>
      </el-table-column>
      <el-table-column label="来源 URL" min-width="300" show-overflow-tooltip resizable>
        <template #default="{ row }">
          <a v-if="row.url" class="source-url" :href="row.url" target="_blank" rel="noopener noreferrer">{{ row.url }}</a>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column prop="spiderName" label="爬虫" min-width="160" show-overflow-tooltip resizable>
        <template #default="{ row }">{{ row.spiderName || '-' }}</template>
      </el-table-column>
      <el-table-column label="标签" min-width="180" resizable>
        <template #default="{ row }">
          <el-tag v-for="tag in row.tags || []" :key="tag" size="small" class="tag" @click="openTagEditor(row)">{{ tag }}</el-tag>
          <span v-if="!row.tags || !row.tags.length" class="tag-empty" @click="openTagEditor(row)">添加标签</span>
        </template>
      </el-table-column>
      <el-table-column prop="crawlTime" label="抓取时间" min-width="180"  resizable />
      <el-table-column label="操作" width="180" fixed="right" resizable>
        <template #default="{ row }">
          <el-button link type="primary" @click="openTagEditor(row)">编辑标签</el-button>
          <el-button type="danger" link @click="remove(row)">取消收藏</el-button>
        </template>
      </el-table-column>
    </el-table>

    <TagEditorDialog v-model="tagVisible" :row="tagCurrentRow" :tag-options="tagOptions" @saved="handleTagsSaved" />

    <el-empty v-if="!loading && !list.length" description="暂无收藏" />
    <el-pagination
      v-if="total > 0"
      class="pagination"
      v-model:current-page="page"
      v-model:page-size="size"
      :total="total"
      :hide-on-single-page="false"
      :page-sizes="[10, 15, 20, 50]"
      layout="total, sizes, prev, pager, next, jumper"
      @change="loadData"
    />
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { dictChildren, favoriteDelete, favoritePage, spiderPage } from '@/api'
import TagEditorDialog from '@/components/TagEditorDialog.vue'

const list = ref<any[]>([])
const loading = ref(false)
const page = ref(1)
const size = ref(15)
const total = ref(0)
const title = ref('')
const url = ref('')
const spiderName = ref('')
const tag = ref('')
const spiderOptions = ref<any[]>([])
const tagVisible = ref(false)
const tagCurrentRow = ref<any>(null)
const tagOptions = ref<any[]>([])

const normalizedFilter = (value: unknown) => typeof value === 'string' ? value.trim() || undefined : undefined

const loadData = async () => {
  loading.value = true
  try {
    const res: any = await favoritePage({
      current: page.value,
      size: size.value,
      title: normalizedFilter(title.value),
      url: normalizedFilter(url.value),
      spiderName: normalizedFilter(spiderName.value),
      tag: normalizedFilter(tag.value)
    })
    const data = res?.data
    const content = data?.content || data?.records || []
    list.value = content
    total.value = Number(data?.totalElements ?? data?.total ?? data?.page?.totalElements ?? content.length) || 0
  } finally {
    loading.value = false
  }
}

const refresh = () => {
  page.value = 1
  loadData()
}

const resetFilters = () => {
  title.value = ''
  url.value = ''
  spiderName.value = ''
  tag.value = ''
  refresh()
}

const clearTagFilter = () => {
  tag.value = ''
  refresh()
}

const loadTagOptions = async () => {
  try {
    const res: any = await dictChildren('tag')
    tagOptions.value = res.data || []
  } catch {
    tagOptions.value = []
  }
}

const loadSpiderOptions = async () => {
  try {
    const res: any = await spiderPage({ current: 1, size: 1000 })
    spiderOptions.value = res.data?.records || []
  } catch {
    spiderOptions.value = []
  }
}

const openTagEditor = (row: any) => {
  tagCurrentRow.value = row
  tagVisible.value = true
}

const handleTagsSaved = (tags: string[]) => {
  if (tagCurrentRow.value) tagCurrentRow.value.tags = tags
}

const remove = async (row: any) => {
  try {
    await ElMessageBox.confirm('确定取消收藏该内容吗？', '提示', { type: 'warning' })
    await favoriteDelete(row.id)
    ElMessage.success('已取消收藏')
    await loadData()
  } catch (error: any) {
    if (error !== 'cancel') console.error(error)
  }
}

onMounted(() => {
  loadTagOptions()
  loadSpiderOptions()
  loadData()
})
</script>

<style scoped>
.pagination { margin-top: 18px; display: flex; justify-content: center; }
.source-url { color: #2563eb; text-decoration: none; }
.source-url:hover { text-decoration: underline; }
.tag { margin-right: 6px; margin-bottom: 4px; cursor: pointer; }
.tag-empty { color: #0f9f9a; cursor: pointer; }
</style>
