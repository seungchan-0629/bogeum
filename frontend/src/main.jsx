import { restoreSession } from './utils/jwtUtil.js'
import { StrictMode, useEffect, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from 'react-router/dom'
import './index.css'
import { router } from './router/index.jsx'

const queryClient = new QueryClient()

export function AuthBootstrap() {
  const [ready, setReady] = useState(false)
  const [error, setError] = useState('')
  useEffect(() => {
    restoreSession()
      .then(() => setReady(true))
      .catch(() => setError('로그인 상태를 확인하지 못했습니다. 연결을 확인하고 새로고침해 주세요.'))
  }, [])
  if (error) return <p role="alert">{error}</p>
  if (!ready) return <p role="status">로그인 상태를 확인하고 있어요.</p>
  return <RouterProvider router={router} />
}

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <AuthBootstrap />
    </QueryClientProvider>
  </StrictMode>,
)
