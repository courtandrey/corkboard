function shade(hex: string, factor: number): string {
  const n = parseInt(hex.replace("#", ""), 16);
  const channel = (shift: number) =>
    Math.max(0, Math.min(255, Math.round(((n >> shift) & 0xff) * factor)));
  return `#${((channel(16) << 16) | (channel(8) << 8) | channel(0)).toString(16).padStart(6, "0")}`;
}

export function pushpinSvg(color: string, pressed = false): string {
  const uid = `p${color.replace("#", "")}${pressed ? "x" : ""}`;
  const rim = shade(color, 0.72);
  const glow = shade(color, 1.18);
  const headY = pressed ? 13.5 : 9;
  const headR = pressed ? 7.4 : 8;
  const needle = pressed
    ? `<path d="M11.2 20.4 L12.8 20.4 L12.4 24.5 L11.6 24.5 Z" fill="#8a8f97"/>`
    : `<path d="M11.1 16.2 L12.9 16.2 L12.35 29.4 L11.65 29.4 Z" fill="#8a8f97"/>
       <path d="M11.1 16.2 L12 16.2 L11.75 29.2 L11.65 29.2 Z" fill="#c9cdd3"/>`;
  const shadow = pressed
    ? `<ellipse cx="12.6" cy="24.6" rx="6.2" ry="1.6" fill="rgba(46,32,14,0.30)"/>`
    : `<ellipse cx="13.4" cy="29.6" rx="5.6" ry="1.7" fill="rgba(46,32,14,0.28)"/>`;
  return `<svg xmlns="http://www.w3.org/2000/svg" width="24" height="32" viewBox="0 0 24 32">
  <defs>
    <radialGradient id="${uid}" cx="0.38" cy="0.32" r="0.85">
      <stop offset="0" stop-color="${glow}"/>
      <stop offset="0.62" stop-color="${color}"/>
      <stop offset="1" stop-color="${rim}"/>
    </radialGradient>
  </defs>
  ${shadow}
  ${needle}
  <circle cx="12" cy="${headY}" r="${headR}" fill="url(#${uid})"/>
  <circle cx="12" cy="${headY}" r="${headR}" fill="none" stroke="${rim}" stroke-width="0.6" opacity="0.7"/>
  <ellipse cx="${pressed ? 9.6 : 9.4}" cy="${headY - headR * 0.42}" rx="2.4" ry="1.5"
           transform="rotate(-28 ${pressed ? 9.6 : 9.4} ${headY - headR * 0.42})" fill="rgba(255,255,255,0.75)"/>
</svg>`;
}

export function pushpinDataUri(color: string, pressed = false): string {
  return `data:image/svg+xml,${encodeURIComponent(pushpinSvg(color, pressed))}`;
}
