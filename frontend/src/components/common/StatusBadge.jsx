// [공통 컴포넌트] 상태 표시 뱃지 (예: 처리중, 계약 완료, 연체)
// tone으로 색을 고른다. 빨간색(red)은 실패·연체처럼 조치가 필요한 상태에만 쓴다
import clsx from 'clsx'

const TONE_CLASSES = {
  gray: 'bg-gray-100 text-gray-700',
  blue: 'bg-blue-100 text-blue-800',
  green: 'bg-emerald-100 text-emerald-800',
  amber: 'bg-amber-100 text-amber-800',
  red: 'bg-red-100 text-red-800',
}

function StatusBadge({ tone = 'gray', children }) {
  return (
    <span className={clsx('inline-block shrink-0 rounded-full px-2.5 py-0.5 text-xs font-bold whitespace-nowrap', TONE_CLASSES[tone])}>
      {children}
    </span>
  )
}

export default StatusBadge
