import { type ReactNode, useId } from "react";
import {
  type FilterBarField,
  type FilterControl,
  type FilterValues,
  filterCountLabel,
  hasActiveFilter,
} from "../lib/filters.ts";

const CONTROL =
  "rounded-sm border border-line bg-surface-2 px-2 py-1.5 font-mono text-[12px] text-ink placeholder:text-ink-faint focus:border-accent focus:outline-none";

const LABEL =
  "flex items-center gap-2 font-mono text-[10px] font-medium tracking-[.09em] text-ink-faint uppercase";

const CLEAR = "text-[10.5px] tracking-[.06em] text-ink-dim uppercase hover:text-ink";

function renderControl(
  control: FilterControl,
  id: string,
  value: string,
  onChange: (value: string) => void,
) {
  switch (control.kind) {
    case "select":
      return (
        <select
          id={id}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          className={CONTROL}
        >
          <option value="">{control.emptyLabel}</option>
          {control.options.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      );
    case "date":
      return (
        <input
          id={id}
          type="date"
          value={value}
          onChange={(event) => onChange(event.target.value)}
          className={CONTROL}
        />
      );
    case "text":
      return (
        <input
          id={id}
          type="text"
          value={value}
          onChange={(event) => onChange(event.target.value)}
          placeholder={control.placeholder}
          spellCheck={false}
          autoComplete="off"
          className={CONTROL}
        />
      );
  }
}

// The one bar shape for every unbounded list (#367): text fields and selects, a live `n de m`, a
// `limpar` only while narrowing, and the narrowing empty state offering the same way out. The page
// owns the values (page-scoped state, never the URL) and passes the already-filtered host as
// children, so a keystroke re-renders the host without rebuilding the controls under the caret.
export function FilterBar({
  fields,
  values,
  onChange,
  onClear,
  shown,
  total,
  children,
}: {
  fields: readonly FilterBarField[];
  values: FilterValues;
  onChange: (key: string, value: string) => void;
  onClear: () => void;
  shown: number;
  total: number;
  children?: ReactNode;
}) {
  const narrowing = hasActiveFilter(values);
  const baseId = useId();

  return (
    <div>
      <div className="mb-3 flex flex-wrap items-center gap-3 rounded-sm border border-line bg-surface px-3 py-2">
        {fields.map((field) => {
          const id = `${baseId}-${field.control.key}`;
          return (
            <label key={field.control.key} htmlFor={id} className={LABEL}>
              {field.control.label}
              {renderControl(field.control, id, values[field.control.key] ?? "", (value) =>
                onChange(field.control.key, value),
              )}
            </label>
          );
        })}
        <span data-testid="filter-count" className="ml-auto font-mono text-[11.5px] text-ink-dim">
          {filterCountLabel(shown, total)}
        </span>
        {narrowing && (
          <button type="button" onClick={onClear} className={CLEAR}>
            limpar
          </button>
        )}
      </div>

      {shown === 0 && (total > 0 || narrowing) ? (
        <p className="rounded-lg border border-line bg-surface px-[18px] py-4 text-[13px] text-ink-dim">
          Nenhuma linha com esse filtro.{" "}
          <button type="button" onClick={onClear} className={CLEAR}>
            limpar
          </button>
        </p>
      ) : (
        children
      )}
    </div>
  );
}
