// [페이지] 마이페이지 (사용자) — /mypage
// 누가: 사용자. 중개사는 /agent/mypage 를 쓴다
// 요구사항: USER_009 (소셜 계정 연결), USER_010 (조회), USER_011 (수정), USER_012 (회원탈퇴 + 재확인)
// API:
// - 조회: GET /api/v1/users/me (BE-013)
// - 수정: PUT/PATCH /api/v1/users/me (BE-014) — 비밀번호, 전화번호, 주소
// - 탈퇴: DELETE /api/v1/users/me (BE-015)
// - 소셜 연결: POST /api/v1/users/me/social/{provider} (BE-006) — 재인증을 거친다
import { useState } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate } from 'react-router'
import PageHeader from '../../components/common/PageHeader.jsx'
import StatusBadge from '../../components/common/StatusBadge.jsx'
import ConfirmModal from '../../components/common/ConfirmModal.jsx'
import { Field, TextInput } from '../../components/common/FormControls.jsx'
import { useAuthStore } from '../../stores/authStore.js'
import { api, logout } from '../../utils/jwtUtil.js'
import { CONTRACT_ROLES, ROLE_LABELS } from '../../constants/auth.js'

// 주소와 소셜 연결은 기존 표시를 유지한다.
const SAMPLE_PROFILE = {
  address: '서울 강남구 역삼동 123-4',
  connectedProviders: ['NAVER'],
}

const SOCIAL_PROVIDERS = [
  { key: 'NAVER', label: '네이버' },
  { key: 'KAKAO', label: '카카오' },
  { key: 'GOOGLE', label: '구글' },
]

