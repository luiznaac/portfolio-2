import type { ReactNode } from "react";
import { cn } from "../lib/cn.ts";

export interface Column<T> {
  key: string;
  header: string;
  align?: "right";
  render: (row: T) => ReactNode;
}

// The console table shell: a 3 px panel radius, a sticky mono-uppercase head, 26 px rows on 1 px
// hairlines. Data-driven so a page declares its columns once.
export function Table<T>({
  columns,
  rows,
  rowKey,
  footer,
  onRowClick,
  rowClassName,
  rowId,
}: {
  columns: Column<T>[];
  rows: T[];
  rowKey: (row: T) => string | number;
  footer?: ReactNode;
  onRowClick?: (row: T) => void;
  // A row's own background replaces the panel default, so a deep-linked row can carry the
  // attention highlight without a second colour token.
  rowClassName?: (row: T) => string | undefined;
  // A stable element id, so a deep link can scroll its target row into view.
  rowId?: (row: T) => string;
}) {
  return (
    <div className="overflow-x-auto rounded-panel border border-line">
      <table className="w-full border-collapse text-num">
        <thead>
          <tr>
            {columns.map((column) => (
              <th
                key={column.key}
                className={cn(
                  "sticky top-0 z-[1] h-[var(--height-row)] bg-panel-2 px-2.5 font-mono text-label font-semibold tracking-[.1em] text-faint uppercase whitespace-nowrap",
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
            <tr
              key={rowKey(row)}
              id={rowId?.(row)}
              onClick={onRowClick === undefined ? undefined : () => onRowClick(row)}
              onKeyDown={
                onRowClick === undefined
                  ? undefined
                  : (event) => {
                      if (event.key !== "Enter" && event.key !== " ") return;
                      event.preventDefault();
                      onRowClick(row);
                    }
              }
              tabIndex={onRowClick === undefined ? undefined : 0}
              className={cn(
                rowClassName?.(row) ?? "bg-panel",
                onRowClick !== undefined && "cursor-pointer hover:bg-panel-2",
              )}
            >
              {columns.map((column) => (
                <td
                  key={column.key}
                  className={cn(
                    "h-[var(--height-row)] border-t border-line-soft px-2.5 align-middle",
                    column.align === "right" ? "text-right" : "text-left",
                  )}
                >
                  {column.render(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
        {footer !== undefined && <tfoot>{footer}</tfoot>}
      </table>
    </div>
  );
}
