import { useMeta } from "../api/hooks";

function mulberry32(seed: number): () => number {
  let a = seed;
  return () => {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function hashSeed(seed: string): number {
  let h = 2166136261;
  for (let i = 0; i < seed.length; i++) {
    h ^= seed.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}

export function PixelAvatar({ seed, size = 24 }: { seed: string; size?: number }) {
  const { data: meta } = useMeta();
  const rand = mulberry32(hashSeed(seed));
  const palette = meta?.types.map((t) => t.color) ?? [];
  const color = palette.length ? palette[Math.floor(rand() * palette.length)] : "#3b5998";

  const cells: boolean[][] = [];
  for (let row = 0; row < 5; row++) {
    const half = [rand() > 0.45, rand() > 0.45, rand() > 0.45];
    cells.push([half[0], half[1], half[2], half[1], half[0]]);
  }

  return (
    <svg
      className="avatar"
      width={size}
      height={size}
      viewBox="0 0 5 5"
      shapeRendering="crispEdges"
      role="img"
      aria-hidden="true"
    >
      <rect width="5" height="5" fill="#ffffff" />
      {cells.flatMap((row, y) =>
        row.map((on, x) =>
          on ? <rect key={`${x}-${y}`} x={x} y={y} width="1" height="1" fill={color} /> : null,
        ),
      )}
    </svg>
  );
}
