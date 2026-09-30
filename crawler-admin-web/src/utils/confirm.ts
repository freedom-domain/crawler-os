import { ElMessageBox } from 'element-plus'

export interface ConfirmOptions {
  title?: string
  confirmText?: string
  cancelText?: string
  danger?: boolean
}

export async function confirm(message: string, options: ConfirmOptions = {}): Promise<boolean> {
  const {
    title = '确认操作',
    confirmText = '确定',
    cancelText = '取消',
    danger = false
  } = options

  const promise = ElMessageBox.confirm(message, title, {
    type: danger ? 'error' : 'warning',
    confirmButtonText: confirmText,
    cancelButtonText: cancelText,
    confirmButtonClass: danger ? 'el-button--danger' : '',
    distinguishCancelAndClose: true
  })

  // 弹窗渲染后，根据内容区实际高度动态设置图标尺寸
  const applyIconSize = () => {
    const box = document.querySelector('.el-message-box')
    if (!box) return
    const msgEl = box.querySelector('.el-message-box__message') as HTMLElement
    if (!msgEl) return
    const status = box.querySelector('.el-message-box__status') as HTMLElement
    if (!status) return
    const lineHeight = 25.2 // font-size 14px × line-height 1.8
    const lineCount = Math.max(1, Math.round(msgEl.offsetHeight / lineHeight))
    const size = Math.min(16 * Math.pow(2, lineCount - 1), 32)
    status.style.fontSize = `${size}px`
  }
  // 300ms 后 DOM 已渲染完成
  setTimeout(applyIconSize, 300)

  try {
    await promise
    return true
  } catch {
    return false
  }
}
