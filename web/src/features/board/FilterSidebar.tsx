import type { FormEvent } from "react";
import { useNavigate } from "react-router";
import { useTagSearch } from "../../api/hooks";
import { strings } from "../../i18n/strings";
import { newNotePath } from "../../api/paths";
import { useBoardStore } from "../../stores/boardStore";
import { useScopeTypes } from "../../ui/scope";
import { CloseIcon, PlusIcon, SearchIcon } from "../../ui/icons";

export function FilterSidebar() {
  const { data: topTags } = useTagSearch("");
  const filters = useBoardStore((s) => s.filters);
  const types = useScopeTypes(filters.board);
  const shared = filters.board === null;
  const toggleType = useBoardStore((s) => s.toggleType);
  const setFilters = useBoardStore((s) => s.setFilters);
  const sidebarOpen = useBoardStore((s) => s.sidebarOpen);
  const toggleSidebar = useBoardStore((s) => s.toggleSidebar);
  const navigate = useNavigate();

  function onSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const value = new FormData(event.currentTarget).get("q");
    setFilters({ q: String(value ?? "") });
    if (sidebarOpen) toggleSidebar();
  }

  function toggleTag(slug: string) {
    setFilters({
      tags: filters.tags.includes(slug)
        ? filters.tags.filter((t) => t !== slug)
        : [...filters.tags, slug],
    });
  }

  return (
    <aside className={`sidebar${sidebarOpen ? " open" : ""}`}>
      <div className="sidebar-head">
        <h2>{strings.board.filtersToggle}</h2>
        <button type="button" className="icon-btn" onClick={toggleSidebar} aria-label={strings.event.close}>
          <CloseIcon size={18} />
        </button>
      </div>
      <button
        type="button"
        className="primary pin-cta"
        onClick={() => {
          if (sidebarOpen) toggleSidebar();
          navigate(newNotePath(filters.board));
        }}
      >
        <PlusIcon size={17} /> {strings.board.pinANote}
      </button>
      <div className="panel">
        <h3>{strings.board.filtersTitle}</h3>
        <form className="search sidebar-search" onSubmit={onSearch}>
          <SearchIcon size={15} className="search-icon" />
          <input
            type="search"
            name="q"
            defaultValue={filters.q}
            placeholder={strings.board.searchPlaceholder}
            aria-label={strings.board.searchPlaceholder}
          />
        </form>
        {types.map((t) => (
          <label key={t.key} className="type-row">
            <input
              type="checkbox"
              checked={filters.types.length === 0 || filters.types.includes(t.key)}
              onChange={() => {
                if (filters.types.length === 0) {
                  setFilters({ types: types.map((x) => x.key).filter((k) => k !== t.key) });
                } else {
                  toggleType(t.key);
                }
              }}
            />
            <span className="type-dot" style={{ background: t.color }} />
            {t.label}
          </label>
        ))}
        {shared && (
          <label className="type-row applyable-row">
            <input
              type="checkbox"
              checked={filters.applyableOnly}
              onChange={(e) => setFilters({ applyableOnly: e.target.checked })}
            />
            {strings.board.applyableOnly}
          </label>
        )}
      </div>
      {shared && topTags && topTags.items.length > 0 && (
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
      <button type="button" className="primary block sidebar-done" onClick={toggleSidebar}>
        {strings.board.filtersDone}
      </button>
    </aside>
  );
}
