import { useNavigate } from "react-router";
import { useMeta, useTagSearch } from "../../api/hooks";
import { strings } from "../../i18n/strings";
import { useBoardStore } from "../../stores/boardStore";

export function FilterSidebar() {
  const { data: meta } = useMeta();
  const { data: topTags } = useTagSearch("");
  const filters = useBoardStore((s) => s.filters);
  const toggleType = useBoardStore((s) => s.toggleType);
  const setFilters = useBoardStore((s) => s.setFilters);
  const navigate = useNavigate();

  function toggleTag(slug: string) {
    setFilters({
      tags: filters.tags.includes(slug)
        ? filters.tags.filter((t) => t !== slug)
        : [...filters.tags, slug],
    });
  }

  return (
    <aside className="sidebar">
      <button type="button" className="primary" onClick={() => navigate("/new")}>
        {strings.board.pinANote}
      </button>
      <div className="panel">
        <h3>{strings.board.filtersTitle}</h3>
        {meta?.types.map((t) => (
          <label key={t.key} className="type-row">
            <input
              type="checkbox"
              checked={filters.types.length === 0 || filters.types.includes(t.key)}
              onChange={() => {
                if (filters.types.length === 0) {
                  setFilters({ types: meta.types.map((x) => x.key).filter((k) => k !== t.key) });
                } else {
                  toggleType(t.key);
                }
              }}
            />
            <span className="type-dot" style={{ background: t.color }} />
            {t.label}
          </label>
        ))}
        <label className="type-row" style={{ marginTop: 8 }}>
          <input
            type="checkbox"
            checked={filters.applyableOnly}
            onChange={(e) => setFilters({ applyableOnly: e.target.checked })}
          />
          {strings.board.applyableOnly}
        </label>
      </div>
      {topTags && topTags.items.length > 0 && (
        <div className="panel">
          <h3>{strings.tags.popularTitle}</h3>
          <div style={{ display: "flex", flexWrap: "wrap", gap: 4 }}>
            {topTags.items.map((t) => (
              <button
                key={t.slug}
                type="button"
                className={`tag-chip${filters.tags.includes(t.slug) ? " active" : ""}`}
                onClick={() => toggleTag(t.slug)}
              >
                {t.name}
              </button>
            ))}
          </div>
        </div>
      )}
    </aside>
  );
}
