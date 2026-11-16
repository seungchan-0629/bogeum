// RISK_003: 최종 확인과 실패 후 입력 보존을 한 모달에서 처리한다.
import { useEffect, useRef, useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation } from '@tanstack/react-query'
import { updateAdminRiskUser } from '../../../api/admin/index.js'
import { useAuthStore } from '../../../stores/authStore.js'
import { Field, TextInput, SelectInput, TextArea } from '../../../components/common/FormControls.jsx'
import { RISK_REASONS, createRiskUserUpdateSchema } from './riskUserForm.js'

function riskUserUpdateError(error) {
  const serverMessage = error?.response?.data?.message
  if (typeof serverMessage === 'string' && serverMessage.trim()) return serverMessage
  const messages = {
    400: '입력값과 변경 내용을 확인해 주세요.',
    401: '다시 로그인해 주세요.',
    403: '수정 권한 또는 보안 인증을 확인한 뒤 다시 시도해 주세요.',
    404: '수정 대상 위험 정보가 존재하지 않아요.',
    409: '이미 등록된 사유이거나 수정할 수 없는 상태예요.',
  }
  return messages[error?.response?.status] || '위험 정보 수정에 실패했어요. 입력 내용을 확인한 뒤 다시 시도해 주세요.'
}

function RiskUserEditModal({ riskUser, adminId, onClose, onUpdated }) {
  const dialogRef = useRef(null)
  const submitLock = useRef(false)
  // 대상·관리자별 모달이 새로 마운트될 때 초기화한다. blur는 편집으로 기록하지 않는다.
  const detailEdited = useRef(false)
  const [confirmation, setConfirmation] = useState(null)
  const { register, handleSubmit, formState: { errors } } = useForm({
    resolver: (values, context, options) => zodResolver(
      createRiskUserUpdateSchema(riskUser, () => detailEdited.current),
    )(values, context, options),
    defaultValues: { reasonType: riskUser.reasonType, detail: riskUser.detail ?? '', updateReason: '' },
  })
  const detailField = register('detail')
  const update = useMutation({
    mutationFn: updateAdminRiskUser,
    retry: false,
    onSuccess: (updated, values) => {
      const currentUser = useAuthStore.getState().user
      if (currentUser?.role === 'ADMIN' && currentUser.userId === adminId) onUpdated(updated, values)
    },
    onSettled: () => { submitLock.current = false },
  })

  useEffect(() => {
    const previousFocus = document.activeElement
    dialogRef.current?.querySelector('button')?.focus()
    return () => { if (previousFocus?.isConnected) previousFocus.focus() }
  }, [])

  useEffect(() => {
    // 확인 화면 전환으로 기존 버튼이 숨겨져도 포커스를 모달 안에 유지한다.
    dialogRef.current?.focus()
  }, [confirmation, update.isPending])

  const close = () => {
    if (!submitLock.current) onClose()
  }
  const handleKeyDown = (event) => {
    if (event.key === 'Escape') {
      event.preventDefault()
      event.stopPropagation()
      close()
    }
    if (event.key === 'Tab') {
      const controls = [...dialogRef.current.querySelectorAll('button, input, select, textarea')]
        .filter((element) => !element.matches(':disabled') && !element.closest('[hidden]'))
      const first = controls[0]
      const last = controls[controls.length - 1]
      if (event.shiftKey && (document.activeElement === first || document.activeElement === dialogRef.current)) {
        event.preventDefault()
        last?.focus()
      } else if (!event.shiftKey && (document.activeElement === last || document.activeElement === dialogRef.current)) {
        event.preventDefault()
        first?.focus()
      }
    }
  }
  const review = (values) => {
    if (submitLock.current) return
    update.reset()
    setConfirmation(values)
  }
  const submit = () => {
    const currentUser = useAuthStore.getState().user
    if (!confirmation || submitLock.current || currentUser?.role !== 'ADMIN' || currentUser.userId !== adminId) return
    // React 렌더 전 연속 클릭도 차단한다. 실패 시 폼 값은 그대로 남긴다.
    submitLock.current = true
    setConfirmation(null)
    update.mutate({ riskInfoId: riskUser.riskInfoId, ...confirmation })
  }

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center bg-slate-900/45 px-4" onClick={close}>
      <div ref={dialogRef} tabIndex={-1} role="dialog" aria-modal="true" aria-labelledby="risk-user-edit-title"
        aria-describedby="risk-user-edit-description" onKeyDown={handleKeyDown} onClick={(event) => event.stopPropagation()}
        className="max-h-[90vh] w-full max-w-xl overflow-y-auto rounded-2xl bg-white p-6 shadow-xl">
        <div className="mb-4 flex items-center justify-between gap-3">
          <h2 id="risk-user-edit-title" className="text-lg font-bold">위험 사용자 정보 수정</h2>
          <button type="button" className="btn-secondary" disabled={update.isPending} onClick={close}>취소</button>
        </div>
        <p id="risk-user-edit-description" className="mb-4 text-sm text-gray-600">저장 전 최종 내용을 확인합니다. 상세 내용을 비우면 기존 내용이 삭제됩니다.</p>
        <div className="mb-4 grid gap-3 sm:grid-cols-2">
          <Field label="이름" htmlFor="risk-edit-name"><TextInput id="risk-edit-name" value={riskUser.name} readOnly /></Field>
          <Field label="회원 ID" htmlFor="risk-edit-user-id"><TextInput id="risk-edit-user-id" value={riskUser.userId} readOnly /></Field>
        </div>
        <form aria-label="위험 사용자 정보 수정" onSubmit={(event) => handleSubmit(review)(event)} noValidate hidden={!!confirmation}>
          <fieldset disabled={update.isPending} className="flex min-w-0 flex-col gap-4">
            <Field label="위험 사유" htmlFor="risk-edit-reason">
              <SelectInput id="risk-edit-reason" options={[{ value: '', label: '사유를 선택해 주세요' }, ...Object.entries(RISK_REASONS).map(([value, reason]) => ({ value, label: reason.label }))]}
                aria-invalid={!!errors.reasonType} aria-describedby={errors.reasonType ? 'risk-edit-reason-error' : undefined} {...register('reasonType')} />
              {errors.reasonType && <p id="risk-edit-reason-error" role="alert" className="text-sm text-red-600">{errors.reasonType.message}</p>}
            </Field>
            <Field label="상세 내용 (선택)" htmlFor="risk-edit-detail" hint="공백과 줄바꿈을 포함해 최대 65,535바이트까지 입력할 수 있어요.">
              <TextArea id="risk-edit-detail" aria-invalid={!!errors.detail} aria-describedby={errors.detail ? 'risk-edit-detail-error' : undefined} {...detailField}
                onChange={(event) => { detailEdited.current = true; detailField.onChange(event) }} />
              {errors.detail && <p id="risk-edit-detail-error" role="alert" className="text-sm text-red-600">{errors.detail.message}</p>}
            </Field>
            <Field label="수정 사유" htmlFor="risk-edit-update-reason" hint="수정 사유를 255자 이하로 입력해 주세요.">
              <TextArea id="risk-edit-update-reason" aria-invalid={!!errors.updateReason} aria-describedby={errors.updateReason ? 'risk-edit-update-reason-error' : undefined} {...register('updateReason')} />
              {errors.updateReason && <p id="risk-edit-update-reason-error" role="alert" className="text-sm text-red-600">{errors.updateReason.message}</p>}
            </Field>
            <button type="submit" className="btn-primary self-start">{update.isPending ? '저장 중…' : '수정 내용 확인'}</button>
          </fieldset>
        </form>
        {confirmation && (
          <div className="flex flex-col gap-3" aria-label="최종 수정 내용">
            <h3 className="font-bold">수정 내용을 확인해 주세요.</h3>
            <p>위험 사유: <strong>{RISK_REASONS[confirmation.reasonType].label}</strong></p>
            <p className="whitespace-pre-wrap break-words">상세 내용: {confirmation.detail === null ? '내용 없음' : confirmation.detail === '' ? '내용 삭제' : confirmation.detail}</p>
            <p className="whitespace-pre-wrap break-words">수정 사유: {confirmation.updateReason}</p>
            <div className="flex gap-2">
              <button type="button" className="btn-secondary" onClick={() => setConfirmation(null)}>계속 편집</button>
              <button type="button" className="btn-primary" onClick={submit}>저장</button>
            </div>
          </div>
        )}
        {update.isPending && <p role="status" className="mt-3">위험 정보를 저장하고 있어요.</p>}
        {update.isError && <p role="alert" className="mt-3 text-red-600">{riskUserUpdateError(update.error)}</p>}
      </div>
    </div>
  )
}

export default RiskUserEditModal
