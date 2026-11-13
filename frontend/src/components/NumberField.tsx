export const FIELD = "rounded-sm border border-line bg-surface-2 px-2 py-1.5 text-[13px] text-ink";

// The month forms' number input: the capital/floor fields, the target rungs and the opener all share
// this one control, so a change to its shape lands everywhere at once.
export function NumberField({
  label,
  value,
  onChange,
}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <label className="flex flex-col gap-1 text-[12.5px] text-ink-dim">
      {label}
      <input
        type="number"
        step="0.01"
        placeholder="0,00"
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className={FIELD}
      />
    </label>
  );
}
