// [공통 컴포넌트] 입력 칸 모음 (로그인·회원가입 외의 화면용)
// - Field: 라벨 + 입력 칸 + 도움말을 묶는다
// - TextInput / SelectInput / TextArea: 같은 모양의 입력 칸
// - DateInput: 날짜 입력 칸. 비어 있을 때는 브라우저 기본 '연도. 월. 일.' 대신 '년 월 일'을 보여준다
//   size="sm"이면 낮은 칸(목록 위 필터 등), fullWidth={false}면 내용 폭만큼만 차지한다
// 지금은 화면만 있는 상태라 값 검증이 없다. API 연결할 때 React Hook Form + zod를 붙인다 (auth/TextField.jsx 참고)
import { useEffect, useRef, useState } from 'react'
import clsx from 'clsx'

const controlClassName = 'rounded-xl border border-gray-300 bg-white px-4 focus:border-blue-500'
const SIZE_CLASSES = { md: 'h-12 text-[15px]', sm: 'h-10 text-sm' }

export function Field({ label, htmlFor, hint, children }) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={htmlFor} className="text-sm font-medium text-gray-700">
        {label}
      </label>
      {children}
      {hint && <p className="text-sm text-gray-500">{hint}</p>}
    </div>
  )
}

export function TextInput({ size = 'md', fullWidth = true, ...props }) {
  return <input className={clsx(controlClassName, SIZE_CLASSES[size], fullWidth && 'w-full')} {...props} />
}

// 브라우저 날짜 칸은 빈칸 문구를 바꿀 수 없어서, 비어 있을 때는 글자 칸으로 두고 누르면 날짜 칸으로 바꾼다
export function DateInput({ placeholder = '년 월 일', onFocus, onBlur, ...props }) {
  const inputRef = useRef(null)
  const [isDateType, setIsDateType] = useState(false)

  // 날짜 칸으로 바뀌면 달력을 바로 연다. 키보드(Tab)로 들어오면 브라우저가 막으므로 그때는 직접 입력한다
  useEffect(() => {
    if (!isDateType || document.activeElement !== inputRef.current) return
    try {
      inputRef.current.showPicker()
    } catch {
      // 달력을 열 수 없으면 날짜 칸에 직접 입력하면 된다
    }
  }, [isDateType])

  return (
    <TextInput
      ref={inputRef}
      type={isDateType ? 'date' : 'text'}
      placeholder={placeholder}
      onFocus={(event) => {
        setIsDateType(true)
        onFocus?.(event)
      }}
      onBlur={(event) => {
        if (!event.target.value) setIsDateType(false)
        onBlur?.(event)
      }}
      {...props}
    />
  )
}

export function SelectInput({ options, size = 'md', fullWidth = true, ...props }) {
  return (
    <select className={clsx(controlClassName, SIZE_CLASSES[size], fullWidth && 'w-full')} {...props}>
      {options.map((option) => (
        <option key={option.value} value={option.value}>
          {option.label}
        </option>
      ))}
    </select>
  )
}

export function TextArea(props) {
  return <textarea rows={4} className={clsx(controlClassName, 'w-full py-3 text-[15px]')} {...props} />
}
