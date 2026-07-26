import { useCallback, useState } from "react";
import type { FormEvent } from "react";
import { Link, useNavigate } from "react-router";
import { useQueryClient } from "@tanstack/react-query";
import { api } from "../api/client";
import { useMe } from "../api/hooks";
import { NotificationsBell } from "../features/notifications/NotificationsBell";
import { strings } from "../i18n/strings";
import { useBoardStore } from "../stores/boardStore";
import { PixelAvatar } from "../ui/PixelAvatar";
import { pushpinDataUri } from "../ui/pushpin";
import { useDismiss } from "../ui/useDismiss";
import { ChatIcon, ChevronDownIcon, PinIcon, SearchIcon, SignOutIcon } from "../ui/icons";

const logoPin = pushpinDataUri("#C94C4C");

export function TopBar() {
  const { data: me } = useMe();
  const q = useBoardStore((s) => s.filters.q);
  const setFilters = useBoardStore((s) => s.setFilters);
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [menuOpen, setMenuOpen] = useState(false);
  const closeMenu = useCallback(() => setMenuOpen(false), []);
  const menuRef = useDismiss<HTMLDivElement>(menuOpen, closeMenu);

  function onSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const value = new FormData(event.currentTarget).get("q");
    setFilters({ q: String(value ?? "") });
  }

  async function signOut() {
    setMenuOpen(false);
    await api.post("/api/v1/auth/logout");
    await queryClient.invalidateQueries({ queryKey: ["auth", "me"] });
    navigate("/");
  }

  const [before, after] = strings.appName.split(/o(.*)/s);

  return (
    <header className="topbar">
      <Link to="/" className="logo" aria-label={strings.appName}>
        <span>{before}</span>
        <img className="logo-pin" src={logoPin} alt="o" />
        <span>{after}</span>
      </Link>
      <form className="search" onSubmit={onSearch}>
        <SearchIcon size={15} className="search-icon" />
        <input
          type="search"
          name="q"
          defaultValue={q}
          placeholder={strings.board.searchPlaceholder}
          aria-label={strings.board.searchPlaceholder}
        />
      </form>
      <span className="spacer" />
      {me ? (
        <>
          <nav className="topbar-links">
            <Link to="/me/pins" className="nav-link">
              <PinIcon size={16} /> {strings.myPins.title}
            </Link>
            <Link to="/messages" className="nav-link">
              <ChatIcon size={16} /> {strings.messagesUi.title}
            </Link>
          </nav>
          <NotificationsBell />
          <div className="user-menu" ref={menuRef}>
            <button
              type="button"
              className="whoami"
              aria-haspopup="menu"
              aria-expanded={menuOpen}
              aria-label={strings.auth.accountMenu}
              onClick={() => setMenuOpen((open) => !open)}
            >
              <PixelAvatar seed={me.avatarSeed} size={22} />
              <span className="whoami-name">{me.displayName}</span>
              <ChevronDownIcon size={13} />
            </button>
            {menuOpen && (
              <div className="menu-panel" role="menu">
                <Link to="/me/pins" className="menu-item on-phone" role="menuitem" onClick={closeMenu}>
                  <PinIcon size={15} /> {strings.myPins.title}
                </Link>
                <Link to="/messages" className="menu-item on-phone" role="menuitem" onClick={closeMenu}>
                  <ChatIcon size={15} /> {strings.messagesUi.title}
                </Link>
                <button type="button" className="menu-item signout" role="menuitem" onClick={signOut}>
                  <SignOutIcon size={15} /> {strings.auth.signOut}
                </button>
              </div>
            )}
          </div>
        </>
      ) : (
        <Link to="/login" className="nav-link">
          {strings.auth.signIn}
        </Link>
      )}
    </header>
  );
}
