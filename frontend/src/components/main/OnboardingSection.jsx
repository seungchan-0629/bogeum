// [메인 화면 섹션] 온보딩 — 로그인하지 않은 사용자에게만, 소개 화면(IntroSection)이 걷힌 뒤 보인다
// 역할별(집 구하는 사람·집 내놓는 사람·중개사)로 보금을 어떻게 쓰는지 보여주고, 회원가입으로 이어준다
// - animate가 true면, isPlaying이 true가 될 때 제목 → 왼쪽 카드부터 하나씩 → 가입 안내 순서로 나타난다
// - animate가 false면(소개를 이미 본 뒤 다른 화면에서 돌아온 경우) 처음부터 다 보여준다
// - [가입 안내 보기]: 남은 카드를 한 번에 보여주고 아래 가입 안내(#signup-cta)로 바로 이동
// 요구사항 시트에는 없는 화면이라 팀 공유 필요 (2026.10.24 결정: 카드 나열형 B안, 2026.10.25 결정: 차례로 나타나기)
import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import clsx from 'clsx'
import logoIcon from '../../assets/logo-icon.svg'

const SIGNUP_CTA_ID = 'signup-cta'

// 나타나는 차례: 1 제목, 2~4 카드(왼쪽부터), 5 가입 안내. 값은 앞 단계가 나온 뒤 기다릴 시간(ms)
const REVEAL_DELAYS = [0, 250, 220, 220, 500]
const ALL_REVEALED = REVEAL_DELAYS.length

const revealClass = (isShown) =>
  clsx(
    'transition-[opacity,transform] duration-600 ease-[cubic-bezier(0.2,0.7,0.2,1)] motion-reduce:transform-none',
    isShown ? 'opacity-100' : 'translate-y-5 opacity-0',
  )

const ROLE_GUIDES = [
  {
    question: '집 구하시나요?',
    role: '임차인',
    steps: [
      ['조건으로 매물 찾기', '전세·월세·매매, 지역·지하철역·가격으로 걸러 봐요.'],
      ['등기·위험 정보 확인', '모든 매물의 등기부등본을 확인하고 위험 신호를 쉬운 말로 알려드려요.'],
      ['채팅으로 바로 문의', '관심 매물로 저장하고 중개사와 바로 이야기해요.'],
      ['계약 후엔 자동으로', '월세 자동 납부, AS 요청, 입주 하자 기록까지 한곳에서.'],
    ],
  },
  {
    question: '집 내놓으시나요?',
    role: '임대인',
    steps: [
      ['중개 신청', '전세·월세·매매 조건과 주소만 입력하면 중개사에게 신청돼요.'],
      ['중개사가 매물 등록', '중개사가 사진과 상세 정보를 올려 매물을 공개해요.'],
      ['월세는 자동 입금', '임차인의 월세가 결제되면 바로 정산 계좌로 지급돼요.'],
      ['입주·AS 기록 관리', '입주 하자 사진과 AS 요청을 기록으로 남겨요.'],
    ],
  },
  {
    question: '매물 중개하시나요?',
    role: '중개사',
    steps: [
      ['가입 후 서류 제출', '가입 후 2~3일 안에 공인중개사 서류를 올려요.'],
      ['관리자 승인', '자격 확인이 끝나면 승인 알림을 보내드려요.'],
      ['중개 신청 수락', '임대인의 중개 신청을 수락하고 매물 정보를 올려요.'],
      ['계약·이력 관리', '계약서를 올리고 중개 이력을 한눈에 관리해요.'],
    ],
  },
]

function OnboardingSection({ animate, isPlaying }) {
  const [revealedCount, setRevealedCount] = useState(animate ? 0 : ALL_REVEALED)

  useEffect(() => {
    if (!animate || !isPlaying) return
    let elapsed = 0
    const timers = REVEAL_DELAYS.map((delay, index) => {
      elapsed += delay
      // [가입 안내 보기]로 이미 다 보여준 뒤에는 줄어들지 않게 한다
      return setTimeout(() => setRevealedCount((count) => Math.max(count, index + 1)), elapsed)
    })
    return () => timers.forEach(clearTimeout)
  }, [animate, isPlaying])

  return (
    <section aria-labelledby="onboarding-title" className="bg-white">
      <div className="mx-auto flex max-w-[1200px] flex-col gap-7 px-6 py-16">
        <div className={clsx('flex flex-wrap items-end justify-between gap-4', revealClass(revealedCount >= 1))}>
          <div className="flex flex-col gap-1">
            <h2 id="onboarding-title" className="text-3xl font-extrabold tracking-tight break-keep sm:text-[34px]">이런 분들을 위해 만들었어요</h2>
            <p className="text-gray-600">내게 맞는 이용 방법을 확인해 보세요.</p>
          </div>
          <a href={`#${SIGNUP_CTA_ID}`} onClick={() => setRevealedCount(ALL_REVEALED)} className="btn-secondary">가입 안내 보기</a>
        </div>
        <div className="grid gap-4 md:grid-cols-3">
          {ROLE_GUIDES.map((guide, index) => (
            <article key={guide.role} className={clsx('flex flex-col gap-5 rounded-2xl bg-[#f3f6fa] p-7', revealClass(revealedCount >= index + 2))}>
              <div className="flex flex-col gap-0.5">
                <h3 className="text-2xl font-bold tracking-tight">{guide.question}</h3>
                <p className="text-sm text-gray-600">{guide.role}으로 이용해요</p>
              </div>
              <ol className="flex list-decimal flex-col gap-3 pl-5 marker:font-bold marker:text-gray-500">
                {guide.steps.map(([title, description]) => (
                  <li key={title}>
                    <p className="font-bold">{title}</p>
                    <p className="text-sm text-gray-600">{description}</p>
                  </li>
                ))}
              </ol>
            </article>
          ))}
        </div>
      </div>

      <div className="px-6 pb-16">
        <div id={SIGNUP_CTA_ID} className={clsx('mx-auto flex max-w-[1200px] scroll-mt-6 flex-col items-center gap-4 rounded-3xl bg-night px-6 py-12 text-center text-white', revealClass(revealedCount >= ALL_REVEALED))}>
          <img src={logoIcon} alt="" className="size-14" />
          <h2 className="text-3xl font-extrabold">보금은 회원가입 후 이용할 수 있어요</h2>
          <p className="text-slate-300">가입하면 매물 검색부터 계약 후 월세 관리까지 바로 시작할 수 있어요.</p>
          <div className="mt-2 flex flex-wrap justify-center gap-2.5">
            <Link to="/signup" className="btn-primary h-12 px-6">임대·임차인 회원가입</Link>
            <Link to="/signup/agent" className="btn-secondary h-12 px-6">중개사 회원가입</Link>
          </div>
          <Link to="/login" className="text-sm text-blue-300 hover:text-blue-200">이미 계정이 있어요 · 로그인</Link>
        </div>
      </div>
    </section>
  )
}

export default OnboardingSection
