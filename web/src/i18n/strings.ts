export const strings = {
  appName: "lamppostal",
  loading: "Loading…",
  apiUnreachable: (detail: string) => `Couldn’t reach the API: ${detail}`,
  auth: {
    signIn: "Sign in",
    register: "Join the board",
    email: "Email",
    password: "Password",
    displayName: "Display name",
    signOut: "Sign out",
    signedInAs: (name: string) => `Signed in as ${name}`,
    googleSignIn: "Sign in with Google",
    googleFailed: "Google sign-in didn’t work — please try again or use your email.",
    switchToRegister: "New here? Create an account",
    switchToSignIn: "Already have an account? Sign in",
    genericError: "Something went wrong. Please try again.",
  },
};
