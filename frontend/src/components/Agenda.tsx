import { useState } from "react";
import { Link } from "react-router-dom";
import {
  type AgendaItem,
  dismissAgendaItem,
  readAgendaIds,
  serializeAgendaIds,
} from "../lib/agenda.ts";
import { formatIsoDate } from "../lib/format.ts";
import { Button } from "./Button.tsx";
import { Chip } from "./Chip.tsx";

const STORAGE_KEY = "portfolio.agenda.read";

function loadRead(): ReadonlySet<string> {
  try {
    return readAgendaIds(localStorage.getItem(STORAGE_KEY));
  } catch {
    return new Set();
  }
}

function saveRead(ids: ReadonlySet<string>): void {
  try {
    localStorage.setItem(STORAGE_KEY, serializeAgendaIds(ids));
  } catch {
    // A browser with storage disabled keeps the dismissal for this render only.
  }
}

// The agenda's lifecycle is manual: an item leaves the list only when the owner marks it read, and
// the read ids persist in localStorage so the dismissal survives a reload.
export function Agenda({ items }: { items: AgendaItem[] }) {
  const [read, setRead] = useState<ReadonlySet<string>>(loadRead);
  const visible = items.filter((item) => !read.has(item.id));

  function dismiss(id: string) {
    const next = dismissAgendaItem(read, id);
    saveRead(next);
    setRead(next);
  }

  if (visible.length === 0) {
    return <p className="text-[13px] text-ink-dim">Nada na agenda.</p>;
  }

  return (
    <ul data-testid="agenda" className="divide-y divide-line-soft">
      {visible.map((item) => (
        <li
          key={item.id}
          data-testid={`agenda-${item.kind.toLowerCase()}`}
          className="flex flex-wrap items-center gap-3 py-2.5 first:pt-0 last:pb-0"
        >
          <Chip tone={item.kind === "CLOSE" ? "amber" : "blue"}>
            {item.kind === "CLOSE" ? "Fechamento" : "Renda fixa"}
          </Chip>
          <div className="min-w-0 flex-1">
            <Link to={item.to} className="text-[13px] font-medium text-ink hover:underline">
              {item.title}
            </Link>
            <div className="text-[11.5px] text-ink-faint">{item.detail}</div>
          </div>
          <span className="font-mono text-[11.5px] text-ink-faint">{formatIsoDate(item.date)}</span>
          <Button
            variant="ghost"
            className="px-2 py-0.5 text-[11px]"
            onClick={() => dismiss(item.id)}
            data-testid={`agenda-dismiss-${item.id}`}
          >
            Marcar como lida
          </Button>
        </li>
      ))}
    </ul>
  );
}
