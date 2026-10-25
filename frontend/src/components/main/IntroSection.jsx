// [메인 화면 섹션] 소개 화면 — 로그인하지 않은 사용자가 메인에 들어올 때마다 화면 전체를 덮고 재생된다
// 순서: 창문 불 켜짐 → 문구 세 줄 타이핑 → 불 켜진 창문 하나로 건물째 다가가며 온보딩으로 넘어감 → onFinish
// - [건너뛰기]: 누르면 남은 타이핑을 건너뛰고 바로 넘어간다
// - 기기에서 '동작 줄이기'를 켠 사용자: 타이핑 없이 문구를 한 번에 보여주고, 넘어갈 때는 짧게 흐려지기만 한다
// 요구사항 시트에는 없는 화면이라 팀 공유 필요 (2026.10.25 결정: 전환 효과 D안, 시안 https://claude.ai/artifact/CbkZPPKb7MyHoJJiXYeXUZ)
import { useEffect, useRef, useState } from 'react'
import clsx from 'clsx'
import Skyline from '../common/Skyline.jsx'
import logoIcon from '../../assets/logo-icon.svg'

// 줄마다 [글자, 파란색 여부] 조각으로 나눈다
const LINES = [
  [['당신의 '], ['보금자리', true], ['와']],
  [['보증금', true], ['을 지켜드립니다.']],
  [['오늘 밤도 안심하고 불 켤 수 있도록.']],
]
const CHARS = LINES.flatMap((segments, line) =>
  segments.flatMap(([text, isBlue]) => [...text].map((char) => ({ char, line, isBlue }))),
)

// 타이핑 시간(ms). 창문 불이 거의 다 켜진 뒤에 시작한다
const TYPING_START_MS = 1400
const CHAR_MS = 52
const SPACE_MS = 25
const LINE_PAUSE_MS = 240
const HOLD_MS = 1100 // 다 친 뒤 문구를 읽을 시간
const DIVE_MS = 1500 // 건물로 다가가는 시간
const BRIGHTEN_MS = 500 // 창문 불빛이 온보딩으로 밝게 풀리는 시간

// i번째 글자를 보여주기 전에 기다릴 시간
function delayBefore(index) {
  if (index === 0) return TYPING_START_MS
  const prev = CHARS[index - 1]
  const lineBreak = CHARS[index].line !== prev.line ? LINE_PAUSE_MS : 0
  return (prev.char === ' ' ? SPACE_MS : CHAR_MS) + lineBreak
}

const prefersReducedMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches

// 화면 가운데 아래쪽에 가장 가까운 불 켜진 창문으로, 장면 전체를 키우며 다가간다.
// 창문이 화면을 채우면 그 불빛이 온보딩 화면으로 밝게 풀린다
async function diveIntoWindow({ root, scene, copy, flash }, animations) {
  const box = root.getBoundingClientRect()
  const target = { x: box.width / 2, y: box.height * 0.7 }
  let best = null
  let bestDistance = Infinity
  scene.querySelectorAll('[data-lit]').forEach((win) => {
    const rect = win.getBoundingClientRect()
    const distance = Math.hypot(rect.left + rect.width / 2 - target.x, rect.top + rect.height / 2 - target.y)
    if (distance < bestDistance) {
      bestDistance = distance
      best = rect
    }
  })
  const cx = best.left + best.width / 2
  const cy = best.top + best.height / 2
  const endScale = Math.max(box.width / best.width, box.height / best.height) * 1.15
  const dx = box.width / 2 - cx
  const dy = box.height / 2 - cy

  // 배율을 거듭제곱으로 키워야 다가가는 속도가 일정하게 느껴진다. 창문은 앞부분에서 먼저 가운데로 온다
  const frames = Array.from({ length: 21 }, (_, i) => {
    const t = i / 20
    const pan = Math.min(1, t * 1.8)
    return { offset: t, transform: `translate(${dx * pan}px, ${dy * pan}px) scale(${endScale ** t})` }
  })
  scene.style.transformOrigin = `${cx}px ${cy}px`
  animations.push(copy.animate([{ opacity: 1 }, { opacity: 0 }], { duration: 400, fill: 'forwards' }))
  const dive = scene.animate(frames, { duration: DIVE_MS, easing: 'cubic-bezier(0.6, 0, 0.4, 1)', fill: 'forwards' })
  animations.push(dive)
  await dive.finished

  const brighten = root.animate([{ opacity: 1 }, { opacity: 1, offset: 0.4 }, { opacity: 0 }], { duration: BRIGHTEN_MS, easing: 'ease-out', fill: 'forwards' })
  animations.push(
    flash.animate(
      [{ opacity: 1, backgroundColor: 'var(--color-window-on)' }, { opacity: 1, backgroundColor: '#fff6dc', offset: 0.4 }, { opacity: 1, backgroundColor: '#ffffff' }],
      { duration: BRIGHTEN_MS, easing: 'ease-out', fill: 'forwards' },
    ),
    brighten,
  )
  await brighten.finished
}

