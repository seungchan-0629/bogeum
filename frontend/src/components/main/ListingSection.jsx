// TODO: 추천 매물 API 연동 전까지 쓰는 임시 데이터
const SAMPLE_LISTINGS = [
  { id: 1, type: '전세', price: '전세 2억 1,000', info: '서울 마포구 · 원룸 · 24㎡ · 3층' },
  { id: 2, type: '월세', price: '월세 1,000 / 65', info: '서울 관악구 · 투룸 · 36㎡ · 2층' },
  { id: 3, type: '매매', price: '매매 4억 8,000', info: '경기 수원시 · 아파트 · 59㎡ · 12층' },
]

function ListingSection() {
  return (
    <section className="mx-auto flex max-w-[1200px] flex-col gap-6 px-6 py-16">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <h2 className="text-[26px] font-extrabold">추천 매물</h2>
        <a href="#" className="text-[15px] font-medium text-blue-700 hover:text-blue-900">
          전체 보기
        </a>
      </div>
      <div className="flex flex-wrap gap-5">
        {SAMPLE_LISTINGS.map((listing) => (
          <article key={listing.id} className="min-w-0 flex-[1_1_280px] overflow-hidden rounded-2xl border border-gray-200 bg-white">
            <div className="flex h-45 items-center justify-center bg-[#e8edf5] text-gray-500">
              <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                <path d="M3 10.5 12 3l9 7.5" />
                <path d="M5 9.5V21h14V9.5" />
              </svg>
            </div>
            <div className="flex flex-col gap-2 p-[18px]">
              <div className="flex flex-wrap gap-1.5">
                <span className="rounded-md bg-emerald-50 px-2.5 py-1 text-[13px] font-bold text-emerald-700">등기 확인 완료</span>
                <span className="rounded-md bg-gray-100 px-2.5 py-1 text-[13px] font-medium text-gray-700">{listing.type}</span>
              </div>
              <p className="text-xl font-extrabold">{listing.price}</p>
              <p className="text-sm text-gray-600">{listing.info}</p>
            </div>
          </article>
        ))}
      </div>
    </section>
  )
}

export default ListingSection
