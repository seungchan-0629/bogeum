// [페이지 틀] 입주·AS (임대인) — /care
// 탭 3개. 탭을 누르면 아래 하위 페이지가 바뀐다
// - /care/as       받은 AS 요청 조회·답변 (LandlordAsPage)
// - /care/move-in  입주 하자 사진 확인 (DefectCheckPage)
// - /care/claim    클레임 접수 (ClaimPage, 임차인과 같은 화면)
import { Outlet } from 'react-router'
import PageHeader from '../../components/common/PageHeader.jsx'
import { TabNav } from '../../components/common/Tabs.jsx'

const TABS = [
  { to: '/care/as', label: 'AS 요청' },
  { to: '/care/move-in', label: '입주 하자 사진' },
  { to: '/care/claim', label: '클레임' },
]

function LandlordCareLayout() {
  return (
    <div className="page-container">
      <PageHeader title="입주·AS" />
      <TabNav label="입주·AS 메뉴" items={TABS} />
      <Outlet />
    </div>
  )
}

export default LandlordCareLayout
