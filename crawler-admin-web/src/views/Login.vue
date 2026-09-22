<template>
  <div class="login-container">
    <div class="ambient ambient-one"></div>
    <div class="ambient ambient-two"></div>
    <div class="login-aside">
      <div class="brand-lockup"><span class="brand-mark">C</span><span>Crawler<span>OS</span></span></div>
      <div class="aside-copy">
        <p class="eyebrow">INTELLIGENT CRAWL OPERATIONS</p>
        <h1>让数据采集<br /><em>更有秩序。</em></h1>
        <p>统一管理爬虫、任务与内容资产，<br />让每一次采集都清晰可追踪。</p>
      </div>
      <div class="aside-foot">CrawlerOS · Admin Console</div>
    </div>
    <div class="login-box">
      <div class="mobile-brand"><span class="brand-mark">C</span>Crawler<span>OS</span></div>
      <p class="welcome">欢迎回来</p>
      <h2 class="title">登录管理控制台</h2>
      <p class="subtitle">输入账号，继续你的数据工作。</p>
      <el-form :model="form" :rules="rules" ref="formRef" @submit.prevent="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="form.username" prefix-icon="User" placeholder="用户名" size="large" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" prefix-icon="Lock" type="password" placeholder="密码" size="large" @keyup.enter="handleLogin" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" style="width:100%" :loading="loading" @click="handleLogin">
            进入工作台
          </el-button>
        </el-form-item>
      </el-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, FormInstance } from 'element-plus'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

// 判断 token 是否未过期
const isTokenValid = (): boolean => {
  const token = localStorage.getItem('token')
  if (!token) return false
  try {
    const payload = token.split('.')[1]
    const binary = atob(payload)
    const bytes = Uint8Array.from(binary, c => c.charCodeAt(0))
    const json = new TextDecoder('utf-8').decode(bytes)
    const decoded = JSON.parse(json)
    const exp = decoded.exp as number
    return exp * 1000 > Date.now()
  } catch {
    return false
  }
}

onMounted(() => {
  if (isTokenValid()) {
    router.replace('/dashboard')
  }
})

const handleLogin = async () => {
  await formRef.value?.validate()
  loading.value = true
  try {
    await userStore.login(form.username, form.password)
    ElMessage.success('登录成功')
    router.push('/dashboard')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-container {
  height: 100vh;
  display: grid;
  grid-template-columns: minmax(420px, 1fr) 480px;
  align-items: center;
  padding: 5vw 9vw;
  position: relative;
  overflow: hidden;
  background: #172b4d;
}
.login-aside { color: white; height: min(650px, 80vh); display: flex; flex-direction: column; justify-content: space-between; position: relative; z-index: 1; }
.brand-lockup, .mobile-brand { display: flex; align-items: center; gap: 11px; font-size: 20px; font-weight: 800; letter-spacing: .4px; }
.brand-lockup > span:last-child span, .mobile-brand > span:last-child { color: #72e0c8; }
.brand-mark { width: 34px; height: 34px; display: grid; place-items: center; border-radius: 9px; background: #72e0c8; color: #102a43; font-weight: 900; }
.aside-copy { margin-top: auto; margin-bottom: auto; }
.eyebrow { margin-bottom: 20px; color: #72e0c8; font-size: 11px; font-weight: 800; letter-spacing: 2px; }
.aside-copy h1 { font-size: clamp(38px, 4vw, 56px); line-height: 1.12; letter-spacing: -1.5px; }
.aside-copy h1 em { color: #72e0c8; font-style: normal; }
.aside-copy p:last-child { margin-top: 24px; color: #b7c9d9; font-size: 16px; line-height: 1.9; }
.aside-foot { color: #829ab1; font-size: 12px; letter-spacing: 1px; }
.ambient { position: absolute; border-radius: 50%; border: 1px solid rgba(114, 224, 200, .16); }
.ambient-one { width: 520px; height: 520px; left: 38%; top: -170px; }
.ambient-two { width: 760px; height: 760px; left: 15%; bottom: -570px; }
.login-box { position: relative; z-index: 1; width: 100%; padding: 44px; background: #fff; border: 1px solid #e6e9ef; border-radius: 9px; box-shadow: 0 16px 36px rgba(23, 43, 77, .16); }
.mobile-brand { display: none; color: #172b4d; margin-bottom: 32px; }
.welcome { color: #16a6a3; font-size: 13px; font-weight: 700; margin-bottom: 10px; }
.title { color: #172b4d; font-size: 25px; letter-spacing: -.4px; }
.subtitle { margin: 8px 0 32px; color: #829ab1; font-size: 14px; }
:deep(.el-form-item) { margin-bottom: 20px; }
:deep(.el-input__wrapper) { height: 48px; background: #f7fafc; }
:deep(.el-button) { height: 48px; margin-top: 8px; font-size: 15px; }
@media (max-width: 800px) {
  .login-container { display: flex; justify-content: center; padding: 24px; }
  .login-aside { display: none; }
  .login-box { max-width: 440px; padding: 34px 28px; }
  .mobile-brand { display: flex; }
}
@media (max-width: 480px) {
  .login-container { padding: 16px; }
  .login-box { padding: 28px 20px; }
  .title { font-size: 21px; }
}
</style>
