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

const logoPin = pushpinDataUri("#C94C4C");

export function TopBar() {
  const { data: me } = useMe();
  const q = useBoardStore((s) => s.filters.q);
  const setFilters = useBoardStore((s) => s.setFilters);
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  function onSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const value = new FormData(event.currentTarget).get("q");
    setFilters({ q: String(value ?? "") });
  }

  async function signOut() {
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
      <form onSubmit={onSearch}>
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
          <Link to="/me/pins">{strings.myPins.title}</Link>
          <Link to="/messages">{strings.messagesUi.title}</Link>
          <NotificationsBell />
          <span className="whoami">
            <PixelAvatar seed={me.avatarSeed} size={22} />
            {me.displayName}
          </span>
          <button type="button" onClick={signOut}>
            {strings.auth.signOut}
          </button>
        </>
      ) : (
        <Link to="/login">{strings.auth.signIn}</Link>
      )}
    </header>
  );
}
