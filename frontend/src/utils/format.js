// 금액 단위는 만원으로 가정한다 (API 명세에 단위가 없어 백엔드와 협의 필요)
export function formatManwon(amount) {
  const eok = Math.floor(amount / 10000)
  const rest = amount % 10000
  if (eok === 0) return rest.toLocaleString()
  if (rest === 0) return `${eok}억`
  return `${eok}억 ${rest.toLocaleString()}`
}

// SALE(매매)은 API 명세(BE-018 leaseType)에 없어 백엔드와 협의 필요. 매매가는 salePrice로 가정한다
const LEASE_TYPE_LABELS = { JEONSE: '전세', MONTHLY: '월세', SALE: '매매' }

export function formatLeasePrice({ leaseType, deposit, monthlyRent, salePrice }) {
  if (leaseType === 'MONTHLY') {
    return `월세 ${formatManwon(deposit)} / ${monthlyRent.toLocaleString()}`
  }
  if (leaseType === 'SALE') {
    return `매매 ${formatManwon(salePrice)}`
  }
  return `${LEASE_TYPE_LABELS[leaseType]} ${formatManwon(deposit)}`
}

export function getLeaseTypeLabel(leaseType) {
  return LEASE_TYPE_LABELS[leaseType]
}

// 원 단위 금액 (월세 납부·지급 내역). 예: 650000 → '650,000원'
export function formatWon(amount) {
  return `${amount.toLocaleString()}원`
}
