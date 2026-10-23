// 금액 단위는 만원으로 가정한다 (API 명세에 단위가 없어 백엔드와 협의 필요)
export function formatManwon(amount) {
  const eok = Math.floor(amount / 10000)
  const rest = amount % 10000
  if (eok === 0) return rest.toLocaleString()
  if (rest === 0) return `${eok}억`
  return `${eok}억 ${rest.toLocaleString()}`
}

const LEASE_TYPE_LABELS = { JEONSE: '전세', MONTHLY: '월세' }

export function formatLeasePrice({ leaseType, deposit, monthlyRent }) {
  if (leaseType === 'MONTHLY') {
    return `월세 ${formatManwon(deposit)} / ${monthlyRent.toLocaleString()}`
  }
  return `${LEASE_TYPE_LABELS[leaseType]} ${formatManwon(deposit)}`
}

export function getLeaseTypeLabel(leaseType) {
  return LEASE_TYPE_LABELS[leaseType]
}
