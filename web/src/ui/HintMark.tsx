export function HintMark({ hint }: { hint: string }) {
  return (
    <span className="hint-mark" title={hint} aria-hidden="true">
      ?
    </span>
  );
}
