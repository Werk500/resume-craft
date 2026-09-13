interface FieldProps {
  id: string;
  label: string;
  error?: string;
  hint?: string;
  children: React.ReactNode;
}

/** 表单字段：label 关联 + 错误/提示信息 */
export default function Field({ id, label, error, hint, children }: FieldProps) {
  return (
    <div>
      <label htmlFor={id} className="mb-1.5 block text-sm font-medium text-zinc-700">
        {label}
      </label>
      {children}
      {error ? (
        <p id={`${id}-error`} role="alert" className="mt-1.5 text-xs text-red-600">
          {error}
        </p>
      ) : hint ? (
        <p className="mt-1.5 text-xs text-zinc-400">{hint}</p>
      ) : null}
    </div>
  );
}
