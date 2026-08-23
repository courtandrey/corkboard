import { useCallback, useEffect, useRef, useState } from "react";
import type { KeyboardEvent } from "react";
import { PLACE_MIN_QUERY, usePlaceSearch } from "../../api/hooks";
import type { PlaceSuggestion } from "../../api/client";
import { strings } from "../../i18n/strings";
import { CloseIcon, SearchIcon } from "../../ui/icons";
import { useDebounced } from "../../ui/useDebounced";
import { useDismiss } from "../../ui/useDismiss";

const TYPING_PAUSE_MS = 1000;
const LIST_ID = "address-matches";

export function AddressSearch({
  near,
  onPick,
}: {
  near: string | undefined;
  onPick: (place: PlaceSuggestion) => void;
}) {
  const [text, setText] = useState("");
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const typed = useDebounced(text, TYPING_PAUSE_MS);

  const close = useCallback(() => setOpen(false), []);
  const boxRef = useDismiss<HTMLDivElement>(open, close);

  const { data, isFetching, isError } = usePlaceSearch(typed, near, open);
  const matches = data?.items ?? [];

  useEffect(() => setActive(0), [text]);

  function pick(place: PlaceSuggestion) {
    onPick(place);
    setText(place.name);
    setOpen(false);
    inputRef.current?.blur();
  }

  function clear() {
    setText("");
    setOpen(false);
    inputRef.current?.focus();
  }

  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === "ArrowDown" || event.key === "ArrowUp") {
      event.preventDefault();
      setOpen(true);
      if (matches.length === 0) return;
      const step = event.key === "ArrowDown" ? 1 : matches.length - 1;
      setActive((current) => (current + step) % matches.length);
    } else if (event.key === "Enter") {
      event.preventDefault();
      if (matches[active]) pick(matches[active]);
    } else if (event.key === "Escape") {
      if (open) setOpen(false);
      else clear();
    }
  }


  const query = text.trim();
  const settled = query === typed.trim() && !isFetching;
  const nothingFound = query.length >= PLACE_MIN_QUERY && settled && !isError && matches.length === 0;
  const showList =
    open && query.length >= PLACE_MIN_QUERY && (matches.length > 0 || nothingFound || isError);

  return (
    <div className="address-search" ref={boxRef}>
      <div className="address-field">
        <SearchIcon size={15} className="search-icon" />
        <input
          ref={inputRef}
          type="text"
          role="combobox"
          autoComplete="off"
          spellCheck={false}
          value={text}
          placeholder={strings.places.placeholder}
          aria-label={strings.places.placeholder}
          aria-expanded={showList}
          aria-controls={LIST_ID}
          aria-activedescendant={showList && matches[active] ? `place-${matches[active].id}` : undefined}
          onChange={(event) => {
            setText(event.target.value);
            setOpen(true);
          }}
          onFocus={() => setOpen(true)}
          onKeyDown={onKeyDown}
        />
        {text && (
          <button type="button" className="icon-btn address-clear" onClick={clear} aria-label={strings.places.clear}>
            <CloseIcon size={15} />
          </button>
        )}
      </div>
      {showList && (
        <ul className="address-matches" id={LIST_ID} role="listbox">
          {matches.map((place, index) => (
            <li key={place.id} id={`place-${place.id}`} role="option" aria-selected={index === active}>
              <button
                type="button"
                className={`address-match${index === active ? " active" : ""}`}
                onMouseEnter={() => setActive(index)}
                onClick={() => pick(place)}
              >
                <span className="address-name">{place.name}</span>
                {place.context && <span className="address-context">{place.context}</span>}
              </button>
            </li>
          ))}
          {nothingFound && <li className="address-empty">{strings.places.nothingFound}</li>}
          {isError && <li className="address-empty">{strings.places.failed}</li>}
        </ul>
      )}
    </div>
  );
}
