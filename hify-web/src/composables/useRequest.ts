import { ref, type Ref, type UnwrapRef } from 'vue'

export function useRequest<T, Args extends unknown[] = []>(
  api: (...args: Args) => Promise<T>,
): {
  data: Ref<UnwrapRef<T> | null>
  loading: Ref<boolean>
  error: Ref<Error | null>
  execute: (...args: Args) => Promise<T | null>
} {
  const data = ref<T | null>(null) as Ref<UnwrapRef<T> | null>
  const loading = ref(false)
  const error = ref<Error | null>(null)

  async function execute(...args: Args): Promise<T | null> {
    loading.value = true
    error.value = null
    try {
      const result = await api(...args)
      data.value = result as UnwrapRef<T>
      return result
    } catch (e) {
      error.value = e as Error
      return null
    } finally {
      loading.value = false
    }
  }

  return { data, loading, error, execute }
}
