// [공통 컴포넌트] 탭 두 종류
// - Tabs: 같은 화면 안에서 내용만 바꾸는 탭 (items: [{ key, label }], value, onChange)
// - TabNav: 주소가 바뀌는 탭. 탭마다 하위 페이지로 이동한다 (items: [{ to, label }])
import { NavLink } from 'react-router'
import clsx from 'clsx'

// 탭 묶음 아래 회색 선은 inset 그림자로 그린다. 테두리로 그리면 선택 탭의 파란 밑줄과 겹치게 하느라 스크롤바가 생긴다
const listClassName = 'flex overflow-x-auto shadow-[inset_0_-1px_0_#e5e7eb]'

const tabClassName = (isActive) =>
  clsx(
    'border-b-2 px-4 py-2.5 text-[15px] whitespace-nowrap',
    isActive ? 'border-blue-600 font-bold text-blue-700' : 'border-transparent font-medium text-gray-600 hover:text-gray-900',
  )

export function Tabs({ items, value, onChange, label }) {
  return (
    <div role="tablist" aria-label={label} className={listClassName}>
      {items.map((item) => (
        <button
          key={item.key}
          type="button"
          role="tab"
          aria-selected={value === item.key}
          onClick={() => onChange(item.key)}
          className={tabClassName(value === item.key)}
        >
          {item.label}
        </button>
      ))}
    </div>
  )
}

export function TabNav({ items, label }) {
  return (
    <nav aria-label={label} className={listClassName}>
      {items.map((item) => (
        <NavLink key={item.to} to={item.to} className={({ isActive }) => tabClassName(isActive)}>
          {item.label}
        </NavLink>
      ))}
    </nav>
  )
}
