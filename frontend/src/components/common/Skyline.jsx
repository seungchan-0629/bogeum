import clsx from 'clsx'

const BUILDINGS = [
  { cols: 3, rows: 7, hasAntenna: false },
  { cols: 4, rows: 11, hasAntenna: true },
  { cols: 3, rows: 15, hasAntenna: true },
  { cols: 5, rows: 9, hasAntenna: false },
  { cols: 2, rows: 6, hasAntenna: false },
  { cols: 4, rows: 13, hasAntenna: true },
  { cols: 3, rows: 8, hasAntenna: false },
]

// 창문마다 켜짐 여부와 켜지는 시점을 고정된 규칙으로 정한다 (새로고침해도 같은 모양)
let windowIndex = 0
const SKYLINE = BUILDINGS.map((building) => ({
  ...building,
  windows: Array.from({ length: building.cols * building.rows }, () => {
    const seed = (windowIndex++ * 7919 + 13) % 101
    return { isLit: seed < 70, delay: 0.2 + ((seed % 37) / 37) * 1.4 }
  }),
}))

function Skyline({ animate }) {
  return (
    <div aria-hidden="true" className="flex items-end justify-center gap-3.5 px-6">
      {SKYLINE.map((building, i) => (
        <div key={i} className="flex shrink-0 flex-col items-center">
          {building.hasAntenna && <div className="h-7 w-0.5 bg-night-line" />}
          <div
            className="grid gap-2 rounded-t bg-night-building p-3.5"
            style={{ gridTemplateColumns: `repeat(${building.cols}, 14px)` }}
          >
            {building.windows.map((win, j) => (
              <div
                key={j}
                className={clsx(
                  'h-[18px] w-3.5 rounded-sm',
                  win.isLit
                    ? 'bg-window-on shadow-[0_0_10px_rgba(252,211,77,0.6)]'
                    : 'bg-window-off',
                  win.isLit && animate && 'animate-lit motion-reduce:animate-none',
                )}
                style={animate ? { animationDelay: `${win.delay}s` } : undefined}
              />
            ))}
          </div>
        </div>
      ))}
    </div>
  )
}

export default Skyline
