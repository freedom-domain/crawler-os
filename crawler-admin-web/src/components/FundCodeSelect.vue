<template>
  <el-select
    :model-value="modelValue"
    filterable
    remote
    reserve-keyword
    clearable
    :remote-method="searchFunds"
    :loading="loading"
    :placeholder="placeholder"
    style="width: 230px"
    @update:model-value="emit('update:modelValue', $event)"
    @visible-change="handleVisibleChange"
  >
    <el-option
      v-for="fund in options"
      :key="`${fund.fundType}:${fund.code}`"
      :label="`${fund.code} - ${fund.name}`"
      :value="fund.code"
    >
      <span>{{ fund.code }} - {{ fund.name }}</span>
      <small v-if="fund.category" class="fund-category">{{ fund.category }}</small>
    </el-option>
    <template #empty>
      <span class="empty-message">{{ emptyMessage }}</span>
    </template>
  </el-select>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { fundCatalogPage } from '@/api'

type FundType = 'ONSHORE' | 'OFFSHORE'

interface FundOption {
  fundType: FundType
  code: string
  name: string
  category: string
}

const props = withDefaults(defineProps<{
  modelValue: string
  fundType: FundType
  placeholder?: string
}>(), {
  placeholder: '搜索基金代码或名称'
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const options = ref<FundOption[]>([])
const loading = ref(false)
const searched = ref(false)
let requestSequence = 0

const emptyMessage = computed(() => searched.value
  ? '没有匹配的基金，请先同步基金目录'
  : '输入基金代码或名称搜索')

const searchFunds = async (keyword: string) => {
  const sequence = ++requestSequence
  loading.value = true
  try {
    const response: any = await fundCatalogPage({
      keyword: keyword.trim() || undefined,
      fundType: props.fundType,
      page: 1,
      pageSize: 50
    })
    if (sequence === requestSequence) {
      options.value = response.data?.items || []
      searched.value = true
    }
  } catch (error) {
    if (sequence === requestSequence) {
      ElMessage.error(error instanceof Error ? error.message : '加载基金目录失败')
    }
  } finally {
    if (sequence === requestSequence) {
      loading.value = false
    }
  }
}

const loadSelectedFund = async (code: string) => {
  if (!code || options.value.some(fund => fund.code === code && fund.fundType === props.fundType)) {
    return
  }
  const sequence = ++requestSequence
  loading.value = true
  try {
    const response: any = await fundCatalogPage({
      keyword: code,
      fundType: props.fundType,
      page: 1,
      pageSize: 50
    })
    if (sequence !== requestSequence) return
    const fund = (response.data?.items || []).find((item: FundOption) =>
      item.code === code && item.fundType === props.fundType
    )
    if (fund) {
      options.value = [fund, ...options.value.filter(item => item.code !== fund.code)]
    }
  } catch (error) {
    if (sequence === requestSequence) {
      ElMessage.error(error instanceof Error ? error.message : '加载已选基金失败')
    }
  } finally {
    if (sequence === requestSequence) {
      loading.value = false
    }
  }
}

const handleVisibleChange = (visible: boolean) => {
  if (visible && !searched.value) {
    void searchFunds('')
  }
}

watch(() => props.fundType, (fundType, previousFundType) => {
  if (fundType !== previousFundType) {
    emit('update:modelValue', '')
    options.value = []
    searched.value = false
    void searchFunds('')
  }
})

watch(() => props.modelValue, code => {
  void loadSelectedFund(code)
})

onMounted(async () => {
  void searchFunds('')
  if (props.modelValue) {
    await loadSelectedFund(props.modelValue)
  }
})
</script>

<style scoped>
.fund-category {
  float: right;
  margin-left: 12px;
  color: #8792a2;
}
.empty-message {
  display: block;
  padding: 8px 0;
  color: #8792a2;
  text-align: center;
}
</style>