function MyPage() {
  const user = useAuthStore((state) => state.user)
  const navigate = useNavigate()
  const [isWithdrawOpen, setIsWithdrawOpen] = useState(false)
  const isTenant = user?.contractRoles?.includes(CONTRACT_ROLES.TENANT)
  const isLandlord = user?.contractRoles?.includes(CONTRACT_ROLES.LANDLORD)

  const queryClient = useQueryClient()
  const userId = user?.userId
  const queryKey = ['user-profile', userId]
  const { data: profile, isPending, isFetching, error, refetch } = useQuery({
    queryKey,
    queryFn: async ({ signal }) => (await api.get('/v1/users/me', { signal })).data,
    enabled: Boolean(userId),
    gcTime: 0,
    retry: false,
    refetchOnWindowFocus: false,
  })
  const [phone, setPhone] = useState(null)
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [passwordConfirm, setPasswordConfirm] = useState('')
  const [isSaving, setIsSaving] = useState(false)
  const [saveError, setSaveError] = useState('')
  const [saveMessage, setSaveMessage] = useState('')
  const isFormDisabled = !profile || isPending || isFetching || Boolean(error) || isSaving

  const handleSave = async (event) => {
    event.preventDefault()
    if (isFormDisabled) return
    setSaveError('')
    setSaveMessage('')
    const nextPhone = (phone ?? profile.phone ?? '').trim()
    if (!nextPhone || nextPhone.length > 20) {
      setSaveError('전화번호를 20자 이하로 입력해 주세요.')
      return
    }
    if (newPassword !== passwordConfirm) {
      setSaveError('새 비밀번호와 확인값이 일치하지 않습니다.')
      return
    }
    if (newPassword && (!newPassword.trim() || new TextEncoder().encode(newPassword).length > 72)) {
      setSaveError('새 비밀번호는 공백만 사용할 수 없고 UTF-8 기준 72바이트 이하여야 합니다.')
      return
    }
    if (newPassword && !currentPassword.trim()) {
      setSaveError('비밀번호를 변경하려면 현재 비밀번호를 입력해 주세요.')
      return
    }
    const changes = {}
    if (nextPhone !== profile.phone) changes.phone = nextPhone
    if (newPassword) Object.assign(changes, { currentPassword, newPassword, passwordConfirm })
    if (!Object.keys(changes).length) {
      setSaveMessage('변경된 정보가 없습니다.')
      return
    }
    setIsSaving(true)
    try {
      await queryClient.cancelQueries({ queryKey })
      const { data } = await api.patch('/v1/users/me', changes)
      if (useAuthStore.getState().user?.userId !== userId) return
      queryClient.setQueryData(queryKey, data)
      setPhone(null)
      setCurrentPassword('')
      setNewPassword('')
      setPasswordConfirm('')
      setSaveMessage('회원 정보가 저장되었습니다.')
    } catch (saveFailure) {
      setSaveError(saveFailure.response?.data?.message || '저장하지 못했습니다. 잠시 후 다시 시도해 주세요.')
    } finally {
      setIsSaving(false)
    }
  }

  // TODO: 각 제공자 인증 창 → 받은 코드로 POST /api/v1/users/me/social/{provider} (BE-006)
  const handleConnect = () => {}

  // TODO: DELETE /api/v1/users/me (BE-015, 확인용 비밀번호 포함 여부는 백엔드와 협의) 성공 후 로그아웃
  const handleWithdraw = async () => {
    try {
      await logout()
    } catch {
      window.alert('서버 로그아웃을 완료하지 못했습니다. 다시 로그인한 후 로그아웃해 주세요.')
      return
    }
    navigate('/')
  }

  return (
    <div className="page-container">
      <PageHeader title="마이페이지" />
      {isPending && <p role="status">회원 정보를 불러오고 있습니다.</p>}
      {error && (
        <div role="alert">
          <p className="text-red-600">{error.response?.data?.message || '회원 정보를 불러오지 못했습니다.'}</p>
          <button type="button" className="btn-secondary" disabled={isFetching} onClick={() => refetch()}>다시 불러오기</button>
        </div>
      )}
      <div className="grid items-start gap-5 lg:grid-cols-2">
        <div className="flex flex-col gap-5">
          <section aria-labelledby="profile-title" className="card flex flex-col gap-3">
            <h2 id="profile-title" className="text-lg font-bold">회원 정보</h2>
            <dl className="flex flex-col gap-2.5">
              {[
                ['이름', profile?.name ?? '—'],
                ['역할', ROLE_LABELS[profile?.role] ?? '—'],
                ['아이디', profile?.email ?? '—'],
                ['전화번호', profile?.phone ?? '—'],
                ['주소', SAMPLE_PROFILE.address],
              ].map(([label, value]) => (
                <div key={label} className="flex justify-between gap-4">
                  <dt className="text-gray-600">{label}</dt>
                  <dd className="font-bold">{value}</dd>
                </div>
              ))}
            </dl>
          </section>

          <section aria-labelledby="social-title" className="card flex flex-col gap-3">
            <h2 id="social-title" className="text-lg font-bold">소셜 계정 연결</h2>
            {SOCIAL_PROVIDERS.map((provider) => (
              <div key={provider.key} className="flex items-center justify-between">
                <span>{provider.label}</span>
                {SAMPLE_PROFILE.connectedProviders.includes(provider.key) ? (
                  <StatusBadge tone="green">연결됨</StatusBadge>
                ) : (
                  <button type="button" onClick={() => handleConnect(provider.key)} className="btn-secondary btn-sm">연결하기</button>
                )}
              </div>
            ))}
          </section>

          {/* 임차인으로 계약했으면 월세 결제 수단, 임대인으로 계약했으면 월세 받을 정산 계좌. 둘 다면 둘 다 보여준다 */}
          {isTenant && (
            <section aria-labelledby="payment-title" className="card flex items-center justify-between gap-3">
              <div>
                <h2 id="payment-title" className="text-lg font-bold">월세 결제 수단</h2>
                <p className="text-sm text-gray-600">오픈뱅킹 출금 계좌 확인·변경</p>
              </div>
              <Link to="/mypage/payment-method" className="btn-secondary btn-sm">변경</Link>
            </section>
          )}
          {isLandlord && (
            <section aria-labelledby="settlement-title" className="card flex items-center justify-between gap-3">
              <div>
                <h2 id="settlement-title" className="text-lg font-bold">정산 계좌</h2>
                <p className="text-sm text-gray-600">월세를 받을 정산 계좌 확인·변경</p>
              </div>
              <Link to="/mypage/settlement-account" className="btn-secondary btn-sm">변경</Link>
            </section>
          )}
        </div>

        <div className="flex flex-col gap-3">
          <form onSubmit={handleSave} className="card flex flex-col gap-4">
            <h2 className="text-lg font-bold">정보 수정</h2>
            <Field label="현재 비밀번호" htmlFor="mypage-current-password" hint="비밀번호를 변경할 때만 입력해 주세요.">
              <TextInput id="mypage-current-password" type="password" autoComplete="current-password" value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} disabled={isFormDisabled} />
            </Field>
            <Field label="새 비밀번호" htmlFor="mypage-password" hint="바꾸지 않으려면 비워 두세요.">
              <TextInput id="mypage-password" type="password" autoComplete="new-password" value={newPassword} onChange={(event) => setNewPassword(event.target.value)} disabled={isFormDisabled} />
            </Field>
            <Field label="새 비밀번호 확인" htmlFor="mypage-password-confirm">
              <TextInput id="mypage-password-confirm" type="password" autoComplete="new-password" value={passwordConfirm} onChange={(event) => setPasswordConfirm(event.target.value)} disabled={isFormDisabled} />
            </Field>
            <Field label="전화번호" htmlFor="mypage-phone">
              <TextInput id="mypage-phone" type="tel" autoComplete="tel" maxLength={20} required value={phone ?? profile?.phone ?? ''} onChange={(event) => setPhone(event.target.value)} disabled={isFormDisabled} />
            </Field>
            <Field label="주소" htmlFor="mypage-address">
              <TextInput id="mypage-address" defaultValue={SAMPLE_PROFILE.address} />
            </Field>
            {saveError && <p role="alert" className="text-red-600">{saveError}</p>}
            {saveMessage && <p role="status">{saveMessage}</p>}
            <button type="submit" className="btn-primary" disabled={isFormDisabled}>{isSaving ? '저장 중…' : '저장'}</button>
          </form>
          <button type="button" onClick={() => setIsWithdrawOpen(true)} className="self-end text-sm text-gray-600 underline hover:text-gray-900">
            회원 탈퇴
          </button>
        </div>
      </div>

      {/* USER_012: '정말 탈퇴하시겠습니까?' 재확인 */}
      {isWithdrawOpen && (
        <ConfirmModal
          title="정말 탈퇴하시겠습니까?"
          description="탈퇴하면 계약·월세 기록을 다시 볼 수 없어요."
          confirmLabel="탈퇴하기"
          onConfirm={handleWithdraw}
          onClose={() => setIsWithdrawOpen(false)}
        >
          <Field label="확인을 위해 비밀번호 입력" htmlFor="withdraw-password">
            <TextInput id="withdraw-password" type="password" autoComplete="current-password" />
          </Field>
        </ConfirmModal>
      )}
    </div>
  )
}

export default MyPage
