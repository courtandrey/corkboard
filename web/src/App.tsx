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
import { filtersToSearch, useBoardStore } from "./stores/boardStore";

export function App() {
  const filters = useBoardStore((s) => s.filters);
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
    </div>
  );
}
