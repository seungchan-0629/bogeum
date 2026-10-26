// [공통 컴포넌트] 요약 숫자 띠 — 하나의 카드를 칸으로 나눠 숫자 2~4개를 보여준다 (예: 이번 달 월세, 다음 납부일)
// - items: [{ label: '이번 달 월세', value: '650,000원', detail: '10.25 결제 완료' }] — detail에는 링크 같은 요소도 넣을 수 있다
import clsx from 'clsx'

const COLUMN_CLASSES = { 2: 'sm:grid-cols-2', 3: 'sm:grid-cols-3', 4: 'sm:grid-cols-4' }

function SummaryStrip({ items }) {
  return (
    <dl className={clsx('grid divide-y divide-gray-100 rounded-2xl border border-gray-200 bg-white sm:divide-x sm:divide-y-0', COLUMN_CLASSES[items.length])}>
      {items.map((item) => (
        <div key={item.label} className="flex flex-col gap-0.5 px-5 py-4">
          <dt className="text-sm text-gray-600">{item.label}</dt>
          <dd className="text-xl font-bold">{item.value}</dd>
          {item.detail && <dd className="text-sm text-gray-600">{item.detail}</dd>}
        </div>
      ))}
    </dl>
  )
}

export default SummaryStrip
