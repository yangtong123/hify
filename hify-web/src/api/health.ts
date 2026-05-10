import { get } from '@/utils/request'

export function getHealth(): Promise<string> {
  return get('/v1/health')
}
