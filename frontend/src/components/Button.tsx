import type { ButtonHTMLAttributes } from "react";
import { cn } from "../lib/cn.ts";

export type ButtonVariant = "default" | "primary" | "ghost" | "danger";

const BASE =
  "inline-flex items-center gap-2 rounded-md border px-3.5 py-1.5 text-[12.5px] font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-45";

// primary = the amber action colour (excesso · vender · ação primária); danger = the rose tax/alerta
// colour. Variant names, not colour names, so callers stay on the convention.
const VARIANTS: Record<ButtonVariant, string> = {
  default: "border-line bg-surface-2 text-ink hover:border-surface-3 hover:bg-surface-3",
  primary:
    "border-accent bg-accent font-semibold text-accent-ink hover:border-accent-2 hover:bg-accent-2",
  ghost: "border-transparent bg-transparent text-ink hover:bg-surface-2",
  danger: "border-tax-bg bg-transparent text-tax hover:bg-tax-bg",
};

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
};

export function Button({ variant = "default", className, ...props }: ButtonProps) {
  return <button type="button" className={cn(BASE, VARIANTS[variant], className)} {...props} />;
}
