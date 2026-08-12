import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "../../api/client";
import type { FeatureFlagItem, FeatureFlagListResponse } from "../../api/client";
import { strings } from "../../i18n/strings";
import { toast } from "../../ui/toast";

const s = strings.features;

export function FeatureFlagList() {
  const queryClient = useQueryClient();

  const { data, isLoading } = useQuery({
    queryKey: ["admin", "features"],
    queryFn: () => api.get<FeatureFlagListResponse>("/api/v1/admin/features"),
  });

  const flip = useMutation({
    mutationFn: ({ key, enabled }: { key: string; enabled: boolean }) =>
      api.patch<FeatureFlagItem>(`/api/v1/admin/features/${key}`, { enabled }),
    onSuccess: async (item) => {
      toast(item.enabled ? s.switchedOn(item.label) : s.switchedOff(item.label));
      queryClient.setQueryData<FeatureFlagListResponse>(["admin", "features"], (previous) =>
        previous
          ? { items: previous.items.map((known) => (known.key === item.key ? item : known)) }
          : previous,
      );
      await queryClient.invalidateQueries({ queryKey: ["features"] });
    },
    onError: () => toast(s.failed, "info"),
  });

  return (
    <div className="modal-body">
      <p className="form-hint">{s.intro}</p>
      {isLoading && <p className="empty-state">{strings.loading}</p>}
      {data?.items.length === 0 && <p className="empty-state">{s.empty}</p>}

      <ul className="flag-list">
        {data?.items.map((item) => (
          <li key={item.key} className="flag-row">
            <div className="flag-main">
              <span className="flag-name">{item.label}</span>
              <span className="form-hint">{item.description}</span>
              <span className="flag-trail">
                <code className="flag-key">{item.key}</code>
                {" · "}
                {item.updatedAt
                  ? item.updatedBy
                    ? s.lastChanged(new Date(item.updatedAt).toLocaleDateString(), item.updatedBy)
                    : s.lastChangedUnknown(new Date(item.updatedAt).toLocaleDateString())
                  : s.never}
              </span>
            </div>
            <button
              type="button"
              role="switch"
              aria-checked={item.enabled}
              aria-label={s.toggleLabel(item.label)}
              className={`flag-switch${item.enabled ? " on" : ""}`}
              disabled={flip.isPending}
              onClick={() => flip.mutate({ key: item.key, enabled: !item.enabled })}
            >
              <span className="flag-switch-track" aria-hidden="true">
                <span className="flag-switch-knob" />
              </span>
              <span className="flag-switch-text">{item.enabled ? s.on : s.off}</span>
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
