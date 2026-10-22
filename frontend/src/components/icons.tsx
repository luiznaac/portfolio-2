import type { SVGProps } from "react";

// The reference's line icons (frontend/design/front-prototype.html), 16px grid, 1.3px stroke,
// inheriting `currentColor`. Nav icons are named in lib/navigation.ts and resolved here.

type IconProps = SVGProps<SVGSVGElement>;

export function BrandMark(props: IconProps) {
  return (
    <svg width={22} height={22} viewBox="0 0 22 22" fill="none" aria-hidden="true" {...props}>
      <circle cx={11} cy={11} r={9} stroke="var(--color-accent)" strokeWidth={1.6} />
      <path d="M11 4v7l5 3" stroke="var(--color-accent)" strokeWidth={1.6} strokeLinecap="round" />
    </svg>
  );
}

export function IconMenu(props: IconProps) {
  return (
    <svg width={15} height={15} viewBox="0 0 15 15" fill="none" aria-hidden="true" {...props}>
      <path
        d="M2 4h11M2 7.5h11M2 11h11"
        stroke="currentColor"
        strokeWidth={1.4}
        strokeLinecap="round"
      />
    </svg>
  );
}

export function IconUpload(props: IconProps) {
  return (
    <svg width={16} height={16} viewBox="0 0 16 16" fill="none" aria-hidden="true" {...props}>
      <path
        d="M8 10V3M5 6l3-3 3 3M3 12h10"
        stroke="currentColor"
        strokeWidth={1.3}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

export function IconAssets(props: IconProps) {
  return (
    <svg width={16} height={16} viewBox="0 0 16 16" fill="none" aria-hidden="true" {...props}>
      <circle cx={8} cy={8} r={6} stroke="currentColor" strokeWidth={1.3} />
    </svg>
  );
}

export function IconBank(props: IconProps) {
  return (
    <svg width={16} height={16} viewBox="0 0 16 16" fill="none" aria-hidden="true" {...props}>
      <path
        d="M8 2l6 3H2l6-3ZM3 6v6M6 6v6M10 6v6M13 6v6M2 13h12"
        stroke="currentColor"
        strokeWidth={1.3}
        strokeLinecap="round"
      />
    </svg>
  );
}

export function IconCalendar(props: IconProps) {
  return (
    <svg width={16} height={16} viewBox="0 0 16 16" fill="none" aria-hidden="true" {...props}>
      <rect
        x={2.5}
        y={3.5}
        width={11}
        height={10}
        rx={1.5}
        stroke="currentColor"
        strokeWidth={1.3}
      />
      <path
        d="M2.5 6.5h11M5.5 2v3M10.5 2v3"
        stroke="currentColor"
        strokeWidth={1.3}
        strokeLinecap="round"
      />
    </svg>
  );
}

export function IconList(props: IconProps) {
  return (
    <svg width={16} height={16} viewBox="0 0 16 16" fill="none" aria-hidden="true" {...props}>
      <path
        d="M5.5 4h8M5.5 8h8M5.5 12h8M2.5 4h.01M2.5 8h.01M2.5 12h.01"
        stroke="currentColor"
        strokeWidth={1.3}
        strokeLinecap="round"
      />
    </svg>
  );
}

export function IconTarget(props: IconProps) {
  return (
    <svg width={16} height={16} viewBox="0 0 16 16" fill="none" aria-hidden="true" {...props}>
      <circle cx={8} cy={8} r={6} stroke="currentColor" strokeWidth={1.3} />
      <circle cx={8} cy={8} r={2.5} stroke="currentColor" strokeWidth={1.3} />
    </svg>
  );
}

export function IconLayers(props: IconProps) {
  return (
    <svg width={16} height={16} viewBox="0 0 16 16" fill="none" aria-hidden="true" {...props}>
      <path
        d="M8 2 2 5l6 3 6-3-6-3Z"
        stroke="currentColor"
        strokeWidth={1.3}
        strokeLinejoin="round"
      />
      <path
        d="m2 8 6 3 6-3M2 11l6 3 6-3"
        stroke="currentColor"
        strokeWidth={1.3}
        strokeLinejoin="round"
      />
    </svg>
  );
}

export function IconEvent(props: IconProps) {
  return (
    <svg width={16} height={16} viewBox="0 0 16 16" fill="none" aria-hidden="true" {...props}>
      <path
        d="M8 2v2M8 12v2M2 8h2M12 8h2M3.8 3.8l1.4 1.4M10.8 10.8l1.4 1.4M12.2 3.8l-1.4 1.4M5.2 10.8l-1.4 1.4"
        stroke="currentColor"
        strokeWidth={1.3}
        strokeLinecap="round"
      />
      <circle cx={8} cy={8} r={2.2} stroke="currentColor" strokeWidth={1.3} />
    </svg>
  );
}

export function IconCoins(props: IconProps) {
  return (
    <svg width={16} height={16} viewBox="0 0 16 16" fill="none" aria-hidden="true" {...props}>
      <ellipse cx={8} cy={4.5} rx={5.5} ry={2.5} stroke="currentColor" strokeWidth={1.3} />
      <path
        d="M2.5 4.5v7c0 1.38 2.46 2.5 5.5 2.5s5.5-1.12 5.5-2.5v-7"
        stroke="currentColor"
        strokeWidth={1.3}
      />
      <path
        d="M2.5 8c0 1.38 2.46 2.5 5.5 2.5s5.5-1.12 5.5-2.5"
        stroke="currentColor"
        strokeWidth={1.3}
      />
    </svg>
  );
}
