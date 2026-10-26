// [공통 컴포넌트] 보금 로고 (아이콘 + '보금' 글자)
// dark: 어두운 배경(밤하늘, 관리자 사이드바)에서 글자를 흰색으로 바꾼다
import { Link } from 'react-router'
import clsx from 'clsx'
import logoIcon from '../../assets/logo-icon.svg'

function Logo({ to = '/', dark = false }) {
  return (
    <Link
      to={to}
      className={clsx('flex items-center gap-2.5 font-logo text-[28px] leading-none', dark ? 'text-white' : 'text-blue-900')}
    >
      <img src={logoIcon} alt="" className="size-10" />
      보금
    </Link>
  )
}

export default Logo
