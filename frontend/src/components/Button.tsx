import type { ButtonHTMLAttributes } from "react";
import { cn } from "../lib/cn.ts";

export type ButtonVariant = "default" | "primary" | "ghost" | "danger";

const BASE =
  "inline-flex items-center gap-2 rounded-md border px-3.5 py-1.5 text-body font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-45";

// primary = the action blue; danger = the return− red. Variant names, not colour names, so callers
// stay on the console convention.
const VARIANTS: Record<ButtonVariant, string> = {
  default: "border-line bg-panel-2 text-text hover:border-panel-3 hover:bg-panel-3",
  primary: "border-action bg-action font-semibold text-bg hover:brightness-110",
  ghost: "border-transparent bg-transparent text-text hover:bg-panel-2",
  danger: "border-return-down-bg bg-transparent text-return-down hover:bg-return-down-bg",
};

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
};

export function Button({ variant = "default", className, ...props }: ButtonProps) {
  return <button type="button" className={cn(BASE, VARIANTS[variant], className)} {...props} />;
}
