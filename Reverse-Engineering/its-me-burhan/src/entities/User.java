package entities;

public abstract class User {
    private String name;
    private String username;
    private String password;

    protected User(String name, String username, String password) {
        this.name = name;
        this.username = username;
        this.password = password;
    }

    public abstract String getWelcomeMessage();

    public abstract String getActorName();

    public boolean authenticate(String username, String password) {
        return getUsername().equals(username) && getPassword().equals(password);
    }

    public String getName() {
        return this.name;
    }

    public String getUsername() {
        return this.username;
    }
    
    protected String getPassword() {
        return this.password;
    }
}