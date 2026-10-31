// [페이지] 정산 계좌 등록 (임대인) — /mypage/settlement-account
// 회사 수납 계좌로 받은 월세를 지급할 임대인 본인 명의 계좌다.
import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import PageHeader from '../../components/common/PageHeader.jsx'
import StatusBadge from '../../components/common/StatusBadge.jsx'
import { Field, TextInput } from '../../components/common/FormControls.jsx'
import { useAuthStore } from '../../stores/authStore.js'
import { api } from '../../utils/jwtUtil.js'

const ACCOUNT_PATH = '/v1/landlords/me/settlement-account'

function SettlementAccountPage() {
  const userId = useAuthStore((state) => state.user?.userId)
  const queryClient = useQueryClient()
  const queryKey = ['settlement-account', userId]
  const { data: account, isPending, isFetching, error: loadError, refetch } = useQuery({
    queryKey,
    queryFn: async ({ signal }) => (await api.get(ACCOUNT_PATH, { signal })).data || null,
    enabled: Boolean(userId),
    retry: false,
  })
  const [bankCode, setBankCode] = useState('')
  const [accountNumber, setAccountNumber] = useState('')
  const [holderName, setHolderName] = useState('')
  const [birthDate, setBirthDate] = useState('')
  const [isSaving, setIsSaving] = useState(false)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const isDisabled = isPending || isFetching || Boolean(loadError) || isSaving

  const handleSave = async (event) => {
    event.preventDefault()
    if (isDisabled) return
    setError('')
    setMessage('')
    if (!/^[0-9]{3}$/.test(bankCode) || !/^[0-9]{4,16}$/.test(accountNumber) || !holderName.trim() || holderName.trim().length > 50 || !/^[0-9]{6}$/.test(birthDate)) {
      setError('은행 코드 3자리, 계좌번호 4~16자리, 예금주 50자 이하, 생년월일 6자리를 확인해 주세요.')
      return
    }
    setIsSaving(true)
    try {
      await queryClient.cancelQueries({ queryKey })
      const { data } = await api.post(ACCOUNT_PATH, { bankCode, accountNumber, holderName: holderName.trim(), birthDate })
      if (useAuthStore.getState().user?.userId !== userId) return
      queryClient.setQueryData(queryKey, data)
      setBankCode('')
      setAccountNumber('')
      setHolderName('')
      setBirthDate('')
      setMessage(data.verifiedAt ? '본인 명의 확인을 마치고 정산 계좌를 저장했어요.' : '정산 계좌가 저장됐어요. 계좌 확인 상태를 확인해 주세요.')
    } catch (failure) {
      setError(failure.response?.data?.message || '정산 계좌를 저장하지 못했습니다. 다시 시도해 주세요.')
    } finally {
      setIsSaving(false)
    }
  }

  return (
    <div className="page-container">
      <PageHeader title="정산 계좌" description="보금의 수납 계좌로 입금된 월세를 받을 본인 명의 계좌를 등록해 주세요." />
      <section className="card flex max-w-xl flex-col gap-4">
        <div className="flex items-center justify-between gap-3">
          <span className="text-gray-600">현재 계좌</span>
          {account && !loadError && (
            <span className="flex items-center gap-2">
              <strong>{account.bankCode} · {account.accountNumberMasked} · {account.holderName}</strong>
              <StatusBadge tone={account.verifiedAt ? 'green' : 'amber'}>{account.verifiedAt ? '검증 완료' : '재검증 필요'}</StatusBadge>
            </span>
          )}
        </div>
        {isPending ? <p role="status">정산 계좌를 불러오고 있어요.</p> : loadError ? (
          <div role="alert">
            <p className="text-red-600">정산 계좌를 불러오지 못했습니다.</p>
            <button type="button" className="btn-secondary btn-sm" disabled={isFetching} onClick={() => refetch()}>다시 불러오기</button>
          </div>
        ) : !account && <p>등록된 정산 계좌가 없어요.</p>}
        <p className="rounded-xl bg-slate-100 px-4 py-3 text-sm text-slate-800">본인 명의 계좌를 등록해 주세요. 은행에서 예금주를 확인한 계좌로 월세를 지급해요. 재검증이 필요한 계좌는 다시 등록해 주세요.</p>
        <form onSubmit={handleSave} className="flex flex-col gap-4">
          <Field label="은행 코드" htmlFor="settlement-bank-code" hint="은행 코드를 숫자 3자리로 입력해 주세요.">
            <TextInput id="settlement-bank-code" inputMode="numeric" pattern="[0-9]{3}" maxLength={3} required value={bankCode} onChange={(event) => setBankCode(event.target.value)} disabled={isDisabled} />
          </Field>
          <Field label="계좌번호" htmlFor="settlement-account-number" hint="하이픈 없이 숫자만 입력해 주세요.">
            <TextInput id="settlement-account-number" inputMode="numeric" autoComplete="off" pattern="[0-9]{4,16}" maxLength={16} required value={accountNumber} onChange={(event) => setAccountNumber(event.target.value)} disabled={isDisabled} />
          </Field>
          <Field label="예금주" htmlFor="settlement-holder-name">
            <TextInput id="settlement-holder-name" maxLength={50} required value={holderName} onChange={(event) => setHolderName(event.target.value)} disabled={isDisabled} />
          </Field>
          <Field label="생년월일" htmlFor="settlement-birth-date" hint="본인 명의 확인에 사용할 생년월일을 YYMMDD 6자리로 입력해 주세요. 저장하지 않아요.">
            <TextInput id="settlement-birth-date" inputMode="numeric" autoComplete="off" pattern="[0-9]{6}" maxLength={6} required value={birthDate} onChange={(event) => setBirthDate(event.target.value)} disabled={isDisabled} />
          </Field>
          {error && <p role="alert" className="text-red-600">{error}</p>}
          {message && <p role="status">{message}</p>}
          <button type="submit" disabled={isDisabled} className="btn-primary">{isSaving ? '저장 중…' : account ? '정산 계좌 변경하기' : '정산 계좌 등록하기'}</button>
        </form>
      </section>
    </div>
  )
}

export default SettlementAccountPage
