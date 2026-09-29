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

  try {
    await ElMessageBox.confirm(message, title, {
      type: danger ? 'error' : 'warning',
      confirmButtonText: confirmText,
      cancelButtonText: cancelText,
      confirmButtonClass: danger ? 'el-button--danger' : '',
      distinguishCancelAndClose: true
    })
    return true
  } catch {
    return false
  }
}
