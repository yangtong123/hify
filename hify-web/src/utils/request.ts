import axios from 'axios'
import { ElMessage } from 'element-plus'

const http = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

http.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body.code === 200) {
      return body.data
    }
    ElMessage.error(body.message || '请求失败')
    return Promise.reject(new Error(body.message || '请求失败'))
  },
  (error) => {
    const message = error.response?.data?.message || error.message || '网络异常'
    ElMessage.error(message)
    return Promise.reject(new Error(message))
  }
)

export async function get<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  return http.get(url, { params })
}

export async function post<T>(url: string, data?: unknown): Promise<T> {
  return http.post(url, data)
}

export async function put<T>(url: string, data?: unknown): Promise<T> {
  return http.put(url, data)
}

export async function del<T>(url: string): Promise<T> {
  return http.delete(url)
}
