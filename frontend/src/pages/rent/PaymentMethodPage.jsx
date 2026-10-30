// [페이지] 월세 출금 계좌 등록 (임차인) — /mypage/payment-method
// 오픈뱅킹 인증 화면에서 계좌 등록·출금 동의를 받은 뒤 사용할 계좌를 고른다.
import { useEffect, useRef, useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router'
import PageHeader from '../../components/common/PageHeader.jsx'
import StatusBadge from '../../components/common/StatusBadge.jsx'
import { useAuthStore } from '../../stores/authStore.js'
import { api } from '../../utils/jwtUtil.js'

const OPEN_BANKING_PATH = '/v1/tenants/me/open-banking'
const PAYMENT_ACCOUNT_PATH = '/v1/tenants/me/payment-account'

function PaymentMethodPage() {
  const userId = useAuthStore((state) => state.user?.userId)
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const [callback] = useState(() => {
    const params = new URLSearchParams(window.location.search)
    return { code: params.get('code'), state: params.get('state'), error: params.get('error') }
  })
  const hasCallback = Boolean(callback.code || callback.state || callback.error)
  const isValidCallback = Boolean(callback.code && callback.state && !callback.error)
  const [isCompleting, setIsCompleting] = useState(isValidCallback)
  const completionRequest = useRef(null)
  const [accounts, setAccounts] = useState(null)
  const [selectedAccount, setSelectedAccount] = useState('')
  const [isWorking, setIsWorking] = useState(false)
  const [error, setError] = useState(hasCallback && !isValidCallback ? '오픈뱅킹 인증을 완료하지 못했습니다. 계좌 연결을 다시 진행해 주세요.' : '')
  const [message, setMessage] = useState('')
  const queryKey = ['payment-account', userId]
  const { data: paymentAccount, isPending, isFetching, error: loadError, refetch } = useQuery({
    queryKey,
    queryFn: async ({ signal }) => (await api.get(PAYMENT_ACCOUNT_PATH, { signal })).data || null,
    enabled: Boolean(userId) && !isCompleting,
    retry: false,
  })

  useEffect(() => {
    if (!hasCallback || !userId) return
    // 인증 코드를 주소와 방문 기록에 남기지 않는다.
    navigate('/mypage/payment-method', { replace: true })
    if (!isValidCallback) return
    // StrictMode에서도 일회용 인증 코드는 한 번만 교환한다.
    completionRequest.current ??= api.post(`${OPEN_BANKING_PATH}/callback`, {
      code: callback.code,
      state: callback.state,
    }).then(async () => (await api.get(`${OPEN_BANKING_PATH}/accounts`)).data)
    let isActive = true
    completionRequest.current.then((data) => {
      if (!isActive || useAuthStore.getState().user?.userId !== userId) return
      setAccounts(data)
      setMessage('계좌 연결이 완료됐어요. 월세를 출금할 계좌를 선택해 주세요.')
    }).catch((failure) => {
      if (isActive) setError(failure.response?.data?.message || '계좌 연결 결과를 확인하지 못했습니다. 연결된 계좌를 다시 불러오거나 계좌 연결을 다시 진행해 주세요.')
    }).finally(() => {
      if (isActive) setIsCompleting(false)
    })
    return () => { isActive = false }
  }, [callback, hasCallback, isValidCallback, navigate, userId])

  const isBusy = isCompleting || isWorking
  const handleConnect = async () => {
    if (isBusy) return
    setIsWorking(true)
    setError('')
    setMessage('')
    try {
      const { data } = await api.post(`${OPEN_BANKING_PATH}/authorization`)
      if (useAuthStore.getState().user?.userId !== userId) return
      window.location.assign(data.authorizationUrl)
    } catch (failure) {
      setError(failure.response?.data?.message || '오픈뱅킹 인증 화면을 열지 못했습니다. 다시 시도해 주세요.')
      setIsWorking(false)
    }
  }

  const handleLoadAccounts = async () => {
    if (isBusy) return
    setIsWorking(true)
    setError('')
    setMessage('')
    try {
      const { data } = await api.get(`${OPEN_BANKING_PATH}/accounts`)
      if (useAuthStore.getState().user?.userId !== userId) return
      setAccounts(data)
      setSelectedAccount('')
    } catch (failure) {
      setError(failure.response?.data?.message || '연결된 계좌를 불러오지 못했습니다. 계좌 연결을 먼저 진행해 주세요.')
    } finally {
      setIsWorking(false)
    }
  }

  const handleSave = async (event) => {
    event.preventDefault()
    if (isBusy || !accounts?.some((account) => account.fintechUseNum === selectedAccount && account.withdrawalAgreed)) return
    setIsWorking(true)
    setError('')
    setMessage('')
    try {
      await queryClient.cancelQueries({ queryKey })
      const { data } = await api.post(PAYMENT_ACCOUNT_PATH, { fintechUseNum: selectedAccount })
      if (useAuthStore.getState().user?.userId !== userId) return
      queryClient.setQueryData(queryKey, data)
      setMessage('월세 출금 계좌가 등록됐어요.')
    } catch (failure) {
      setError(failure.response?.data?.message || '출금 계좌를 등록하지 못했습니다. 다시 시도해 주세요.')
    } finally {
      setIsWorking(false)
    }
  }

  return (
    <div className="page-container">
      <PageHeader title="월세 출금 계좌" description="오픈뱅킹에 계좌를 연결하고 출금에 동의하면 매월 납부일에 월세가 자동 출금돼요." />
      <p className="rounded-xl bg-slate-100 px-4 py-3 text-slate-800">
        출금된 월세는 보금의 수납 계좌로 먼저 입금된 뒤 임대인의 정산 계좌로 보내져요.
      </p>
      <section aria-labelledby="account-title" className="card flex max-w-xl flex-col gap-4 p-7">
        <div className="flex items-center justify-between gap-3">
          <h2 id="account-title" className="text-xl font-bold">현재 출금 계좌</h2>
          {paymentAccount && <StatusBadge tone="blue">사용 중</StatusBadge>}
        </div>
        {isCompleting ? <p role="status">오픈뱅킹 인증 결과를 확인하고 있어요.</p> : isPending ? <p role="status">출금 계좌를 불러오고 있어요.</p> : loadError ? (
          <div role="alert">
            <p className="text-red-600">출금 계좌를 불러오지 못했습니다.</p>
            <button type="button" className="btn-secondary btn-sm" disabled={isBusy || isFetching} onClick={() => refetch()}>다시 불러오기</button>
          </div>
        ) : <p>{paymentAccount ? `${paymentAccount.bankCode} · ${paymentAccount.accountNumberMasked}` : '등록된 출금 계좌가 없어요.'}</p>}
        <p className="text-sm text-gray-600">금융결제원 오픈뱅킹 화면에서 계좌 인증과 출금 동의를 진행해요.</p>
        <button type="button" onClick={handleConnect} disabled={isBusy} className="btn-primary">오픈뱅킹 계좌 연결하기</button>
        <button type="button" onClick={handleLoadAccounts} disabled={isBusy} className="btn-secondary">연결된 계좌 불러오기</button>
        {accounts && (
          <form onSubmit={handleSave} className="flex flex-col gap-3">
            <label htmlFor="payment-account" className="font-bold">월세를 출금할 계좌</label>
            <select id="payment-account" className="rounded-lg border border-gray-300 px-3 py-2" value={selectedAccount} onChange={(event) => setSelectedAccount(event.target.value)} disabled={isBusy} required>
              <option value="">계좌를 선택해 주세요</option>
              {accounts.map((account) => (
                <option key={account.fintechUseNum} value={account.fintechUseNum} disabled={!account.withdrawalAgreed}>
                  {account.bankCode} · {account.accountNumberMasked} · {account.holderName}{!account.withdrawalAgreed ? ' (출금 동의 필요)' : ''}
                </option>
              ))}
            </select>
            {accounts.length === 0 && <p>연결된 계좌가 없어요. 오픈뱅킹 계좌 연결을 진행해 주세요.</p>}
            <button type="submit" disabled={isBusy || !selectedAccount} className="btn-primary">출금 계좌로 등록하기</button>
          </form>
        )}
        {error && <p role="alert" className="text-red-600">{error}</p>}
        {message && <p role="status">{message}</p>}
      </section>
      <p className="text-sm text-gray-600">새 계좌를 등록하면 기존 출금 계좌가 교체돼요.</p>
    </div>
  )
}

export default PaymentMethodPage
