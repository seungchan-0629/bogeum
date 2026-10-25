// [공통 컴포넌트] 확인 창 (모달)
// 화면 전체를 어둡게 덮고 가운데에 뜬다. 회원탈퇴·매물 삭제처럼 되돌릴 수 없는 동작 전에 쓴다
// - children: 설명 아래에 넣을 입력 칸 (예: 비밀번호 확인)
// - Esc 키나 [취소], 바깥 어두운 곳을 누르면 닫힌다
import { useEffect } from 'react'

function ConfirmModal({ title, description, confirmLabel, onConfirm, onClose, children }) {
  useEffect(() => {
    const handleKeyDown = (event) => {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [onClose])

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center bg-slate-900/45 px-4" onClick={onClose}>
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="confirm-modal-title"
        onClick={(event) => event.stopPropagation()}
        className="flex w-full max-w-sm flex-col gap-4 rounded-2xl bg-white p-6 shadow-[0_20px_50px_rgba(15,23,42,0.25)]"
      >
        <h2 id="confirm-modal-title" className="text-lg font-bold">{title}</h2>
        {description && <p className="text-sm text-gray-600">{description}</p>}
        {children}
        <div className="flex gap-2">
          <button type="button" autoFocus onClick={onClose} className="h-11 flex-1 rounded-xl border border-gray-300 font-medium hover:bg-gray-50">
            취소
          </button>
          <button type="button" onClick={onConfirm} className="h-11 flex-1 rounded-xl bg-red-600 font-bold text-white hover:bg-red-700">
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}

export default ConfirmModal
