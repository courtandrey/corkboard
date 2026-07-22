import { useNavigate } from "react-router";
import { strings } from "../../i18n/strings";
import { Modal } from "../../ui/Modal";
import { AuthPanel } from "./AuthPanel";

export function AuthDrawer() {
  const navigate = useNavigate();
  return (
    <Modal onClose={() => navigate("/")} size="sm">
      <div className="modal-head">
        <h2>{strings.auth.signIn}</h2>
      </div>
      <div className="modal-body" style={{ paddingBottom: 18 }}>
        <AuthPanel onSignedIn={() => navigate("/")} />
      </div>
    </Modal>
  );
}
