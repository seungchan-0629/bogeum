// USER_004, USER_017 (동의 항목을 각각 구분해 보여주고 전부 필수 동의)
import { useFormContext, useWatch } from 'react-hook-form'

// TODO: GET /api/v1/auth/terms?userType= (BE-007) 응답으로 교체
// - userType: 임대·임차인은 LANDLORD_TENANT, 중개사는 AGENT
// - 응답 필드: termsId, type(PRIVACY_COLLECT, THIRD_PARTY_PROVIDE), title, content, required
// - content에는 제공받는 자, 이용 목적, 제공 항목, 보유·이용 기간, 거부 권리와 불이익이 들어간다
const SAMPLE_TERMS = [
  { termsId: 1, type: 'PRIVACY_COLLECT', title: '개인정보 수집·이용 동의', content: '[약관 내용은 BE-007 응답으로 표시]', required: true },
  { termsId: 2, type: 'THIRD_PARTY_PROVIDE', title: '개인정보 제3자 제공 동의', content: '[약관 내용은 BE-007 응답으로 표시]', required: true },
]

// 약관 type → 회원가입 요청의 agreements 필드 이름 (BE-010, BE-011)
const AGREEMENT_FIELDS = {
  PRIVACY_COLLECT: 'privacyCollect',
  THIRD_PARTY_PROVIDE: 'thirdPartyProvide',
}

function AgreementFields() {
  const {
    register,
    setValue,
    formState: { errors },
  } = useFormContext()
  const agreements = useWatch({ name: 'agreements' })
  const fieldNames = SAMPLE_TERMS.map((terms) => AGREEMENT_FIELDS[terms.type])
  const isAllChecked = fieldNames.every((name) => agreements?.[name])

  const handleCheckAll = (event) => {
    fieldNames.forEach((name) => {
      setValue(`agreements.${name}`, event.target.checked, { shouldValidate: true })
    })
  }

  return (
    <fieldset className="flex flex-col gap-3">
      <legend className="mb-3 text-sm font-medium text-gray-700">약관 동의</legend>
      <label className="flex min-h-11 items-center gap-3 rounded-xl border border-gray-300 px-4 font-bold">
        <input type="checkbox" checked={isAllChecked} onChange={handleCheckAll} className="size-5 accent-blue-600" />
        전체 동의
      </label>
      {SAMPLE_TERMS.map((terms) => {
        const name = AGREEMENT_FIELDS[terms.type]
        const error = errors.agreements?.[name]
        return (
          <div key={terms.termsId} className="flex flex-col gap-1 px-1">
            <label className="flex min-h-11 items-center gap-3 text-[15px]">
              <input type="checkbox" className="size-5 accent-blue-600" {...register(`agreements.${name}`)} />
              <span>
                <span className="text-blue-700">(필수)</span> {terms.title}
              </span>
            </label>
            <details className="ml-8 text-sm text-gray-600">
              <summary className="cursor-pointer py-1">내용 보기</summary>
              <p className="mt-1 rounded-lg bg-gray-50 p-3 whitespace-pre-line">{terms.content}</p>
            </details>
            {error && <p className="ml-8 text-sm text-red-600">{error.message}</p>}
          </div>
        )
      })}
    </fieldset>
  )
}

export default AgreementFields
