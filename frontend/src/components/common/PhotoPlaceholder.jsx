// [공통 컴포넌트] 사진이 아직 없을 때 자리를 채우는 회색 칸
// API의 이미지 주소가 오면 이 자리에 <img>를 넣는다
import clsx from 'clsx'

function PhotoPlaceholder({ label = '사진', className }) {
  return (
    <div className={clsx('flex items-center justify-center rounded-lg bg-[#e6ecf4] text-sm text-slate-500', className)}>
      {label}
    </div>
  )
}

export default PhotoPlaceholder
