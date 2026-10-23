import clsx from 'clsx'
import { useFormContext } from 'react-hook-form'

// react-hook-form의 FormProvider 안에서 쓰는 입력 칸 (라벨 + 입력 + 오류 문구)
function TextField({ name, label, hint, className, ...inputProps }) {
  const {
    register,
    formState: { errors },
  } = useFormContext()
  const error = errors[name]
  const id = `field-${name}`
  const describedBy = [hint && `${id}-hint`, error && `${id}-error`].filter(Boolean).join(' ')

  return (
    <div className={clsx('flex flex-col gap-1.5', className)}>
      <label htmlFor={id} className="text-sm font-medium text-gray-700">
        {label}
      </label>
      <input
        id={id}
        aria-invalid={Boolean(error)}
        aria-describedby={describedBy || undefined}
        className={clsx('h-12 rounded-xl border px-4 text-base', error ? 'border-red-500' : 'border-gray-300')}
        {...inputProps}
        {...register(name)}
      />
      {hint && (
        <p id={`${id}-hint`} className="text-sm text-gray-500">{hint}</p>
      )}
      {error && (
        <p id={`${id}-error`} className="text-sm text-red-600">{error.message}</p>
      )}
    </div>
  )
}

export default TextField
