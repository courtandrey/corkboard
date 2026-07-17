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

  const suggestions =
    input.trim().length > 0
      ? (data?.items ?? []).filter((t) => !value.includes(t.name)).slice(0, 6)
      : [];

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
    <div>
      <div style={{ display: "flex", flexWrap: "wrap", gap: 4, marginBottom: 4 }}>
        {value.map((tag) => (
          <button key={tag} type="button" className="tag-chip" onClick={() => remove(tag)}>
            {tag} ×
          </button>
        ))}
      </div>
      {value.length < max && (
        <input
          value={input}
          placeholder={strings.tags.inputPlaceholder}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              e.preventDefault();
              add(input);
            }
          }}
        />
      )}
      {suggestions.length > 0 && (
        <div style={{ display: "flex", flexWrap: "wrap", gap: 4, marginTop: 4 }}>
          {suggestions.map((t) => (
            <button key={t.slug} type="button" className="tag-chip" onClick={() => add(t.name)}>
              {t.name} ({t.usageCount})
            </button>
          ))}
          {input.trim().length >= 2 &&
            !suggestions.some((t) => t.name.toLowerCase() === input.trim().toLowerCase()) && (
              <button type="button" className="tag-chip" onClick={() => add(input)}>
                {strings.tags.addFree(input.trim())}
              </button>
            )}
        </div>
      )}
    </div>
  );
}
