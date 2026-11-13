// RISK_001·002: 관리자 위험 사용자 등록 및 유형별 목록·이름 검색·서버 페이징.
// 증빙은 키 기록 여부만 표시하며 수정·후보 조회·파일 열람은 별도 기능이다.
import { useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { RISK_REASONS, riskUserSchema } from './riskUserForm.js'
import { createAdminRiskUser, getAdminRiskUsers } from '../../../api/admin/index.js'
import { useAuthStore } from '../../../stores/authStore.js'
import PageHeader from '../../../components/common/PageHeader.jsx'
import DataTable from '../../../components/common/DataTable.jsx'
import StatusBadge from '../../../components/common/StatusBadge.jsx'
import SearchInput from '../../../components/common/SearchInput.jsx'
import Pagination from '../../../components/common/Pagination.jsx'
import ConfirmModal from '../../../components/common/ConfirmModal.jsx'
import { Field, TextInput, SelectInput, TextArea } from '../../../components/common/FormControls.jsx'
import { Tabs } from '../../../components/common/Tabs.jsx'

const USER_TYPE_TABS = [
  { key: 'TENANT', label: '임차인' },
  { key: 'LANDLORD', label: '임대인' },
  { key: 'AGENT', label: '중개사' },
]

function AdminRiskUserPage() {
  const user = useAuthStore((state) => state.user)
  if (user?.role !== 'ADMIN') return null
  // 계정 전환 시 이전 관리자의 입력·확인·Mutation 상태를 재사용하지 않는다.
  return <AdminRiskUserContent key={user.userId} user={user} />
}

function AdminRiskUserContent({ user }) {
  const queryClient = useQueryClient()
  const [userType, setUserType] = useState('TENANT')
  const [keyword, setKeyword] = useState('')
  const [page, setPage] = useState(1)
  const [formOpen, setFormOpen] = useState(false)
  const [confirmation, setConfirmation] = useState(null)
  const [success, setSuccess] = useState('')
  const submitLock = useRef(false)
  const { register, handleSubmit, reset, formState: { errors } } = useForm({
    resolver: zodResolver(riskUserSchema),
    defaultValues: { userId: '', reasonType: '', detail: '' },
  })
  const registration = useMutation({
    mutationFn: createAdminRiskUser,
    retry: false,
    onSuccess: (created, values) => {
      const currentUser = useAuthStore.getState().user
      if (currentUser?.role !== 'ADMIN' || currentUser.userId !== user.userId) return
      reset()
      setFormOpen(false)
      setUserType(RISK_REASONS[values.reasonType].userType)
      setKeyword('')
      setPage(1)
      setSuccess(`회원 ID ${created.userId}의 위험 정보를 등록했어요.`)
      // 같은 탭·검색어·페이지에서 등록해도 재조회하며, 다른 관리자 캐시는 건드리지 않는다.
      queryClient.invalidateQueries({ queryKey: ['admin-risk-users', user.userId] })
    },
    onSettled: () => { submitLock.current = false },
  })
  const list = useQuery({
    queryKey: ['admin-risk-users', user?.userId, userType, keyword, page],
    queryFn: ({ signal }) => getAdminRiskUsers(userType, keyword, page, signal),
    enabled: user?.role === 'ADMIN',
    gcTime: 0,
    retry: false,
  })
  const changeType = (nextType) => {
    setUserType(nextType)
    setPage(1)
  }
  const changeSearch = (event) => {
    setKeyword(event.target.value)
    setPage(1)
  }
  const reviewRegistration = (values) => {
    if (submitLock.current) return
    registration.reset()
    setSuccess('')
    setConfirmation(values)
  }
  const submitRegistration = () => {
    const currentUser = useAuthStore.getState().user
    if (!confirmation || submitLock.current || currentUser?.role !== 'ADMIN' || currentUser.userId !== user.userId) return
    // React가 다시 그리기 전의 연속 클릭도 한 번만 처리한다.
    submitLock.current = true
    setConfirmation(null)
    registration.mutate(confirmation)
  }

  const columns = [
    { label: '이름', render: (row) => (
      <div className="flex flex-col gap-1 whitespace-nowrap">
        <strong>{row.name}</strong>
        <span className="text-xs text-gray-500">ID {row.userId}</span>
      </div>
    ) },
    { label: '사유 유형', render: (row) => {
      const reason = RISK_REASONS[row.reasonType] ?? { label: '사유 확인 필요', tone: 'gray' }
      return <StatusBadge tone={reason.tone}>{reason.label}</StatusBadge>
    } },
    { label: '상세 내용', render: (row) => <span className="whitespace-pre-wrap break-words">{row.detail?.trim() ? row.detail : '-'}</span> },
    { label: '근거 자료', render: (row) => row.hasEvidence ? '있음' : '없음' },
    { label: '생성일', render: (row) => row.createdAt?.slice(0, 10) || '-' },
  ]
  const errorMessage = registration.error?.response?.data?.message
    || (registration.error?.response?.status === 401 ? '다시 로그인해 주세요.'
      : registration.error?.response?.status === 403 ? '등록 권한 또는 보안 인증을 확인한 뒤 다시 시도해 주세요.'
        : '위험 사용자 등록에 실패했어요. 입력 내용을 유지했으니 확인 후 다시 시도해 주세요.')

  return (
    <>
      <PageHeader title="위험 사용자" actions={
        <button type="button" className="btn-primary" disabled={registration.isPending} aria-expanded={formOpen} aria-controls="risk-user-form"
          onClick={() => { setFormOpen(!formOpen); setSuccess('') }}>
          {formOpen ? '등록 폼 닫기' : '위험 사용자 등록'}
        </button>
      } />
      {success && <p role="status" className="text-emerald-700">{success}</p>}
      {formOpen && (
        <form id="risk-user-form" aria-label="위험 사용자 등록" onSubmit={(event) => handleSubmit(reviewRegistration)(event)} noValidate className="card max-w-2xl">
          <fieldset disabled={registration.isPending} className="flex min-w-0 flex-col gap-4">
            <legend className="mb-4 text-lg font-bold">위험 사용자 등록</legend>
            <Field label="회원 ID" htmlFor="risk-user-id" hint="등록할 회원 ID를 확인해 주세요. 회원 역할과 사유 조합은 등록 시 확인합니다.">
              <TextInput id="risk-user-id" inputMode="numeric" aria-invalid={!!errors.userId} aria-describedby={errors.userId ? 'risk-user-id-error' : undefined} {...register('userId')} />
              {errors.userId && <p id="risk-user-id-error" role="alert" className="text-sm text-red-600">{errors.userId.message}</p>}
            </Field>
            <Field label="위험 사유" htmlFor="risk-user-reason">
              <SelectInput id="risk-user-reason" options={[{ value: '', label: '사유를 선택해 주세요' }, ...Object.entries(RISK_REASONS).map(([value, reason]) => ({ value, label: reason.label }))]}
                aria-invalid={!!errors.reasonType} aria-describedby={errors.reasonType ? 'risk-user-reason-error' : undefined} {...register('reasonType')} />
              {errors.reasonType && <p id="risk-user-reason-error" role="alert" className="text-sm text-red-600">{errors.reasonType.message}</p>}
            </Field>
            <Field label="상세 내용 (선택)" htmlFor="risk-user-detail" hint="공백과 줄바꿈을 포함해 최대 65,535바이트까지 입력할 수 있어요.">
              <TextArea id="risk-user-detail" aria-invalid={!!errors.detail} aria-describedby={errors.detail ? 'risk-user-detail-error' : undefined} {...register('detail')} />
              {errors.detail && <p id="risk-user-detail-error" role="alert" className="text-sm text-red-600">{errors.detail.message}</p>}
            </Field>
            <button type="submit" className="btn-primary self-start">{registration.isPending ? '등록 중…' : '입력 내용 확인'}</button>
          </fieldset>
          {registration.isPending && <p role="status" className="mt-3">위험 사용자 정보를 등록하고 있어요.</p>}
          {registration.isError && <p role="alert" className="mt-3 text-red-600">{errorMessage}</p>}
        </form>
      )}
      {confirmation && (
        <ConfirmModal title="위험 사용자 등록 확인" description="회원 ID와 위험 사유를 다시 확인해 주세요." confirmLabel="등록"
          onClose={() => setConfirmation(null)} onConfirm={submitRegistration}>
          <p>회원 ID: <strong>{confirmation.userId}</strong></p>
          <p>위험 사유: <strong>{RISK_REASONS[confirmation.reasonType].label}</strong></p>
        </ConfirmModal>
      )}
      <div className="flex flex-wrap items-end justify-between gap-3">
        <Tabs label="대상" items={USER_TYPE_TABS} value={userType} onChange={changeType} />
        <SearchInput label="이름 검색" placeholder="이름으로 검색" value={keyword} onChange={changeSearch} />
      </div>
      {list.isPending && <p role="status">위험 사용자 목록을 불러오고 있어요.</p>}
      {list.isError && (
        <div role="alert" className="card flex items-center justify-between gap-3">
          <p>{list.error.response?.data?.message || '위험 사용자 목록을 불러오지 못했어요.'}</p>
          <button type="button" className="btn-secondary" disabled={list.isFetching} onClick={() => list.refetch()}>다시 조회</button>
        </div>
      )}
      {list.isFetching && !list.isPending && <p role="status">위험 사용자 목록을 다시 불러오고 있어요.</p>}
      {list.isSuccess && (
        <>
          <p className="text-sm text-gray-600">총 {list.data.totalElements}건</p>
          <DataTable columns={columns} rows={list.data.riskUsers} rowKey="riskInfoId"
            emptyMessage={keyword.trim() ? '검색 결과가 없어요.' : '등록된 위험 사용자가 없어요.'} />
          {list.data.totalPages > 1 && (
            <Pagination page={list.data.page} totalPages={list.data.totalPages} onChange={setPage} />
          )}
        </>
      )}
    </>
  )
}

export default AdminRiskUserPage
