// [공통 컴포넌트] 파일 올리는 칸
// 누르면 파일 선택 창이 열린다. 고른 파일 이름을 아래에 보여주고, 실제 업로드는 각 화면의 제출 버튼에서 한다
// - accept: 받을 파일 형식 (예: 'image/*'), multiple: 여러 개 선택 허용
import { useId, useState } from 'react'
import Icon from './Icon.jsx'

function FileDropzone({ title, hint, accept = 'image/*', multiple = false, onChange }) {
  const inputId = useId()
  const [fileNames, setFileNames] = useState([])

  const handleChange = (event) => {
    const files = Array.from(event.target.files)
    setFileNames(files.map((file) => file.name))
    onChange?.(files)
  }

  return (
    <div className="flex flex-col gap-2">
      <label
        htmlFor={inputId}
        className="flex cursor-pointer flex-col items-center gap-1.5 rounded-xl border border-dashed border-gray-300 bg-slate-50 px-6 py-7 text-center text-gray-600 hover:border-blue-400"
      >
        <Icon name="upload" size={26} />
        <span className="font-bold text-gray-800">{title}</span>
        {hint && <span className="text-sm">{hint}</span>}
      </label>
      <input id={inputId} type="file" accept={accept} multiple={multiple} onChange={handleChange} className="sr-only" />
      {fileNames.length > 0 && (
        <ul className="flex flex-col gap-1 text-sm text-gray-700">
          {fileNames.map((name) => (
            <li key={name}>{name}</li>
          ))}
        </ul>
      )}
    </div>
  )
}

export default FileDropzone
