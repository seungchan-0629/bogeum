// [페이지] 클레임 접수 — /my-home/claim (임차인), /care/claim (임대인)
// 로그인한 역할에 따라 대상과 고를 수 있는 사유가 바뀐다
// - 임차인 → 임대인에 대한 클레임: AS 요청 미응답, 실제 매물과 다름
// - 임대인 → 임차인에 대한 클레임: 월세 미납, 악성 세입자
// 요구사항: CLAIM_001
// API: POST /api/v1/claims (BE-073) — 매물, 사유, 내용, 첨부
import { useState } from 'react'
import clsx from 'clsx'
import FileDropzone from '../../components/common/FileDropzone.jsx'
import { Field, SelectInput, TextArea } from '../../components/common/FormControls.jsx'
import { useAuthStore } from '../../stores/authStore.js'
import { ROLES } from '../../constants/auth.js'
import { CLAIM_REASONS_BY_ROLE } from '../../constants/residence.js'

// TODO: 내 계약 매물 목록 API가 명세에 없어 백엔드와 협의 필요. 지금은 예시 매물
const SAMPLE_PROPERTY_OPTIONS = [
  { value: '1', label: '역삼동 오피스텔' },
  { value: '2', label: '공덕동 아파트' },
]

function ClaimPage() {
  const user = useAuthStore((state) => state.user)
  const reasons = CLAIM_REASONS_BY_ROLE[user.role]
  const [reason, setReason] = useState(reasons[0])
  const targetLabel = user.role === ROLES.TENANT ? '임대인' : '임차인'

  // TODO: POST /api/v1/claims (BE-073) — 요청: { propertyId, reason, content, files }. 사유 값 형식은 백엔드와 협의
  const handleSubmit = (event) => {
    event.preventDefault()
  }

  return (
    <form onSubmit={handleSubmit} className="card flex max-w-2xl flex-col gap-5">
      <h2 className="text-lg font-bold">{targetLabel}에 대한 클레임 접수</h2>
      <Field label="매물" htmlFor="claim-property">
        <SelectInput id="claim-property" options={SAMPLE_PROPERTY_OPTIONS} />
      </Field>
      <fieldset className="flex flex-col gap-2">
        <legend className="mb-2 text-sm font-medium text-gray-700">사유</legend>
        <div className="flex flex-wrap gap-2">
          {reasons.map((item) => (
            <button
              key={item}
              type="button"
              aria-pressed={reason === item}
              onClick={() => setReason(item)}
              className={clsx('rounded-full px-4 py-2 text-sm', reason === item ? 'bg-blue-600 font-bold text-white' : 'border border-gray-300 bg-white')}
            >
              {item}
            </button>
          ))}
        </div>
      </fieldset>
      <Field label="내용" htmlFor="claim-content">
        <TextArea id="claim-content" placeholder="어떤 일이 있었는지 적어 주세요." />
      </Field>
      <FileDropzone title="증빙 자료 첨부 (선택)" accept="image/*,application/pdf" multiple />
      <button type="submit" className="btn-primary self-start">접수하기</button>
    </form>
  )
}

export default ClaimPage
