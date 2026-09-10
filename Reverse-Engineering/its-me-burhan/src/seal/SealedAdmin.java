package seal;

import entities.Admin;

public final class SealedAdmin extends Admin {

    public SealedAdmin() {
        super("");
    }

    @Override
    public boolean authenticate(String username, String password) {
        return getUsername().equals(username)
            && InstanceConfig.get().checkAdminPassword(password);
    }
}