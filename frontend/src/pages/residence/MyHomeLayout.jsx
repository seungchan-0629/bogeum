// [페이지 틀] 내 집 관리 (임차인) — /my-home
// 지금 사는 집 요약 + 탭 4개. 탭을 누르면 아래 하위 페이지가 바뀐다
// - /my-home/rent     월세 납부 내역 (RentPaymentPage)
// - /my-home/as       AS 요청 (TenantAsPage)
// - /my-home/move-in  입주 하자 사진 (MoveInUploadPage)
// - /my-home/claim    클레임 접수 (ClaimPage)
import { Outlet } from 'react-router'
import PageHeader from '../../components/common/PageHeader.jsx'
import PhotoPlaceholder from '../../components/common/PhotoPlaceholder.jsx'
import { TabNav } from '../../components/common/Tabs.jsx'

const TABS = [
  { to: '/my-home/rent', label: '월세 납부 내역' },
  { to: '/my-home/as', label: 'AS 요청' },
  { to: '/my-home/move-in', label: '입주 하자 사진' },
  { to: '/my-home/claim', label: '클레임' },
]

// TODO: 임차인의 현재 계약 정보 API가 명세에 없어 백엔드와 협의 필요 (매물, 계약 기간, 월세, 납부일)
const SAMPLE_CONTRACT = {
  title: '역삼동 오피스텔 · 월세 1,000 / 65',
  period: '2026.10.01 ~ 2028.09.30',
  paymentDay: 25,
}

function MyHomeLayout() {
  return (
    <div className="page-container">
      <PageHeader title="내 집 관리" />
      <div className="flex items-center gap-3.5 rounded-2xl bg-[#eef2f7] px-5 py-4">
        <PhotoPlaceholder label="사진" className="size-14 shrink-0 bg-[#d9e1ec]" />
        <div className="flex flex-col">
          <span className="text-lg font-bold">{SAMPLE_CONTRACT.title}</span>
          <span className="text-sm text-gray-600">
            계약 {SAMPLE_CONTRACT.period} · 매월 {SAMPLE_CONTRACT.paymentDay}일 납부
          </span>
        </div>
      </div>
      <TabNav label="내 집 관리 메뉴" items={TABS} />
      <Outlet />
    </div>
  )
}

export default MyHomeLayout
