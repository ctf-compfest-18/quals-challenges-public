package services;

import entities.Admin;
import entities.User;

import utils.BurhanLogger;
import utils.enums.LogCategory;

import java.util.ArrayList;
import java.util.stream.IntStream;

public class AuthService {
    private final ArrayList<User> users;

    public AuthService() {
        this.users = new ArrayList<>();
        users.add(new Admin());
    }

    public User login(String username, String password) {
        BurhanLogger logger = BurhanLogger.getInstance();

        User loggedInUser = users.stream()
                .filter(user -> user.authenticate(username, password))
                .findFirst()
                .orElse(null);

        if (loggedInUser != null) {
            logger.log(
                LogCategory.AUTH,
                loggedInUser.getActorName(),
                "Berhasil login ke dalam sistem."
            );
            return loggedInUser;
        }

        logger.log(
            LogCategory.AUTH,
            "Sistem",
            username + " Gagal login: Autentikasi tidak valid"
        );
        return null;
    }

    public void addUser(User user) {
        if (user != null) {
            users.add(user);
        }
    }

    public boolean removeUser(User user) {
        return users.remove(user);
    }

    public ArrayList<User> getUsers() {
        return new ArrayList<>(users);
    }

    public boolean isUsernameTaken(String username) {
        return users.stream()
                .anyMatch(user -> user.getUsername().equals(username));
    }

    public boolean isUniqueUsername(String username) {
        return !isUsernameTaken(username);
    }

    public boolean isUsernameInputValid(String usernameInput) {
        return usernameInput != null
                && !usernameInput.isEmpty()
                && usernameInput.chars().allMatch(ch -> Character.isLetterOrDigit(ch) || ch == '_');
    }

    public boolean isValidLoginInput(String usernameInput, String passwordInput) {
        return isUsernameInputValid(usernameInput) && passwordInput != null && !passwordInput.isEmpty();
    }

    public void validateLoginInput(String usernameInput, String passwordInput) {
        if (!isValidLoginInput(usernameInput, passwordInput)) {
            throw new IllegalArgumentException("Input invalid, masukkan username dan password sesuai dengan ketentuan yang ada!");
        }
    }

    public boolean isAdmin(User user) {
        return user instanceof Admin;
    }

    public boolean isNonNegativeInteger(String number) {
        if (number == null || number.isEmpty()) {
            return false;
        }
        return number.matches("^\\d+$");
    }

    public boolean isPositiveInteger(String number) {
        return isNonNegativeInteger(number) && !number.equals("0");
    }

    public boolean isPositiveDouble(String number) {
        if (number == null || number.isEmpty()) {
            return false;
        }
        if (!number.matches("^(\\d+\\.\\d+|\\d+|\\.\\d+)$")) {
            return false;
        }
        return Double.parseDouble(number) > 0;
    }

    public boolean isAlphaNumericSpace(String str) {
        return str != null
                && !str.isEmpty()
                && str.chars().allMatch(ch -> Character.isLetterOrDigit(ch) || ch == ' ');
    }

    public boolean isNameInputValid(String nameInput) {
        return nameInput != null
                && !nameInput.isEmpty()
                && isAlphaNumericSpace(nameInput)
                && IntStream.range(0, nameInput.length()).allMatch(i -> {
                    char c = nameInput.charAt(i);
                    if (!Character.isLetter(c)) {
                        return true;
                    }
                    boolean isFirstLetterOfWord = i == 0 || nameInput.charAt(i - 1) == ' ';
                    return isFirstLetterOfWord == Character.isUpperCase(c);
                });
    }

    public boolean isSortOrderValid(String order) {
        return order != null && !order.isEmpty() && (order.equalsIgnoreCase("asc") || order.equalsIgnoreCase("desc"));
    }

    public void validateSortOrderInput(String orderType) {
        if (!isSortOrderValid(orderType)) {
            throw new IllegalArgumentException("Pilihan tidak valid. Harap masukkan pilihan dengan benar.");
        }
    }
}
