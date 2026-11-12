import { api } from "../api/client.ts";
import type { Import } from "../api/types.ts";
import { cn } from "../lib/cn.ts";

// The short hash the console prints for a retained original. One place decides the length, so the
// assistant header and the applied banner cannot drift.
export function shortHash(hash: string): string {
  return hash.slice(0, 12);
}

// File name, sha256 prefix and the retained original — the applied record's provenance.
export function ImportProvenance({
  record,
  className,
}: {
  record: Import;
  className?: string;
}) {
  return (
    <span className={cn("flex flex-wrap items-center gap-x-2 text-[11.5px]", className)}>
      <span className="font-mono">{record.file_name}</span>
      <span>·</span>
      <span className="font-mono">sha256 {shortHash(record.file_hash)}</span>
      {record.has_original ? (
        <>
          <span>·</span>
          <a href={api.importOriginalUrl(record.id)} className="text-accent hover:underline">
            Baixar original
          </a>
        </>
      ) : null}
    </span>
  );
}
