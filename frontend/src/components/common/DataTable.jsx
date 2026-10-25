// [공통 컴포넌트] 표
// - columns: [{ label: '금액', align: 'right', render: (row) => 값 }] — 칸 제목과 각 칸에 보여줄 값
// - rows: 데이터 목록, rowKey: 각 줄을 구분하는 필드 이름 (예: 'paymentId')
// - emptyMessage: 데이터가 없을 때 보여줄 문장
// 좁은 화면에서는 표 안에서만 옆으로 스크롤된다
import clsx from 'clsx'

function DataTable({ columns, rows, rowKey, emptyMessage = '내역이 없어요.' }) {
  return (
    <div className="card overflow-x-auto p-0">
      <table className="w-full min-w-[640px] text-left text-sm">
        <thead className="text-gray-500">
          <tr className="border-b border-gray-200">
            {columns.map((column) => (
              <th key={column.label} scope="col" className={clsx('px-5 py-3 font-medium whitespace-nowrap', column.align === 'right' && 'text-right')}>
                {column.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 && (
            <tr>
              <td colSpan={columns.length} className="px-5 py-10 text-center text-gray-500">{emptyMessage}</td>
            </tr>
          )}
          {rows.map((row) => (
            <tr key={row[rowKey]} className="border-b border-gray-100 last:border-b-0">
              {columns.map((column) => (
                <td key={column.label} className={clsx('px-5 py-3.5', column.align === 'right' && 'text-right tabular-nums')}>
                  {column.render(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export default DataTable
