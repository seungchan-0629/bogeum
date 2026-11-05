import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import PageHeader from '../../components/common/PageHeader.jsx'
import { api } from '../../utils/jwtUtil.js'

function OpenBankingCallbackPage() {
  const [params] = useSearchParams()
  const code = params.get('code')
  const state = params.get('state')
  const authorizationError = params.get('error')
  const hasStarted = useRef(false)
  const [result, setResult] = useState(() => {
    if (authorizationError || !code || !state) {
      return {
        message: authorizationError === 'access_denied' ? '계좌 등록을 취소했습니다.' : '인증 정보가 없습니다. 계좌 등록을 다시 시작해 주세요.',
        isError: authorizationError !== 'access_denied',
        isDone: true,
      }
    }
    return { message: '계좌를 등록하고 있어요.', isError: false, isDone: false }
  })

  useEffect(() => {
    // 인증 코드는 한 번만 교환한다. 개발 모드의 Effect 재실행도 중복 요청하지 않는다.
    if (hasStarted.current) return
    hasStarted.current = true
    if (authorizationError || !code || !state) return
    api.get('/openbanking/callback', { params: { code, state } })
      .then(() => setResult({ message: '계좌가 등록되었습니다.', isError: false, isDone: true }))
      .catch(() => setResult({ message: '계좌를 등록하지 못했습니다. 가상 금결원 서버 실행 상태를 확인하고 다시 시작해 주세요.', isError: true, isDone: true }))
  }, [authorizationError, code, state])

  return (
    <div className="page-container">
      <PageHeader title="계좌 등록" />
      <section className="card flex max-w-xl flex-col gap-4">
        <p role={result.isError ? 'alert' : 'status'}>{result.message}</p>
        {result.isDone && <Link to="/mypage" replace className="btn-primary self-start">마이페이지로 돌아가기</Link>}
      </section>
    </div>
  )
}

export default OpenBankingCallbackPage
