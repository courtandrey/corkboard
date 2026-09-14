import { useNavigate } from "react-router";
import { useBoardHome } from "../../stores/boardStore";
import { strings } from "../../i18n/strings";
import { Modal } from "../../ui/Modal";

const s = strings.about;

export function AboutModal() {
  const navigate = useNavigate();
  const home = useBoardHome();

  return (
    <Modal onClose={() => navigate(home)} size="sm" className="modal-about" labelledBy="about-title">
      <div className="modal-head">
        <h2 id="about-title">{s.title}</h2>
      </div>
      <div className="modal-body">
        <p className="about-lead">{s.lead}</p>
        <ul className="about-points">
          <li>{s.boards}</li>
          <li>{s.reports}</li>
        </ul>
        <div className="about-support">
          <h3>{s.supportTitle}</h3>
          <p>{s.supportBody}</p>
          <a href={`mailto:${s.supportEmail}`}>{s.supportEmail}</a>
        </div>
      </div>
    </Modal>
  );
}
