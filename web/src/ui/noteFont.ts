import { useEffect, useReducer } from "react";

const NOTE_FONT = '700 27px "Caveat"';

export function loadNoteFont(text: string): Promise<unknown> {
  if (!text || !document.fonts) return Promise.resolve();
  return document.fonts.load(NOTE_FONT, text).catch(() => undefined);
}

export function noteFontReady(text: string): boolean {
  if (!text || !document.fonts) return true;
  return document.fonts.check(NOTE_FONT, text);
}

export function useNoteFontReady(text: string | undefined): boolean {
  const [, recheck] = useReducer((n: number) => n + 1, 0);

  const ready = !text || noteFontReady(text);

  useEffect(() => {
    if (ready || !text) return;
    let current = true;
    void loadNoteFont(text).finally(() => {
      if (current) recheck();
    });
    return () => {
      current = false;
    };
  }, [text, ready]);

  return ready;
}
