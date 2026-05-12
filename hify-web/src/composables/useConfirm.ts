import { ElMessageBox, ElMessage } from 'element-plus'

export function useConfirm() {
  function confirm(
    message: string,
    api: () => Promise<unknown>,
    successMsg?: string,
    title?: string,
  ): Promise<void> {
    return ElMessageBox.confirm(message, title || '确认操作', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }).then(async () => {
      await api()
      ElMessage.success(successMsg || '操作成功')
    })
  }

  return { confirm }
}
