import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import PageHeader from '../../../components/common/PageHeader.jsx'
import StatusBadge from '../../../components/common/StatusBadge.jsx'
import { getInstitutionToken, issueInstitutionToken } from '../../../api/admin/index.js'

const dateTime = (value) => value ? value.replace('T', ' ').slice(0, 19) : '-'

function AdminOpenBankingPage() {
  const queryClient = useQueryClient()
  const token = useQuery({ queryKey: ['admin-institution-token'], queryFn: getInstitutionToken, refetchInterval: 30000 })
  const issue = useMutation({
    mutationFn: issueInstitutionToken,
    onSuccess: (data) => queryClient.setQueryData(['admin-institution-token'], data),
  })
  const data = token.data
  const error = issue.error || token.error

  return (
    <>
      <PageHeader title="기업용 OOB 토큰 관리" description="기관 송금과 이체 결과 조회에 사용하는 토큰을 관리합니다." />
      <section className="card flex max-w-3xl flex-col gap-6 p-6">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <h2 className="text-lg font-bold">기관 인증 상태</h2>
          {data && <StatusBadge tone={data.usable ? 'green' : 'amber'}>{data.usable ? '사용 가능' : data.issued ? '만료·재발급 필요' : '미발급'}</StatusBadge>}
        </div>
        {token.isPending && <p role="status" className="text-sm text-gray-500">토큰 상태를 확인하고 있습니다.</p>}
        {data && <dl className="grid gap-x-6 gap-y-4 text-sm sm:grid-cols-[140px_1fr]">
          <dt className="text-gray-500">권한 범위</dt><dd>oob</dd>
          <dt className="text-gray-500">이용기관 코드</dt><dd>{data.clientUseCode || '-'}</dd>
          <dt className="text-gray-500">토큰</dt><dd className="break-all font-mono">{data.maskedToken || '-'}</dd>
          <dt className="text-gray-500">발급 시각</dt><dd>{dateTime(data.issuedAt)}</dd>
          <dt className="text-gray-500">만료 시각</dt><dd>{dateTime(data.expiresAt)}</dd>
        </dl>}
        <div className="rounded-xl bg-slate-50 p-4 text-sm leading-6 text-gray-600">
          기관 토큰은 아래 버튼에서 발급·재발급할 수 있습니다. 만료되면 새 토큰을 발급해 주세요.
          인증 정보는 서버 설정을 사용하며, 토큰 원문은 화면에 표시하지 않습니다.
        </div>
        {error && <p role="alert" className="text-sm text-red-600">{error.response?.data?.message || '토큰 요청에 실패했습니다. 가상금결원 실행 상태를 확인해 주세요.'}</p>}
        {issue.isSuccess && <p role="status" className="text-sm text-emerald-700">기관 토큰을 발급하고 저장했습니다.</p>}
        <div className="flex gap-3">
          <button type="button" disabled={issue.isPending || token.isPending} onClick={() => issue.mutate()} className="btn-primary px-5 py-3 disabled:opacity-40">
            {issue.isPending ? '발급 중…' : data?.issued ? '토큰 재발급' : '토큰 발급'}
          </button>
          <button type="button" disabled={token.isFetching || issue.isPending} onClick={() => token.refetch()} className="rounded-xl border border-gray-300 px-4 text-sm disabled:opacity-40">상태 새로고침</button>
        </div>
      </section>
    </>
  )
}

export default AdminOpenBankingPage
