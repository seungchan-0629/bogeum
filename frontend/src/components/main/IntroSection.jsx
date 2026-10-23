import { useEffect, useRef } from 'react'
import clsx from 'clsx'
import Skyline from '../common/Skyline.jsx'
import logoIcon from '../../assets/logo-icon.svg'

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
      <div className="mx-auto flex w-full max-w-[1200px] items-center gap-3 px-6">
        <img src={logoIcon} alt="" className="size-11" />
        <div className="flex flex-col gap-1">
          <span className="font-logo text-[32px] leading-none">보금</span>
          <span className="text-xs font-medium tracking-[3px] text-slate-300">보증금 보장 · 보금자리</span>
        </div>
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
