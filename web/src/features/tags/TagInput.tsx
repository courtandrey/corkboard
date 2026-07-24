import { useState } from "react";
import { useTagSearch } from "../../api/hooks";
import { strings } from "../../i18n/strings";

export function TagInput({
  value,
  onChange,
  max = 5,
}: {
  value: string[];
  onChange: (tags: string[]) => void;
  max?: number;
}) {
  const [input, setInput] = useState("");
  const { data } = useTagSearch(input.trim());

  const typed = input.trim();
  const full = value.length >= max;
  const suggestions =
    typed.length > 0 && !full
      ? (data?.items ?? []).filter((t) => !value.includes(t.name)).slice(0, 6)
      : [];
  const canAddFree =
    typed.length >= 2 && !full && !suggestions.some((t) => t.name.toLowerCase() === typed.toLowerCase());

  function add(name: string) {
    const trimmed = name.trim();
    if (!trimmed || value.includes(trimmed) || value.length >= max) return;
    onChange([...value, trimmed]);
    setInput("");
  }

  function remove(name: string) {
    onChange(value.filter((t) => t !== name));
  }

  return (
    <div className="tag-input">
      <div className="tag-input-chips">
        {value.map((tag) => (
          <button key={tag} type="button" className="tag-chip" onClick={() => remove(tag)}>
            {tag} ×
          </button>
        ))}
      </div>
      <input
        value={input}
        disabled={full}
        placeholder={strings.tags.inputPlaceholder}
        onChange={(e) => setInput(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === "Enter") {
            e.preventDefault();
            add(input);
          }
        }}
      />
      <div className="tag-suggestions">
        {suggestions.map((t) => (
          <button key={t.slug} type="button" className="tag-chip" onClick={() => add(t.name)}>
            {t.name} ({t.usageCount})
          </button>
        ))}
        {canAddFree && (
          <button type="button" className="tag-chip" onClick={() => add(typed)}>
            {strings.tags.addFree(typed)}
          </button>
        )}
      </div>
    </div>
  );
}
