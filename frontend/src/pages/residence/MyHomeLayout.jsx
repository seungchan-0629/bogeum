// [페이지 틀] 내 집 관리 (임차인) — /my-home
// 계약 매물 목록 + 탭 4개. 매물 선택은 같은 화면의 월세 요약과 출금 내역에 반영한다.
// - /my-home/rent     월세 납부 내역 (RentPaymentPage)
// - /my-home/as       AS 요청 (TenantAsPage)
// - /my-home/move-in  입주 하자 사진 (MoveInUploadPage)
// - /my-home/claim    클레임 접수 (ClaimPage)
import { useState } from 'react'
import { Outlet } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import clsx from 'clsx'
import PageHeader from '../../components/common/PageHeader.jsx'
import PhotoPlaceholder from '../../components/common/PhotoPlaceholder.jsx'
import { TabNav } from '../../components/common/Tabs.jsx'
import { getTenantContracts } from '../../api/contract/index.js'
import { useAuthStore } from '../../stores/authStore.js'
import { formatLeasePrice } from '../../utils/format.js'

const TABS = [
  { to: '/my-home/rent', label: '월세 납부 내역' },
  { to: '/my-home/as', label: 'AS 요청' },
  { to: '/my-home/move-in', label: '입주 하자 사진' },
  { to: '/my-home/claim', label: '클레임' },
]

function MyHomeLayout() {
  const userId = useAuthStore((state) => state.user?.userId)
  const [selectedContractId, setSelectedContractId] = useState(null)
  const contracts = useQuery({
    queryKey: ['tenant-contracts', userId],
    queryFn: ({ signal }) => getTenantContracts(signal),
    enabled: Boolean(userId),
  })
  const selectedContract = contracts.data?.find((contract) => contract.contractId === selectedContractId) ?? null

  return (
    <div className="page-container">
      <PageHeader title="내 집 관리" />
      <section aria-labelledby="tenant-contracts-title" className="flex flex-col gap-3 rounded-2xl bg-[#eef2f7] px-5 py-4">
        <div className="flex items-center justify-between gap-3">
          <h2 id="tenant-contracts-title" className="font-bold">계약한 매물</h2>
          <button type="button" onClick={() => setSelectedContractId(null)} aria-pressed={!selectedContract} className="btn-secondary btn-sm">전체 보기</button>
        </div>
        {contracts.isPending && <p role="status" className="text-sm text-gray-600">계약한 매물을 불러오고 있어요.</p>}
        {contracts.isError && (
          <div role="alert" className="flex flex-wrap items-center gap-3">
            <p className="text-sm text-red-600">계약한 매물을 불러오지 못했어요.</p>
            <button type="button" onClick={() => contracts.refetch()} disabled={contracts.isFetching} className="btn-secondary btn-sm">다시 불러오기</button>
          </div>
        )}
        {contracts.isSuccess && contracts.data.length === 0 && <p className="text-sm text-gray-600">아직 계약한 매물이 없어요.</p>}
        {contracts.data?.length > 0 && (
          <ul className="flex flex-col gap-2">
            {contracts.data.map((contract) => (
              <li key={contract.contractId}>
                <button
                  type="button"
                  aria-pressed={selectedContract?.contractId === contract.contractId}
                  onClick={() => setSelectedContractId(contract.contractId)}
                  className={clsx('flex w-full items-center gap-3.5 rounded-xl border p-3 text-left transition-colors focus-visible:outline-2 focus-visible:outline-blue-600',
                    selectedContract?.contractId === contract.contractId ? 'border-blue-600 bg-white ring-1 ring-blue-600' : 'border-transparent hover:bg-white/70')}
                >
                  <PhotoPlaceholder label="사진" className="size-14 shrink-0 bg-[#d9e1ec]" />
                  <span className="flex min-w-0 flex-col gap-1">
                    <span className="text-lg font-bold">{contract.roadAddress} {contract.detailAddress}</span>
                    <span className="font-semibold">{formatLeasePrice({ ...contract, deposit: contract.deposit / 10000, monthlyRent: contract.monthlyRent / 10000 })}</span>
                    <span className="text-sm text-gray-600">
                      계약 {contract.startAt.replaceAll('-', '.')} ~ {contract.endAt.replaceAll('-', '.')}
                      {contract.paymentDay && ` · 매월 ${contract.paymentDay}일 납부`}
                    </span>
                  </span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>
      <TabNav label="내 집 관리 메뉴" items={TABS} />
      <Outlet context={{ selectedContract }} />
    </div>
  )
}

export default MyHomeLayout
