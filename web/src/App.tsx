import { useEffect } from "react";
import { Route, Routes, useSearchParams } from "react-router";
import { TopBar } from "./app/TopBar";
import { useMe } from "./api/hooks";
import { FilterSidebar } from "./features/board/FilterSidebar";
import { BoardMap } from "./features/board/BoardMap";
import { EventDrawer } from "./features/events/EventDrawer";
import { CreateEventFlow } from "./features/events/CreateEventFlow";
import { AuthDrawer } from "./features/auth/AuthDrawer";
import { MessagesDrawer } from "./features/messaging/MessagesDrawer";
import { MyPins } from "./features/events/MyPins";
import { useSocket } from "./features/realtime/useSocket";
import { strings } from "./i18n/strings";
import { filtersToSearch, useBoardStore } from "./stores/boardStore";
import { Toaster } from "./ui/toast";
import { FiltersIcon } from "./ui/icons";

export function App() {
  const filters = useBoardStore((s) => s.filters);
  const toggleSidebar = useBoardStore((s) => s.toggleSidebar);
  const [, setSearchParams] = useSearchParams();
  const { data: me } = useMe();
  useSocket(!!me);

  useEffect(() => {
    setSearchParams(filtersToSearch(filters), { replace: true });
  }, [filters, setSearchParams]);

  return (
    <div className="layout">
      <TopBar />
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
          <Route path="/messages" element={<MessagesDrawer />} />
          <Route path="/messages/:conversationId" element={<MessagesDrawer />} />
          <Route path="/login" element={<AuthDrawer />} />
          <Route path="*" element={null} />
        </Routes>
      </div>
      <Toaster />
    </div>
  );
}
