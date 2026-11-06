// USER_003 (회원가입 시 본인 인증)
import { useEffect, useState } from 'react'
import { useFormContext, useWatch } from 'react-hook-form'
import { publicApi } from '../../utils/jwtUtil.js'

function IdentityVerificationField() {
  const {
    formState: { errors }, setValue,
  } = useFormContext()
  const phone = useWatch({ name: 'phone' }) || ''
  const [code, setCode] = useState('')
  const [verification, setVerification] = useState(null)
  const [tokenExpiresAt, setTokenExpiresAt] = useState(null)
  const [isSending, setIsSending] = useState(false)
  const [isConfirming, setIsConfirming] = useState(false)
  const [errorMessage, setErrorMessage] = useState('')
  const [now, setNow] = useState(Date.now)
  const isVerified = Boolean(useWatch({ name: 'verificationToken' }))
  const isBusy = isSending || isConfirming
  const normalizedPhone = phone.replace(/[\s-]/g, '')
  const remainingSeconds = verification && !verification.isExpired ? Math.max(0, Math.ceil((Date.parse(verification.expiresAt) - now) / 1000)) : 0
  const resendSeconds = verification ? Math.max(0, Math.ceil((Date.parse(verification.resendAvailableAt) - now) / 1000)) : 0

  useEffect(() => {
    if (!verification && !isVerified) return
    const timer = window.setInterval(() => {
      const currentTime = Date.now()
      setNow(currentTime)
      if (isVerified && tokenExpiresAt && currentTime >= Date.parse(tokenExpiresAt)) {
        setValue('verificationToken', '', { shouldValidate: true })
        setTokenExpiresAt(null)
        setVerification(null)
        setCode('')
        setErrorMessage('인증 완료 후 10분이 지났어요. 다시 인증해 주세요.')
      }
    }, 1000)
    return () => window.clearInterval(timer)
  }, [verification, isVerified, tokenExpiresAt, setValue])

  const handleRequest = async () => {
    setErrorMessage('')
    setIsSending(true)
    try {
      const { data } = await publicApi.post('/auth/identity-verification', { phone: normalizedPhone })
      setVerification(data)
      setCode('')
      setNow(Date.parse(data.expiresAt) - 180_000)
      setValue('phone', normalizedPhone, { shouldValidate: true })
      setValue('verificationToken', '')
    } catch (error) {
      setErrorMessage(error.response?.data?.message || '인증 문자를 요청하지 못했어요. 다시 시도해 주세요.')
    } finally {
      setIsSending(false)
    }
  }

  const handleConfirm = async () => {
    setErrorMessage('')
    setIsConfirming(true)
    try {
      const { data } = await publicApi.post('/auth/identity-verification/confirm', {
        verificationId: verification.verificationId, code,
      })
      if (data.verified && data.verificationToken) {
        setValue('verificationToken', data.verificationToken, { shouldValidate: true })
        setTokenExpiresAt(data.expiresAt)
      }
    } catch (error) {
      setErrorMessage(error.response?.data?.message || '인증번호를 확인하지 못했어요. 다시 시도해 주세요.')
      if (['PHONE_VERIFICATION_EXPIRED', 'PHONE_ATTEMPTS_EXCEEDED', 'PHONE_VERIFICATION_INVALID'].includes(error.response?.data?.code)) {
        setVerification((current) => ({ ...current, isExpired: true }))
      }
    } finally {
      setIsConfirming(false)
    }
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
            setValue('phone', event.target.value, { shouldValidate: true })
            setValue('verificationToken', '')
            setVerification(null)
            setTokenExpiresAt(null)
            setCode('')
            setErrorMessage('')
          }}
          disabled={isBusy}
          className="h-12 min-w-0 flex-1 rounded-xl border border-gray-300 px-4 text-base disabled:bg-gray-100"
        />
        <button
          type="button"
          onClick={handleRequest}
          disabled={!/^010\d{8}$/.test(normalizedPhone) || isVerified || isBusy || resendSeconds > 0}
          className="h-12 shrink-0 rounded-xl border border-blue-600 px-4 text-sm font-bold text-blue-700 hover:bg-blue-50 disabled:border-gray-300 disabled:text-gray-400 disabled:hover:bg-transparent"
        >
          {isSending ? '발송 중…' : isVerified ? '인증 완료' : resendSeconds > 0 ? `${resendSeconds}초 후 재요청` : verification ? '다시 요청' : '인증 요청'}
        </button>
      </div>
      {verification && !isVerified && (
        <div className="flex gap-2">
          <label htmlFor="verify-code" className="sr-only">인증번호</label>
          <input
            id="verify-code"
            type="text"
            inputMode="numeric"
            autoComplete="one-time-code"
            placeholder="인증번호"
            maxLength={6}
            value={code}
            onChange={(event) => setCode(event.target.value.replace(/\D/g, '').slice(0, 6))}
            disabled={isBusy || remainingSeconds === 0}
            className="h-12 min-w-0 flex-1 rounded-xl border border-gray-300 px-4 text-base"
          />
          <button
            type="button"
            onClick={handleConfirm}
            disabled={!/^\d{6}$/.test(code) || isBusy || remainingSeconds === 0}
            className="h-12 shrink-0 rounded-xl bg-blue-600 px-4 text-sm font-bold text-white hover:bg-blue-700 disabled:opacity-50"
          >
            {isConfirming ? '확인 중…' : '확인'}
          </button>
        </div>
      )}
      {verification && !isVerified && (
        <p className="text-sm text-gray-600" role="timer" aria-label="인증번호 남은 시간">
          {remainingSeconds > 0
            ? `남은 시간 ${Math.floor(remainingSeconds / 60)}:${String(remainingSeconds % 60).padStart(2, '0')}`
            : '인증번호를 다시 요청해 주세요.'}
        </p>
      )}
      {isVerified && <p className="text-sm font-medium text-emerald-700">본인 인증이 완료되었어요.</p>}
      {(errorMessage || errors.phone || (errors.verificationToken && !isVerified)) && (
        <p role="alert" className="text-sm text-red-600">{errorMessage || errors.phone?.message || errors.verificationToken?.message}</p>
      )}
    </fieldset>
  )
}

export default IdentityVerificationField
