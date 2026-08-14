import { useEffect, useRef, useState } from "react";
import { Route, Routes, useLocation, useNavigate, useSearchParams } from "react-router";
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
import { AdminPanel } from "./features/admin/AdminPanel";
import { MessagesDrawer } from "./features/messaging/MessagesDrawer";
import { MyPins } from "./features/events/MyPins";
import { useSocket } from "./features/realtime/useSocket";
import { strings } from "./i18n/strings";
import { boardInPath, filtersToSearch, useBoardStore } from "./stores/boardStore";
import { Toaster, toast } from "./ui/toast";
import { FiltersIcon } from "./ui/icons";

const SHARED_BOARD_ROUTES = /^\/(events\/|new$|$)/;

const VERIFIED_MESSAGES: Record<string, string> = {
  "1": strings.verify.confirmed,
  already: strings.verify.already,
  expired: strings.verify.expired,
  invalid: strings.verify.invalid,
};

export function App() {
  const filters = useBoardStore((s) => s.filters);
  const toggleSidebar = useBoardStore((s) => s.toggleSidebar);
  const setBoard = useBoardStore((s) => s.setBoard);
  const [, setSearchParams] = useSearchParams();
  const { pathname } = useLocation();
  const navigate = useNavigate();
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

  useEffect(() => {
    const named = boardInPath(pathname);
    const saysShared = named === null && SHARED_BOARD_ROUTES.test(pathname);
    if (named !== null && named !== filters.board) setBoard(named);
    else if (saysShared && filters.board) setBoard(null);
  }, [pathname, filters.board, setBoard]);

  useEffect(() => {
    if (me === null && boardInPath(pathname)) navigate("/", { replace: true });
  }, [me, pathname, navigate]);

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
          <Route path="/boards/:ownerId" element={null} />
          <Route path="/events/:id" element={<EventDrawer />} />
          <Route path="/boards/:ownerId/events/:id" element={<EventDrawer />} />
          <Route path="/new" element={<CreateEventFlow />} />
          <Route path="/boards/:ownerId/new" element={<CreateEventFlow />} />
          <Route path="/me/pins" element={<MyPins />} />
          <Route path="/me/account" element={<AccountModal />} />
          <Route path="/admin/reports" element={<AdminPanel tab="reports" />} />
          <Route path="/admin/features" element={<AdminPanel tab="features" />} />
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
