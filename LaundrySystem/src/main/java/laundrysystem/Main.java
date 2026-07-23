package laundrysystem;

public class Main {
    public static void main(String[] args) {
        // "Keep signed in": if a previous session left this flag set (done
        // in MainDashboard's constructor, since that only runs after a
        // successful login), skip straight to the dashboard instead of
        // showing the login form again. Log Out (in Settings) clears this.
        if (SessionManager.isKeptSignedIn()) {
            javax.swing.SwingUtilities.invokeLater(() -> new MainDashboard().setVisible(true));
        } else {
            LoginAndSignUp.main(args);
        }
    }
}
