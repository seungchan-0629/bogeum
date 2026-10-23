import { useEffect, useRef } from 'react'
import clsx from 'clsx'

const BUILDINGS = [
  { cols: 3, rows: 7, hasAntenna: false },
  { cols: 4, rows: 11, hasAntenna: true },
  { cols: 3, rows: 15, hasAntenna: true },
  { cols: 5, rows: 9, hasAntenna: false },
  { cols: 2, rows: 6, hasAntenna: false },
  { cols: 4, rows: 13, hasAntenna: true },
  { cols: 3, rows: 8, hasAntenna: false },
]

// 창문마다 켜짐 여부와 켜지는 시점을 고정된 규칙으로 정한다 (새로고침해도 같은 모양)
let windowIndex = 0
const SKYLINE = BUILDINGS.map((building) => ({
  ...building,
  windows: Array.from({ length: building.cols * building.rows }, () => {
    const seed = (windowIndex++ * 7919 + 13) % 101
    return { isLit: seed < 70, delay: 0.2 + ((seed % 37) / 37) * 1.4 }
  }),
}))

function Skyline({ animate }) {
  return (
    <div aria-hidden="true" className="flex items-end justify-center gap-3.5 px-6">
      {SKYLINE.map((building, i) => (
        <div key={i} className="flex flex-col items-center">
          {building.hasAntenna && <div className="h-7 w-0.5 bg-night-line" />}
          <div
            className="grid gap-2 rounded-t bg-night-building p-3.5"
            style={{ gridTemplateColumns: `repeat(${building.cols}, 14px)` }}
          >
            {building.windows.map((win, j) => (
              <div
                key={j}
                className={clsx(
                  'h-[18px] w-3.5 rounded-sm',
                  win.isLit
                    ? 'bg-window-on shadow-[0_0_10px_rgba(252,211,77,0.6)]'
                    : 'bg-window-off',
                  win.isLit && animate && 'animate-lit motion-reduce:animate-none',
                )}
                style={animate ? { animationDelay: `${win.delay}s` } : undefined}
              />
            ))}
          </div>
        </div>
      ))}
    </div>
  )
}

function IntroSection({ animate, nextSectionId }) {
  const contentRef = useRef(null)

  // 스크롤을 내릴수록 소개 문구가 흐려지며 위로 올라간다
  useEffect(() => {
    const handleScroll = () => {
      const progress = Math.min(window.scrollY / (window.innerHeight * 0.6), 1)
      contentRef.current.style.opacity = 1 - progress
      contentRef.current.style.transform = `translateY(${-progress * 40}px)`
    }
    window.addEventListener('scroll', handleScroll, { passive: true })
    return () => window.removeEventListener('scroll', handleScroll)
  }, [])

  const rise = animate && 'animate-rise motion-reduce:animate-none'

  return (
    <section
      aria-label="서비스 소개"
      className="flex min-h-svh flex-col overflow-hidden bg-night pt-10 text-white"
    >
      <div className="mx-auto w-full max-w-[1200px] px-6 text-[22px] font-extrabold text-blue-400">
        보금
      </div>
      <div
        ref={contentRef}
        className="m-auto flex flex-col items-center gap-5 px-6 py-8 text-center motion-reduce:!transform-none"
      >
        <h1 className="text-4xl leading-snug font-extrabold tracking-tight sm:text-[56px]">
          <span className={clsx('inline-block', rise)} style={{ animationDelay: '1.5s' }}>
            당신의 보금자리와
          </span>
          <br />
          <span className={clsx('inline-block', rise)} style={{ animationDelay: '1.9s' }}>
            <span className="text-blue-400">보증금</span>을 지켜드립니다.
          </span>
        </h1>
        <p className={clsx('text-lg text-slate-300', rise)} style={{ animationDelay: '2.4s' }}>
          오늘 밤도 안심하고 불 켤 수 있도록.
        </p>
        <a
          href={`#${nextSectionId}`}
          className={clsx('mt-3 flex flex-col items-center gap-1.5 p-2 text-sm text-slate-300 hover:text-white', rise)}
          style={{ animationDelay: '2.9s' }}
        >
          스크롤
          <svg className="animate-nudge motion-reduce:animate-none" width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M12 5v14" />
            <path d="m19 12-7 7-7-7" />
          </svg>
        </a>
      </div>
      <Skyline animate={animate} />
      <div aria-hidden="true" className="h-5 bg-night-ground" />
    </section>
  )
}

export default IntroSection
