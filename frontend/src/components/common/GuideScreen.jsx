// [공통 컴포넌트] 안내 페이지 틀 (404, 권한 없음, 로그인 필요, 서버 오류)
// 메인 소개 화면과 같은 밤하늘 + 건물 스카이라인 위에 큰 코드, 제목, 설명, 버튼을 보여준다
// - code: 큰 글자 (예: '404'), litRatio: 불 켜진 창문 비율 (상황마다 다르게)
// - children: 버튼 자리
import Logo from './Logo.jsx'
import Skyline from './Skyline.jsx'

function GuideScreen({ code, title, description, litRatio, extra, children }) {
  return (
    <section className="flex min-h-svh flex-col bg-night text-white">
      <div className="px-6 pt-6">
        <Logo dark />
      </div>
      <div className="m-auto flex flex-col items-center gap-3.5 px-6 py-10 text-center">
        {code && <p className="text-[88px] leading-none font-extrabold tracking-tight text-blue-400">{code}</p>}
        <h1 className="text-3xl font-extrabold sm:text-[34px]">{title}</h1>
        <p className="text-[17px] text-slate-300">{description}</p>
        {extra}
        <div className="mt-3 flex flex-wrap justify-center gap-2.5">{children}</div>
      </div>
      <Skyline animate={false} litRatio={litRatio} />
      <div aria-hidden="true" className="h-5 bg-night-ground" />
    </section>
  )
}

export default GuideScreen
