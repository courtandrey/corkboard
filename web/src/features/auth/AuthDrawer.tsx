import { useNavigate } from "react-router";
import { strings } from "../../i18n/strings";
import { AuthPanel } from "./AuthPanel";

export function AuthDrawer() {
  const navigate = useNavigate();
  return (
    <section className="drawer">
      <button type="button" className="close" onClick={() => navigate("/")}>
        {strings.event.close}
      </button>
      <h2>{strings.auth.signIn}</h2>
      <AuthPanel onSignedIn={() => navigate("/")} />
    </section>
  );
}