function IntroSection({ onFinish }) {
  const rootRef = useRef(null)
  const sceneRef = useRef(null)
  const copyRef = useRef(null)
  const flashRef = useRef(null)
  const [isReducedMotion] = useState(prefersReducedMotion)
  const [typedCount, setTypedCount] = useState(() => (isReducedMotion ? CHARS.length : 0))
  const [isLeaving, setIsLeaving] = useState(false)
  const isTyping = !isReducedMotion && typedCount < CHARS.length

  // 한 글자씩 보여주고, 다 보여주면 잠시 뒤 넘어간다
  useEffect(() => {
    if (isLeaving) return
    const isDone = typedCount >= CHARS.length
    const timer = setTimeout(
      () => (isDone ? setIsLeaving(true) : setTypedCount((count) => count + 1)),
      isDone ? (isReducedMotion ? 1500 : LINE_PAUSE_MS + HOLD_MS) : delayBefore(typedCount),
    )
    return () => clearTimeout(timer)
  }, [typedCount, isLeaving, isReducedMotion])

  // 넘어가는 효과가 끝나면 부모(MainPage)가 이 화면을 걷어낸다
  useEffect(() => {
    if (!isLeaving) return
    const animations = []
    let isCancelled = false
    const elements = { root: rootRef.current, scene: sceneRef.current, copy: copyRef.current, flash: flashRef.current }
    const leave = isReducedMotion
      ? () => {
          const fade = elements.root.animate([{ opacity: 1 }, { opacity: 0 }], { duration: 300, fill: 'forwards' })
          animations.push(fade)
          return fade.finished
        }
      : () => diveIntoWindow(elements, animations)
    leave()
      .then(() => !isCancelled && onFinish())
      .catch(() => {}) // 화면을 벗어나 애니메이션이 취소된 경우
    return () => {
      isCancelled = true
      animations.forEach((animation) => animation.cancel())
    }
  }, [isLeaving, isReducedMotion, onFinish])

  return (
    <section ref={rootRef} aria-label="서비스 소개" className="fixed inset-0 z-50 overflow-hidden bg-night text-white">
      <div ref={sceneRef} className="flex h-full flex-col pt-10">
        <div className="mx-auto flex w-full max-w-[1200px] items-center gap-3 px-6">
          <img src={logoIcon} alt="" className="size-11" />
          <div className="flex flex-col gap-1">
            <span className="font-logo text-[32px] leading-none">보금</span>
            <span className="text-xs font-medium tracking-[3px] text-slate-300">보증금 보장 · 보금자리</span>
          </div>
        </div>
        <div ref={copyRef} className="m-auto flex flex-col items-center gap-5 px-6 py-8 text-center">
          {/* 글자는 미리 자리를 잡아 두고 하나씩 보이게 한다. 그래서 가운데 정렬이 흔들리지 않는다 */}
          <h1 aria-label="당신의 보금자리와 보증금을 지켜드립니다." className="text-4xl leading-snug font-extrabold tracking-tight break-keep sm:text-[56px]">
            {[0, 1].map((line) => (
              <span key={line} aria-hidden="true" className="block">
                <TypedLine line={line} typedCount={typedCount} isTyping={isTyping} />
              </span>
            ))}
          </h1>
          <p aria-label="오늘 밤도 안심하고 불 켤 수 있도록." className="min-h-[1.75rem] text-lg break-keep text-slate-300">
            <span aria-hidden="true">
              <TypedLine line={2} typedCount={typedCount} isTyping={isTyping} />
            </span>
          </p>
          <button
            type="button"
            onClick={() => setIsLeaving(true)}
            disabled={isLeaving}
            className="mt-2 animate-fade-in rounded-full bg-white/10 px-5 py-2.5 text-sm text-slate-300 hover:text-white motion-reduce:animate-none"
          >
            건너뛰기
          </button>
        </div>
        <Skyline animate />
        <div aria-hidden="true" className="h-5 bg-night-ground" />
      </div>
      <div ref={flashRef} aria-hidden="true" className="pointer-events-none absolute inset-0 opacity-0" />
    </section>
  )
}

// 한 줄의 글자들. 아직 안 친 글자는 투명하게 자리만 차지하고, 마지막으로 친 글자 뒤에 커서를 둔다
function TypedLine({ line, typedCount, isTyping }) {
  return CHARS.map((item, index) =>
    item.line !== line ? null : (
      <span key={index} className={clsx(index >= typedCount && 'invisible', item.isBlue && 'text-blue-400')}>
        {item.char}
        {isTyping && index === typedCount - 1 && (
          // 커서는 폭이 0이라 글자를 밀어내지 않는다
          <span className="relative inline-block h-[1em] w-0 align-[-0.12em] after:absolute after:inset-y-0 after:left-0.5 after:w-0.5 after:animate-blink after:bg-current" />
        )}
      </span>
    ),
  )
}

export default IntroSection
