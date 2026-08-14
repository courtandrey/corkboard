import { Link, useNavigate } from "react-router";
import { useBoardHome } from "../../stores/boardStore";
import { strings } from "../../i18n/strings";
import { Modal } from "../../ui/Modal";
import { usePermissions } from "../../ui/permissions";
import type { Permission } from "../../ui/permissions";
import { ReportQueue } from "../moderation/ReportQueue";
import { FeatureFlagList } from "./FeatureFlagList";

const s = strings.admin;

export type AdminTab = "reports" | "features";

const tabs: { key: AdminTab; to: string; label: string; permission: Permission }[] = [
  { key: "reports", to: "/admin/reports", label: s.tabs.reports, permission: "REPORT_QUEUE_VIEW" },
  { key: "features", to: "/admin/features", label: s.tabs.features, permission: "FEATURE_FLAG_MANAGE" },
];

export function AdminPanel({ tab }: { tab: AdminTab }) {
  const navigate = useNavigate();
  const home = useBoardHome();
  const { can, loaded } = usePermissions();
  const close = () => navigate(home);

  const visible = tabs.filter((t) => can(t.permission));

  if (loaded && !visible.some((t) => t.key === tab)) {
    return (
      <Modal onClose={close} size="sm" labelledBy="admin-title">
        <div className="modal-head">
          <h2 id="admin-title">{s.title}</h2>
        </div>
        <div className="modal-body" style={{ paddingBottom: 18 }}>
          <p className="empty-state">{s.notAllowed}</p>
        </div>
      </Modal>
    );
  }

  return (
    <Modal onClose={close} size="lg" className="modal-admin" labelledBy="admin-title">
      <div className="modal-head">
        <h2 id="admin-title">{s.title}</h2>
        {visible.length > 1 && (
          <nav className="admin-tabs">
            {visible.map((t) => (
              <Link
                key={t.key}
                to={t.to}
                className={`admin-tab${t.key === tab ? " current" : ""}`}
                aria-current={t.key === tab ? "page" : undefined}
              >
                {t.label}
              </Link>
            ))}
          </nav>
        )}
      </div>
      {tab === "reports" ? <ReportQueue /> : <FeatureFlagList />}
    </Modal>
  );
}
