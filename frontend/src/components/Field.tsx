import {
  type InputHTMLAttributes,
  type ReactNode,
  cloneElement,
  isValidElement,
  useId,
} from "react";
import { cn } from "../lib/cn.ts";

// The reference's form field: a small label above a token-styled input. A page names a field instead
// of re-declaring the same label/input Tailwind string, so the input look stays in one place. The
// field owns the label/input association: the id is generated here and handed to the child input.
const INPUT = "rounded-sm border border-line bg-surface-2 px-2 py-1.5 text-[13px] text-ink";

export function Field({ label, children }: { label: string; children: ReactNode }) {
  const id = useId();
  const control = isValidElement<InputHTMLAttributes<HTMLInputElement>>(children)
    ? cloneElement(children, { id })
    : children;

  return (
    <label htmlFor={id} className="flex flex-col gap-1 text-[11.5px] text-ink-dim">
      {label}
      {control}
    </label>
  );
}

export function Input({ className, ...props }: InputHTMLAttributes<HTMLInputElement>) {
  return <input className={cn(INPUT, className)} {...props} />;
}
