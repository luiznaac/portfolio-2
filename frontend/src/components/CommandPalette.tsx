import { type KeyboardEvent, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { NAV_GROUPS } from "../lib/navigation.ts";
import { type CommandItem, commandItems, filterCommands } from "../lib/palette.ts";

// The nav table is the palette's only registry: a new route is searchable the moment it joins the
// sidebar. Ctrl/Cmd+K is bound by the shell (Layout); this component owns the list and its keyboard.
const COMMANDS = commandItems(NAV_GROUPS);

export function CommandPalette({ onClose }: { onClose: () => void }) {
  const navigate = useNavigate();
  const [query, setQuery] = useState("");
  const [selected, setSelected] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);

  // The palette is mounted by the shell only while it is open, so focusing on mount is the whole
  // lifecycle; Ctrl/Cmd+K lands the caret in the field.
  useEffect(() => {
    inputRef.current?.focus();
  }, []);
  const items = filterCommands(COMMANDS, query);
  const active = Math.min(selected, Math.max(items.length - 1, 0));

  function choose(item: CommandItem | undefined) {
    if (item === undefined) return;
    navigate(item.to);
    onClose();
  }

  function handleKeyDown(event: KeyboardEvent<HTMLDialogElement>) {
    if (event.key === "Escape") {
      event.preventDefault();
      onClose();
    } else if (event.key === "ArrowDown") {
      event.preventDefault();
      setSelected((current) => Math.min(current + 1, Math.max(items.length - 1, 0)));
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      setSelected((current) => Math.max(current - 1, 0));
    } else if (event.key === "Enter") {
      event.preventDefault();
      choose(items[active]);
    }
  }

  return (
    <div className="fixed inset-0 z-50">
      <button
        type="button"
        aria-label="Fechar comandos"
        onClick={onClose}
        className="absolute inset-0 cursor-default bg-black/60"
      />
      <dialog
        open
        aria-label="Comandos"
        onKeyDown={handleKeyDown}
        className="relative mx-auto mt-[12vh] w-[540px] max-w-[92vw] overflow-hidden rounded-xl border border-line bg-surface p-0 text-ink shadow-2xl"
      >
        <input
          ref={inputRef}
          value={query}
          onChange={(event) => {
            setQuery(event.target.value);
            setSelected(0);
          }}
          aria-label="Buscar tela"
          placeholder="Buscar tela…"
          className="w-full border-b border-line bg-transparent px-4 py-3.5 text-[15px] text-ink outline-none placeholder:text-ink-faint"
        />
        <div className="max-h-[340px] overflow-y-auto p-1.5">
          {items.length === 0 ? (
            <p className="px-3 py-2.5 text-[13px] text-ink-faint">Nada encontrado</p>
          ) : (
            items.map((item, index) => (
              <button
                key={item.to}
                type="button"
                onClick={() => choose(item)}
                onMouseEnter={() => setSelected(index)}
                aria-current={index === active ? "true" : undefined}
                className={`flex w-full items-center gap-2.5 rounded-md px-2.5 py-2 text-left text-[13px] ${
                  index === active
                    ? "bg-surface-2 text-ink"
                    : "text-ink-dim hover:bg-surface-2 hover:text-ink"
                }`}
              >
                <span>{item.label}</span>
                <span className="ml-auto font-mono text-[10px] tracking-[.08em] text-ink-faint uppercase">
                  {item.group}
                </span>
              </button>
            ))
          )}
        </div>
      </dialog>
    </div>
  );
}
