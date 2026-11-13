// [매물 컴포넌트] 가격 범위 막대 — 손잡이 두 개로 최소·최대 가격을 고른다 (PROPERTY_005)
// - steps: 고를 수 있는 금액(원) 목록. 낮은 금액은 촘촘히, 높은 금액은 듬성듬성 두면 낮은 금액을 세밀하게 고를 수 있다
// - value: { min, max } 원 단위 문자열. 손잡이가 양 끝에 있으면 '' (조건 없음)
//   오른쪽 끝은 "N 이상"이라 최대값을 보내지 않는다. 그래야 끝보다 비싼 매물도 빠지지 않는다
// - onChange({ min, max }), disabled, hint
// 브라우저 기본 범위 입력 두 개를 겹쳐 만든다. 키보드 화살표로도 손잡이를 움직일 수 있다
import clsx from 'clsx'

// 손잡이만 누를 수 있게 하고, 겹친 두 입력의 막대 부분은 누르지 않게 한다
const THUMB_CLASSES = clsx(
  'pointer-events-none absolute inset-0 h-full w-full appearance-none bg-transparent',
  '[&::-webkit-slider-thumb]:pointer-events-auto [&::-webkit-slider-thumb]:size-5 [&::-webkit-slider-thumb]:appearance-none',
  '[&::-webkit-slider-thumb]:rounded-full [&::-webkit-slider-thumb]:border-2 [&::-webkit-slider-thumb]:border-blue-600',
  '[&::-webkit-slider-thumb]:bg-white [&::-webkit-slider-thumb]:shadow',
  '[&::-moz-range-thumb]:pointer-events-auto [&::-moz-range-thumb]:size-5 [&::-moz-range-thumb]:rounded-full',
  '[&::-moz-range-thumb]:border-2 [&::-moz-range-thumb]:border-blue-600 [&::-moz-range-thumb]:bg-white',
)

// 원 → "0원", "5,000만", "1억", "1억 5,000만"
function formatPriceLabel(won) {
  if (won === 0) return '0원'
  const manwon = won / 10000
  const eok = Math.floor(manwon / 10000)
  const rest = manwon % 10000
  if (eok === 0) return `${rest.toLocaleString()}만`
  return rest === 0 ? `${eok}억` : `${eok}억 ${rest.toLocaleString()}만`
}

// 원 단위 문자열을 가장 가까운 눈금 위치로 바꾼다. 주소창에 눈금에 없는 값이 와도 막대가 깨지지 않게 한다
function toIndex(steps, won, fallback) {
  if (won === '') return fallback
  const index = steps.findIndex((step) => step >= Number(won))
  return index === -1 ? steps.length - 1 : index
}

function PriceRangeSlider({ id, label, steps, value, onChange, disabled = false, hint }) {
  const lastIndex = steps.length - 1
  const minIndex = toIndex(steps, value.min, 0)
  const maxIndex = toIndex(steps, value.max, lastIndex)
  const maxLabel = maxIndex === lastIndex ? `${formatPriceLabel(steps[lastIndex])} 이상` : formatPriceLabel(steps[maxIndex])

  // 양 끝 손잡이는 '' (조건 없음)로 바꿔 보낸다. 두 손잡이가 서로를 넘지 않게 막는다
  const handleMin = (event) => {
    const index = Math.min(Number(event.target.value), maxIndex)
    onChange({ min: index === 0 ? '' : String(steps[index]), max: value.max })
  }
  const handleMax = (event) => {
    const index = Math.max(Number(event.target.value), minIndex)
    onChange({ min: value.min, max: index === lastIndex ? '' : String(steps[index]) })
  }

  return (
    <div role="group" aria-labelledby={`${id}-label`} className={clsx('flex flex-col gap-2', disabled && 'opacity-50')}>
      <div className="flex items-baseline justify-between gap-2">
        <span id={`${id}-label`} className="text-sm font-bold">{label}</span>
        <span className="text-sm font-medium text-blue-700">
          {formatPriceLabel(steps[minIndex])} ~ {maxLabel}
        </span>
      </div>
      <div className="relative h-5">
        <div className="absolute inset-x-0 top-1/2 h-1.5 -translate-y-1/2 rounded-full bg-gray-200" />
        {/* 고른 범위만 파랗게 칠한다 */}
        <div
          className="absolute top-1/2 h-1.5 -translate-y-1/2 rounded-full bg-blue-600"
          style={{ left: `${(minIndex / lastIndex) * 100}%`, right: `${100 - (maxIndex / lastIndex) * 100}%` }}
        />
        <input
          type="range"
          min={0}
          max={lastIndex}
          value={minIndex}
          onChange={handleMin}
          disabled={disabled}
          aria-label={`${label} 최소`}
          aria-valuetext={formatPriceLabel(steps[minIndex])}
          className={THUMB_CLASSES}
          // 두 손잡이가 오른쪽 끝에서 겹치면 최소 손잡이를 위로 올려야 다시 왼쪽으로 끌 수 있다
          style={{ zIndex: minIndex > lastIndex / 2 ? 3 : 1 }}
        />
        <input
          type="range"
          min={0}
          max={lastIndex}
          value={maxIndex}
          onChange={handleMax}
          disabled={disabled}
          aria-label={`${label} 최대`}
          aria-valuetext={maxLabel}
          className={THUMB_CLASSES}
          style={{ zIndex: 2 }}
        />
      </div>
      {hint && <p className="text-sm text-gray-500">{hint}</p>}
    </div>
  )
}

export default PriceRangeSlider
