// 29~31일 납부는 해당 월에 그 날짜가 없으면 말일로 계산한다.
export function getNextPaymentDate(contract, today = new Date(), hasPaidThisMonth = false) {
  if (contract.leaseType !== 'MONTHLY' || !contract.paymentDay) return null

  const currentDate = new Date(today.getFullYear(), today.getMonth(), today.getDate())
  const startDate = new Date(`${contract.startAt}T00:00:00`)
  const endDate = new Date(`${contract.endAt}T00:00:00`)
  const earliestDate = new Date(Math.max(currentDate, startDate))
  const paymentDate = (year, month) => new Date(year, month,
    Math.min(contract.paymentDay, new Date(year, month + 1, 0).getDate()))

  let nextDate = paymentDate(earliestDate.getFullYear(), earliestDate.getMonth())
  if (nextDate < earliestDate || (hasPaidThisMonth
    && nextDate.getFullYear() === currentDate.getFullYear() && nextDate.getMonth() === currentDate.getMonth())) {
    nextDate = paymentDate(nextDate.getFullYear(), nextDate.getMonth() + 1)
  }
  return nextDate <= endDate ? nextDate : null
}
