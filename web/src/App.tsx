import { useEffect, useRef, useState } from "react";
import { Route, Routes, useSearchParams } from "react-router";
import { TopBar } from "./app/TopBar";
import { useMe } from "./api/hooks";
import { FilterSidebar } from "./features/board/FilterSidebar";
import { BoardMap } from "./features/board/BoardMap";
import { EventDrawer } from "./features/events/EventDrawer";
import { CreateEventFlow } from "./features/events/CreateEventFlow";
import { AuthDrawer } from "./features/auth/AuthDrawer";
import { VerifyBanner } from "./features/auth/VerifyBanner";
import { VerifyGate } from "./features/auth/verifyGate";
import { AccountModal } from "./features/auth/AccountModal";
import { CookieBanner } from "./features/consent/CookieBanner";
import { ModerationPanel } from "./features/moderation/ModerationPanel";
import { MessagesDrawer } from "./features/messaging/MessagesDrawer";
import { MyPins } from "./features/events/MyPins";
import { useSocket } from "./features/realtime/useSocket";
import { strings } from "./i18n/strings";
import { filtersToSearch, useBoardStore } from "./stores/boardStore";
import { Toaster, toast } from "./ui/toast";
import { FiltersIcon } from "./ui/icons";

const VERIFIED_MESSAGES: Record<string, string> = {
  "1": strings.verify.confirmed,
  already: strings.verify.already,
  expired: strings.verify.expired,
  invalid: strings.verify.invalid,
};

export function App() {
  const filters = useBoardStore((s) => s.filters);
  const toggleSidebar = useBoardStore((s) => s.toggleSidebar);
  const [, setSearchParams] = useSearchParams();
  const { data: me, refetch: refetchMe } = useMe();
  useSocket(!!me);

  const [verified] = useState(() => new URLSearchParams(window.location.search).get("verified"));
  const announced = useRef(false);

  useEffect(() => {
    if (!verified || announced.current) return;
    announced.current = true;
    toast(VERIFIED_MESSAGES[verified] ?? strings.verify.invalid, verified === "1" ? "ok" : "info");
    if (verified === "1") void refetchMe();
  }, [verified, refetchMe]);

  useEffect(() => {
    setSearchParams(filtersToSearch(filters), { replace: true });
  }, [filters, setSearchParams]);

  return (
    <div className="layout">
      <TopBar />
      <VerifyBanner />
      <div className="board">
        <FilterSidebar />
        <button type="button" className="filters-toggle" onClick={toggleSidebar}>
          <FiltersIcon size={16} /> {strings.board.filtersToggle}
        </button>
        <BoardMap />
        <Routes>
          <Route path="/" element={null} />
          <Route path="/events/:id" element={<EventDrawer />} />
          <Route path="/new" element={<CreateEventFlow />} />
          <Route path="/me/pins" element={<MyPins />} />
          <Route path="/me/account" element={<AccountModal />} />
          <Route path="/admin/reports" element={<ModerationPanel />} />
          <Route path="/messages" element={<MessagesDrawer />} />
          <Route path="/messages/:conversationId" element={<MessagesDrawer />} />
          <Route path="/login" element={<AuthDrawer />} />
          <Route path="*" element={null} />
        </Routes>
      </div>
      <CookieBanner />
      <VerifyGate />
      <Toaster />
    </div>
  );
}
