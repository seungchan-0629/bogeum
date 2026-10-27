// USER_003 (회원가입 시 본인 인증)
import { useState } from 'react'
import { useFormContext, useWatch } from 'react-hook-form'

// 본인 인증 수단(휴대폰, PASS 등)이 미정이라 휴대폰 인증번호 방식으로 화면을 구성했다 (BE-008 비고)
function IdentityVerificationField() {
  const {
    formState: { errors },setValue
  } = useFormContext()
  const [phone, setPhone] = useState('')
  const [code, setCode] = useState('')
  const [isCodeSent, setIsCodeSent] = useState(false)
  const isVerified = Boolean(useWatch({ name: 'verificationToken' }))

  // TODO: POST /api/v1/auth/identity-verification (BE-008) 호출
  // - 요청: { phone } / 응답: { verificationId } → 상태에 저장해 확인 요청에 사용
  const handleRequest = () => {
    setIsCodeSent(true)
  }

  // TODO: POST /api/v1/auth/identity-verification/confirm (BE-009) 호출
  // - 요청: { verificationId, code } / 응답: { verified, verificationToken }
  // - verified가 true면 setValue('verificationToken', verificationToken, { shouldValidate: true })
  const handleConfirm = () =>
  {
    //임시로 집어넣은 인증 토큰
    setValue('verificationToken', '1111', { shouldValidate: true });
    console.log("asdfasdf");
  }

  return (
    <fieldset className="flex flex-col gap-3">
      <legend className="mb-1.5 text-sm font-medium text-gray-700">본인 인증</legend>
      <div className="flex gap-2">
        <label htmlFor="verify-phone" className="sr-only">휴대폰 번호</label>
        <input
          id="verify-phone"
          type="tel"
          inputMode="numeric"
          autoComplete="tel"
          placeholder="휴대폰 번호"
          value={phone}
          onChange={(event) => {
            setPhone(event.target.value)
            setValue('phone', event.target.value, { shouldValidate: true })
          }}
          disabled={isVerified}
          className="h-12 min-w-0 flex-1 rounded-xl border border-gray-300 px-4 text-base disabled:bg-gray-100"
        />
        <button
          type="button"
          onClick={handleRequest}
          disabled={!phone || isVerified}
          className="h-12 shrink-0 rounded-xl border border-blue-600 px-4 text-sm font-bold text-blue-700 hover:bg-blue-50 disabled:border-gray-300 disabled:text-gray-400 disabled:hover:bg-transparent"
        >
          {isCodeSent ? '다시 요청' : '인증 요청'}
        </button>
      </div>
      {isCodeSent && !isVerified && (
        <div className="flex gap-2">
          <label htmlFor="verify-code" className="sr-only">인증번호</label>
          <input
            id="verify-code"
            type="text"
            inputMode="numeric"
            autoComplete="one-time-code"
            placeholder="인증번호"
            value={code}
            onChange={(event) => setCode(event.target.value)}
            className="h-12 min-w-0 flex-1 rounded-xl border border-gray-300 px-4 text-base"
          />
          <button
            type="button"
            onClick={handleConfirm}
            disabled={!code}
            className="h-12 shrink-0 rounded-xl bg-blue-600 px-4 text-sm font-bold text-white hover:bg-blue-700 disabled:opacity-50"
          >
            확인
          </button>
        </div>
      )}
      {isVerified && <p className="text-sm font-medium text-emerald-700">본인 인증이 완료되었어요.</p>}
      {(errors.phone || (errors.verificationToken && !isVerified)) && (
        <p className="text-sm text-red-600">{errors.phone?.message || errors.verificationToken.message}</p>
      )}
    </fieldset>
  )
}

export default IdentityVerificationField
