// [페이지] 마이페이지 (사용자) — /mypage
// 누가: 사용자. 중개사는 /agent/mypage 를 쓴다
// 요구사항: USER_009 (소셜 계정 연결), USER_010 (조회), USER_011 (수정), USER_012 (회원탈퇴 + 재확인)
// API:
// - 조회: GET /api/v1/users/me (BE-013)
// - 수정: PUT/PATCH /api/v1/users/me (BE-014) — 비밀번호, 전화번호, 주소
// - 탈퇴: DELETE /api/v1/users/me (BE-015)
// - 소셜 연결: POST /api/v1/users/me/social/{provider} (BE-006) — 재인증을 거친다
import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import PageHeader from '../../components/common/PageHeader.jsx'
import StatusBadge from '../../components/common/StatusBadge.jsx'
import ConfirmModal from '../../components/common/ConfirmModal.jsx'
import { Field, TextInput } from '../../components/common/FormControls.jsx'
import { useAuthStore } from '../../stores/authStore.js'
import { logout } from '../../utils/jwtUtil.js'
import { CONTRACT_ROLES, ROLE_LABELS } from '../../constants/auth.js'

// TODO: GET /api/v1/users/me (BE-013) 응답으로 교체 (name, phone, role, loginId, address, socialAccounts[])
const SAMPLE_PROFILE = {
  loginId: 'bogeum@example.com',
  phone: '010-1234-5678',
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
  const isTenant = user.contractRoles.includes(CONTRACT_ROLES.TENANT)
  const isLandlord = user.contractRoles.includes(CONTRACT_ROLES.LANDLORD)

  // TODO: PUT/PATCH /api/v1/users/me (BE-014) — 바뀐 항목만 보낸다
  const handleSave = (event) => {
    event.preventDefault()
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
      <div className="grid items-start gap-5 lg:grid-cols-2">
        <div className="flex flex-col gap-5">
          <section aria-labelledby="profile-title" className="card flex flex-col gap-3">
            <h2 id="profile-title" className="text-lg font-bold">회원 정보</h2>
            <dl className="flex flex-col gap-2.5">
              {[
                ['이름', user.name],
                ['역할', ROLE_LABELS[user.role]],
                ['아이디', SAMPLE_PROFILE.loginId],
                ['전화번호', SAMPLE_PROFILE.phone],
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
                <p className="text-sm text-gray-600">카드 등록됨</p>
              </div>
              <Link to="/mypage/payment-method" className="btn-secondary btn-sm">변경</Link>
            </section>
          )}
          {isLandlord && (
            <section aria-labelledby="settlement-title" className="card flex items-center justify-between gap-3">
              <div>
                <h2 id="settlement-title" className="text-lg font-bold">정산 계좌</h2>
                <p className="text-sm text-gray-600">국민은행 계좌 등록됨</p>
              </div>
              <Link to="/mypage/settlement-account" className="btn-secondary btn-sm">변경</Link>
            </section>
          )}
        </div>

        <div className="flex flex-col gap-3">
          <form onSubmit={handleSave} className="card flex flex-col gap-4">
            <h2 className="text-lg font-bold">정보 수정</h2>
            <Field label="새 비밀번호" htmlFor="mypage-password" hint="바꾸지 않으려면 비워 두세요.">
              <TextInput id="mypage-password" type="password" autoComplete="new-password" />
            </Field>
            <Field label="새 비밀번호 확인" htmlFor="mypage-password-confirm">
              <TextInput id="mypage-password-confirm" type="password" autoComplete="new-password" />
            </Field>
            <Field label="전화번호" htmlFor="mypage-phone">
              <TextInput id="mypage-phone" type="tel" defaultValue={SAMPLE_PROFILE.phone} />
            </Field>
            <Field label="주소" htmlFor="mypage-address">
              <TextInput id="mypage-address" defaultValue={SAMPLE_PROFILE.address} />
            </Field>
            <button type="submit" className="btn-primary">저장</button>
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
