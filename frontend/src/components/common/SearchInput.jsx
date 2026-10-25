// [공통 컴포넌트] 검색 칸 (돋보기 아이콘 + 입력) — 관리자 목록 화면 오른쪽 위에서 쓴다
// - label: 화면 낭독기용 이름, placeholder: 무엇으로 검색하는지
import { useId } from 'react'
import Icon from './Icon.jsx'

function SearchInput({ label, placeholder, onChange }) {
  const inputId = useId()

  return (
    <div className="relative w-full sm:w-80">
      <label htmlFor={inputId} className="sr-only">{label}</label>
      <Icon name="search" size={16} className="pointer-events-none absolute top-1/2 left-3.5 -translate-y-1/2 text-gray-500" />
      <input id={inputId} type="search" placeholder={placeholder} onChange={onChange} className="h-10 w-full rounded-xl border border-gray-300 bg-white pr-4 pl-10 text-sm" />
    </div>
  )
}

export default SearchInput
