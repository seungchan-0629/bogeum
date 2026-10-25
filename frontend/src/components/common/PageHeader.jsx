// [공통 컴포넌트] 화면 제목 영역
// title은 화면의 h1, description은 제목 아래 한 줄 설명, actions는 오른쪽 버튼 자리
function PageHeader({ title, description, actions }) {
  return (
    <div className="flex flex-wrap items-end justify-between gap-4">
      <div className="flex flex-col gap-1">
        <h1 className="text-[28px] font-extrabold tracking-tight">{title}</h1>
        {description && <p className="text-gray-600">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
    </div>
  )
}

export default PageHeader
