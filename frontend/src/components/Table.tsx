import type { ReactNode } from "react";
import { cn } from "../lib/cn.ts";

export interface Column<T> {
  key: string;
  header: string;
  align?: "right";
  render: (row: T) => ReactNode;
}

// The reference table shell: bordered, rounded, sticky mono-uppercase head, hairline row rules.
// Data-driven so a page declares its columns once.
export function Table<T>({
  columns,
  rows,
  rowKey,
}: {
  columns: Column<T>[];
  rows: T[];
  rowKey: (row: T) => string | number;
}) {
  return (
    <div className="overflow-x-auto rounded-lg border border-line">
      <table className="w-full border-collapse text-[13px]">
        <thead>
          <tr>
            {columns.map((column) => (
              <th
                key={column.key}
                className={cn(
                  "sticky top-0 z-[1] bg-surface-2 px-3 py-2.5 font-mono text-[10px] font-medium tracking-[.09em] text-ink-faint uppercase whitespace-nowrap",
                  column.align === "right" ? "text-right" : "text-left",
                )}
              >
                {column.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={rowKey(row)} className="bg-surface">
              {columns.map((column) => (
                <td
                  key={column.key}
                  className={cn(
                    "border-t border-line-soft px-3 py-2.5 align-middle",
                    column.align === "right" ? "text-right" : "text-left",
                  )}
                >
                  {column.render(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
