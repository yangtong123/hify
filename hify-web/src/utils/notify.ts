import { ElMessage } from 'element-plus'

const defaults = { duration: 3000, grouping: true }

export function notifySuccess(msg: string) {
  ElMessage.success({ message: msg, ...defaults })
}

export function notifyError(msg: string) {
  ElMessage.error({ message: msg, ...defaults })
}

export function notifyWarning(msg: string) {
  ElMessage.warning({ message: msg, ...defaults })
}
