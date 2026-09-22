import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'

let resizingColumn: { table: HTMLElement; cell: HTMLElement; startX: number; startWidth: number; className: string } | null = null

const getColumnClass = (cell: HTMLElement) =>
  Array.from(cell.classList).find((name) => name.includes('_column_')) || ''

document.addEventListener('mousedown', (event) => {
  const target = event.target as HTMLElement
  const cell = target.closest('th') as HTMLElement | null
  const table = cell?.closest('.el-table') as HTMLElement | null
  if (!cell || !table || !cell.classList.contains('el-table__cell')) return

  const rect = cell.getBoundingClientRect()
  const isResizeHandle = rect.width > 12 && rect.right - event.clientX < 8
  const isLastCell = cell.parentElement?.lastElementChild === cell
  const className = getColumnClass(cell)
  if (!isResizeHandle || isLastCell || !className) return

  resizingColumn = { table, cell, startX: event.clientX, startWidth: rect.width, className }
})

document.addEventListener('mousemove', (event) => {
  if (!resizingColumn) return
  const width = Math.max(30, resizingColumn.startWidth + event.clientX - resizingColumn.startX)
  const cells = resizingColumn.table.querySelectorAll<HTMLElement>(`[class~="${resizingColumn.className}"]`)
  cells.forEach((cell) => {
    cell.style.width = `${width}px`
  })
})

document.addEventListener('mouseup', () => {
  resizingColumn = null
})

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.use(ElementPlus)

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

app.mount('#app')
