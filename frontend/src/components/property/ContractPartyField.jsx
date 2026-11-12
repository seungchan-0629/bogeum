// [매물] 계약 임차인 고르기 — 계약서 올리기 화면에서 쓴다
// 후보가 몇 명뿐이라 칸을 누르면 전체 후보가 뜨고, 이름을 입력하면 맞는 사람만 남는다. 고르면 칸에 이름과 가린 이메일이 들어간다
// - candidates: 고를 수 있는 사람 [{ userId, name, maskedEmail }] (이 매물로 상담한 사람)
// 요구사항: PROPERTY_011
import { useState } from 'react'
import Icon from '../common/Icon.jsx'

function ContractPartyField({ id, label, candidates, selected, onSelect }) {
  const [query, setQuery] = useState('')
  const [isOpen, setIsOpen] = useState(false)
  const keyword = query.trim()
  const matches = keyword ? candidates.filter((candidate) => candidate.name.includes(keyword)) : candidates
  const listId = `${id}-options`

  const handleSelect = (candidate) => {
    onSelect(candidate)
    setQuery('')
    setIsOpen(false)
  }

  return (
    <div className="relative flex flex-col gap-1.5">
      <label htmlFor={id} className="text-sm font-medium text-gray-700">{label}</label>
      {selected ? (
        <div className="flex h-12 items-center gap-2 rounded-xl border border-gray-300 bg-white px-4">
          <span className="font-bold">{selected.name}</span>
          <span className="text-xs text-slate-400">{selected.maskedEmail}</span>
          <button
            id={id}
            type="button"
            onClick={() => onSelect(null)}
            aria-label={`${label} ${selected.name} 선택 취소`}
            className="ml-auto flex size-8 items-center justify-center rounded-lg text-gray-400 hover:bg-gray-100 hover:text-gray-700"
          >
            <Icon name="close" size={16} />
          </button>
        </div>
      ) : (
        <input
          id={id}
          role="combobox"
          aria-expanded={isOpen}
          aria-controls={listId}
          autoComplete="off"
          value={query}
          onChange={(event) => {
            setQuery(event.target.value)
            setIsOpen(true)
          }}
          onFocus={() => setIsOpen(true)}
          onBlur={() => setIsOpen(false)}
          placeholder="눌러서 고르거나 이름을 입력하세요"
          className="h-12 w-full rounded-xl border border-gray-300 bg-white px-4 text-[15px] focus:border-blue-500"
        />
      )}
      {!selected && isOpen && (
        <ul
          id={listId}
          className="absolute top-full right-0 left-0 z-10 mt-1 flex flex-col rounded-xl border border-gray-200 bg-white p-1.5 shadow-[0_12px_30px_rgba(15,23,42,0.16)]"
        >
          {matches.length > 0 ? (
            matches.map((candidate) => (
              <li key={candidate.userId}>
                {/* onMouseDown: 입력 칸의 blur보다 먼저 골라지게 한다 */}
                <button
                  type="button"
                  onMouseDown={(event) => event.preventDefault()}
                  onClick={() => handleSelect(candidate)}
                  className="flex w-full items-center gap-2 rounded-lg px-3 py-2.5 text-left hover:bg-blue-50"
                >
                  <span className="font-bold">{candidate.name}</span>
                  <span className="text-xs text-slate-400">{candidate.maskedEmail}</span>
                </button>
              </li>
            ))
          ) : (
            <li className="px-3 py-2.5 text-sm text-gray-500">
              {keyword ? '이 매물로 상담한 사람 중에 맞는 이름이 없어요.' : '이 매물로 상담한 사람이 없어요.'}
            </li>
          )}
        </ul>
      )}
    </div>
  )
}

export default ContractPartyField
