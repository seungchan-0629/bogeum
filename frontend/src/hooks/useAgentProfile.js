import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '../utils/jwtUtil.js'
import { useAuthStore } from '../stores/authStore.js'

// 계정별로 조회 결과를 분리하고 화면 재진입·새로 확인 시 서버의 승인 상태를 읽는다.
export function useAgentProfile() {
  const userId = useAuthStore((state) => state.user?.userId)
  const query = useQuery({
    queryKey: ['agent-profile', userId],
    queryFn: async ({ signal }) => (await api.get('/v1/agents/me', { signal })).data,
    enabled: Boolean(userId),
    gcTime: 0,
    retry: false,
  })
  useEffect(() => {
    const current = useAuthStore.getState().user
    if (query.data && !query.isFetching && !query.isError && current?.userId === query.data.userId) {
      useAuthStore.getState().login({ ...current, agentId: query.data.agentId, approvalStatus: query.data.approvalStatus })
    }
  }, [query.data, query.isFetching, query.isError])
  return query
}
