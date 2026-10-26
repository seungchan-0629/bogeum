// [공통 컴포넌트] '관련 정보가 없어요' 안내 — 헤더·푸터는 그대로 두고 본문 자리에 보여준다
// 안내 페이지(GuideScreen)와 같은 밤하늘 + 건물 스카이라인에 큰 코드 대신 집 아이콘을 쓴다
// - description: 메뉴마다 다른 안내 문구 (예: '계약이 등록되면 이곳에 내 집 정보가 표시돼요.')
import { Link } from 'react-router'
import Icon from './Icon.jsx'
import Skyline from './Skyline.jsx'

function EmptyGuide({ description }) {
  return (
    <section className="flex grow flex-col bg-night text-white">
      <div className="m-auto flex flex-col items-center gap-3.5 px-6 pt-12 pb-10 text-center">
        <Icon name="home" size={56} className="text-blue-400" />
        <h1 className="text-3xl font-extrabold sm:text-[34px]">관련 정보가 없어요</h1>
        <p className="text-[17px] text-slate-300">{description}</p>
        <div className="mt-3 flex flex-wrap justify-center gap-2.5">
          <Link to="/properties" className="btn-primary">매물 찾기</Link>
          <Link to="/" className="btn-secondary">메인으로</Link>
        </div>
      </div>
      <Skyline animate={false} litRatio={0.04} />
      <div aria-hidden="true" className="h-5 bg-night-ground" />
    </section>
  )
}

export default EmptyGuide
