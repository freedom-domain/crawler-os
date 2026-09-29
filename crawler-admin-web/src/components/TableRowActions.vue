<template>
  <div class="table-row-actions">
    <el-dropdown trigger="click" popper-class="table-action-popper" @command="emit('command', $event)">
      <el-button size="small" type="primary">
        操作<el-icon class="el-icon--right"><ArrowDown /></el-icon>
      </el-button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item
            v-for="item in items"
            :key="item.command"
            :command="item.command"
            :disabled="item.disabled"
            :divided="item.divided"
            :class="{ 'is-danger': item.danger }"
          >
            <span class="table-action-item">
              <el-icon><component :is="item.icon" /></el-icon>
              {{ item.label }}
            </span>
          </el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<script setup lang="ts">
import type { Component } from 'vue'
import { ArrowDown } from '@element-plus/icons-vue'

export interface TableRowAction {
  command: string
  label: string
  icon: Component
  disabled?: boolean
  divided?: boolean
  danger?: boolean
}

defineProps<{
  items: TableRowAction[]
}>()

const emit = defineEmits<{
  (event: 'command', command: string): void
}>()
</script>

<style scoped>
.table-action-item { display: inline-flex; align-items: center; gap: 8px; }
</style>
