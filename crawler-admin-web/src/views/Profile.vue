<template>
  <div class="profile-page">
    <!-- 用户卡片 -->
    <div class="profile-card">
      <div class="avatar-section">
        <div class="avatar">
          <el-icon :size="48"><UserFilled /></el-icon>
        </div>
        <div class="user-meta">
          <h2 class="username">{{ userStore.nickname || userStore.username }}</h2>
          <p class="user-role">
            <el-tag type="primary" size="small">{{ userStore.role || '未分配角色' }}</el-tag>
          </p>
        </div>
      </div>
    </div>

    <!-- 详细信息 -->
    <el-card class="info-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">账户信息</span>
        </div>
      </template>
      <div class="info-grid">
        <div class="info-item">
          <div class="info-label">
            <el-icon><User /></el-icon>
            <span>用户ID</span>
          </div>
          <div class="info-value">{{ userStore.userId }}</div>
        </div>
        <div class="info-item">
          <div class="info-label">
            <el-icon><Avatar /></el-icon>
            <span>用户名</span>
          </div>
          <div class="info-value">{{ userStore.username }}</div>
        </div>
        <div class="info-item">
          <div class="info-label">
            <el-icon><ChatDotRound /></el-icon>
            <span>昵称</span>
          </div>
          <div class="info-value">{{ userStore.nickname || '-' }}</div>
        </div>
        <div class="info-item">
          <div class="info-label">
            <el-icon><Lock /></el-icon>
            <span>角色</span>
          </div>
          <div class="info-value">{{ userStore.role || '-' }}</div>
        </div>
      </div>
    </el-card>

    <!-- 权限列表 -->
    <el-card class="perm-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">权限列表</span>
          <el-tag size="small" type="info">{{ userStore.permissions.length }} 项</el-tag>
        </div>
      </template>
      <div v-if="userStore.permissions.length > 0" class="perm-tree">
        <div v-for="group in permGroups" :key="group.name" class="perm-group">
          <div class="perm-group-header">
            <el-icon><component :is="group.icon" /></el-icon>
            <span class="perm-group-name">{{ group.name }}</span>
            <el-tag size="small" type="info">{{ group.items.length }}</el-tag>
          </div>
          <div class="perm-group-items">
            <el-tag
              v-for="item in group.items"
              :key="item.code"
              class="perm-tag"
              size="default"
              effect="light"
            >
              {{ item.name }}
            </el-tag>
          </div>
        </div>
      </div>
      <el-empty v-else description="暂无权限" :image-size="80" />
    </el-card>

  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { permissionTree } from '@/api'
import { UserFilled, User, Avatar, ChatDotRound, Lock, Connection, List, Search, Folder, Setting, PriceTag } from '@element-plus/icons-vue'

const userStore = useUserStore()
const permTree = ref<any[]>([])

// 图标映射
const iconMap: Record<string, any> = {
  user: User,
  role: Avatar,
  permission: Lock,
  spider: Connection,
  task: List,
  search: Search,
  file: Folder,
  dict: PriceTag,
  system: Setting
}

const loadPermTree = async () => {
  try {
    const res: any = await permissionTree()
    permTree.value = res.data || []
  } catch {
    permTree.value = []
  }
}

onMounted(loadPermTree)

// 按分组展示权限
const permGroups = computed(() => {
  const groups: { name: string; icon: any; items: { code: string; name: string }[] }[] = []
  const userPerms = new Set(userStore.permissions)
  
  const processNode = (node: any) => {
    // 如果当前节点在用户权限中，或者其子节点在用户权限中
    const hasPerm = userPerms.has(node.code)
    const childPerms: { code: string; name: string }[] = []
    
    if (node.children && node.children.length > 0) {
      for (const child of node.children) {
        if (userPerms.has(child.code)) {
          childPerms.push({ code: child.code, name: child.name })
        }
        // 递归处理子节点
        if (child.children && child.children.length > 0) {
          const subGroup = processNode(child)
          if (subGroup) {
            groups.push(subGroup)
          }
        }
      }
    }
    
    if (hasPerm || childPerms.length > 0) {
      return {
        name: node.name,
        icon: iconMap[node.code] || iconMap[node.icon] || Setting,
        items: hasPerm ? [{ code: node.code, name: node.name }, ...childPerms] : childPerms
      }
    }
    return null
  }
  
  for (const node of permTree.value) {
    const group = processNode(node)
    if (group) {
      groups.push(group)
    }
  }
  
  return groups
})
</script>

<style scoped>
.profile-page {
  max-width: 800px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

/* 用户卡片 */
.profile-card {
  background: linear-gradient(135deg, #132b47 0%, #10243d 100%);
  border-radius: 16px;
  padding: 32px;
  color: #fff;
  box-shadow: 0 8px 32px rgba(16, 36, 61, 0.15);
}

.avatar-section {
  display: flex;
  align-items: center;
  gap: 24px;
}

.avatar {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: rgba(114, 224, 200, 0.15);
  border: 2px solid rgba(114, 224, 200, 0.3);
  display: grid;
  place-items: center;
  color: #72e0c8;
}

.user-meta {
  flex: 1;
}

.username {
  font-size: 24px;
  font-weight: 700;
  margin: 0 0 8px 0;
  color: #fff;
}

.user-role {
  margin: 0;
}

/* 信息卡片 */
.info-card, .perm-card {
  border-radius: 12px;
  border: none;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #102a43;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.info-label {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #909399;
}

.info-label .el-icon {
  color: #109f9a;
}

.info-value {
  font-size: 15px;
  font-weight: 500;
  color: #303133;
}

/* 权限列表 */
.perm-tree {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.perm-group {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  overflow: hidden;
}

.perm-group-header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 16px;
  background: #f5f7fa;
  border-bottom: 1px solid #ebeef5;
}

.perm-group-header .el-icon {
  color: #109f9a;
}

.perm-group-name {
  font-weight: 600;
  color: #303133;
  flex: 1;
}

.perm-group-items {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px 16px;
}

.perm-tag {
  border-radius: 6px;
  padding: 4px 10px;
}

@media (max-width: 600px) {
  .info-grid {
    grid-template-columns: 1fr;
  }
}
</style>
