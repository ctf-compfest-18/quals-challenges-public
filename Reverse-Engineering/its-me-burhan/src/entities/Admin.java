package entities;

public class Admin extends User {
    public Admin() {
        super("Burhan", "burhan", "burunghantu123");
    }

    // Seeded admin used by the running instance. The no-arg constructor above is
    // retained so the existing unit tests keep passing; the live game replaces the
    // default admin with one built through this constructor.
    public Admin(String password) {
        super("Burhan", "burhan", password);
    }

    @Override
    public String getWelcomeMessage() {
        return "Login berhasil! Selamat datang, Burhan.";
    }

    @Override
    public String getActorName() {
        return "Admin: " + getUsername();
    }
}