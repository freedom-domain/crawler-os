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
      <div v-if="userStore.permissions.length > 0" class="perm-list">
        <el-tag
          v-for="perm in userStore.permissions"
          :key="perm"
          class="perm-tag"
          size="default"
          effect="light"
        >
          {{ getPermName(perm) }}
        </el-tag>
      </div>
      <el-empty v-else description="暂无权限" :image-size="80" />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'
import { permissionTree } from '@/api'
import { UserFilled, User, Avatar, ChatDotRound, Lock } from '@element-plus/icons-vue'

const userStore = useUserStore()
const permNameMap = ref<Record<string, string>>({})

const loadPermNames = async () => {
  try {
    const res: any = await permissionTree()
    const map: Record<string, string> = {}
    const buildMap = (nodes: any[]) => {
      for (const node of nodes) {
        if (node.code) map[node.code] = node.name
        if (node.children && node.children.length > 0) {
          buildMap(node.children)
        }
      }
    }
    buildMap(res.data || [])
    permNameMap.value = map
  } catch {
    // ignore
  }
}

onMounted(loadPermNames)

const getPermName = (code: string) => permNameMap.value[code] || code
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
.perm-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.perm-tag {
  border-radius: 6px;
  padding: 6px 12px;
}

@media (max-width: 600px) {
  .info-grid {
    grid-template-columns: 1fr;
  }
}
</style>
