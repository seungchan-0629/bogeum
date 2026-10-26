// [공통 컴포넌트] 선 아이콘 모음
// 사용법: <Icon name="bell" size={22} /> — name은 아래 PATHS의 키
// 장식용이라 화면 낭독기에는 읽히지 않게 aria-hidden을 붙인다. 버튼에 쓸 때는 버튼에 aria-label을 단다

const PATHS = {
  bell: ['M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9', 'M10.3 21a1.94 1.94 0 0 0 3.4 0'],
  heart: ['M19 14c1.5-1.5 3-3.2 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.8 0-3 .5-4.5 2-1.5-1.5-2.7-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4 3 5.5l7 7Z'],
  home: ['M3 10.5 12 3l9 7.5', 'M5 9.5V21h14V9.5'],
  chat: ['M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12Z'],
  image: ['M3 4h18v16H3z', 'M7 10a2 2 0 1 0 4 0a2 2 0 1 0-4 0', 'm21 17-5-5-9 8'],
  upload: ['M12 16V4', 'm7 9 5-5 5 5', 'M4 20h16'],
  file: ['M14 3H6v18h12V7z', 'M14 3v4h4'],
  search: ['M11 18a7 7 0 1 0 0-14 7 7 0 0 0 0 14Z', 'm20 20-3.5-3.5'],
  back: ['m15 18-6-6 6-6'],
  chevronDown: ['m6 9 6 6 6-6'],
  close: ['M18 6 6 18', 'm6 6 12 12'],
}

function Icon({ name, size = 20, filled = false, className }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill={filled ? 'currentColor' : 'none'}
      stroke="currentColor"
      strokeWidth="1.8"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      className={className}
    >
      {PATHS[name].map((d) => (
        <path key={d} d={d} />
      ))}
    </svg>
  )
}

export default Icon
