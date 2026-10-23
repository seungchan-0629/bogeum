const VALUES = [
  {
    label: '쉽게 찾는다',
    title: '조건으로 찾고, 채팅으로 바로 연락',
    description: '관심 매물로 저장해 두고, 집주인과 바로 이야기를 나눠요.',
  },
  {
    label: '안전하게 확인한다',
    title: '모든 매물에 등기부등본 확인',
    description: '확인된 위험 신호를 그 뜻과 함께 보여드려요.',
  },
  {
    label: '편리하게 관리한다',
    title: '계약서 한 번이면 퇴실까지',
    description: '월세 납부, 기한 알림, 입주 기록, 퇴실 정산이 이어져요.',
  },
]

function ValueSection() {
  return (
    <section className="bg-night text-white">
      <div className="mx-auto flex max-w-[1200px] flex-wrap gap-5 px-6 py-16">
        {VALUES.map((value) => (
          <div key={value.label} className="flex flex-[1_1_280px] flex-col gap-2.5 rounded-2xl border border-night-line p-7">
            <p className="text-sm font-bold text-blue-400">{value.label}</p>
            <h3 className="text-xl font-bold">{value.title}</h3>
            <p className="text-[15px] leading-relaxed text-slate-300">{value.description}</p>
          </div>
        ))}
      </div>
    </section>
  )
}

export default ValueSection
