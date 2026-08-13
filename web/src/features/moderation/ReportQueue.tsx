import { useNavigate } from "react-router";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../../api/client";
import type { ReportQueueResponse } from "../../api/client";
import { strings } from "../../i18n/strings";
import { toast } from "../../ui/toast";
import { CheckIcon, FlagIcon, TrashIcon } from "../../ui/icons";

const s = strings.moderation;
const reasons = strings.engagement.reasons;

export function ReportQueue() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const { data, isLoading } = useQuery({
    queryKey: ["admin", "reports"],
    queryFn: () => api.get<ReportQueueResponse>("/api/v1/admin/reports"),
  });

  const act = useMutation({
    mutationFn: ({ id, action }: { id: string; action: "takedown" | "approve" }) =>
      api.post(`/api/v1/admin/events/${id}/${action}`),
    onSuccess: async (_result, { action }) => {
      toast(action === "takedown" ? s.tookDown : s.approved);
      await queryClient.invalidateQueries({ queryKey: ["admin", "reports"] });
      await queryClient.invalidateQueries({ queryKey: ["events"] });
    },
  });

  return (
    <div className="modal-body">
      <p className="form-hint">{s.intro}</p>
      {isLoading && <p className="empty-state">{strings.loading}</p>}
      {data?.items.length === 0 && <p className="empty-state">{s.empty}</p>}

      <ul className="report-queue">
        {data?.items.map((item) => (
          <li key={item.id} className="report-row">
            <div className="report-main">
              <button type="button" className="link-like" onClick={() => navigate(`/events/${item.id}`)}>
                {item.title}
              </button>
              <div className="meta-row">
                <span className={`report-status status-${item.status}`}>
                  {strings.myPins.statusHeading[item.status] ?? item.status}
                </span>
                <span>{s.by(item.authorDisplayName)}</span>
              </div>
              <div className="report-reasons">
                {item.reasons.map((r) => (
                  <span key={r.reason} className="tag-chip">
                    {reasons[r.reason] ?? r.reason} × {r.count}
                  </span>
                ))}
              </div>
            </div>
            <div className="report-side">
              <span className="report-count" title={s.reportCount(item.reportCount)}>
                <FlagIcon size={14} /> {item.reportCount}
              </span>
              <div className="report-actions">
                <button
                  type="button"
                  className="ghost sm"
                  disabled={act.isPending}
                  onClick={() => act.mutate({ id: item.id, action: "approve" })}
                >
                  <CheckIcon size={14} /> {s.approve}
                </button>
                {item.status !== "removed" && (
                  <button
                    type="button"
                    className="danger sm"
                    disabled={act.isPending}
                    onClick={() => act.mutate({ id: item.id, action: "takedown" })}
                  >
                    <TrashIcon size={14} /> {s.takeDown}
                  </button>
                )}
              </div>
            </div>
          </li>
        ))}
      </ul>
    </div>
  );
}
