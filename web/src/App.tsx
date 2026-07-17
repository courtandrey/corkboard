import { useEffect } from "react";
import { Route, Routes, useSearchParams } from "react-router";
import { TopBar } from "./app/TopBar";
import { FilterSidebar } from "./features/board/FilterSidebar";
import { BoardMap } from "./features/board/BoardMap";
import { EventDrawer } from "./features/events/EventDrawer";
import { CreateEventFlow } from "./features/events/CreateEventFlow";
import { AuthDrawer } from "./features/auth/AuthDrawer";
import { filtersToSearch, useBoardStore } from "./stores/boardStore";

export function App() {
  const filters = useBoardStore((s) => s.filters);
  const [, setSearchParams] = useSearchParams();

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
          <Route path="/login" element={<AuthDrawer />} />
          <Route path="*" element={null} />
        </Routes>
      </div>
    </div>
  );
}
