package services;

import entities.User;
import entities.Wanderer;
import entities.roles.Assassin;
import entities.roles.Fighter;
import entities.roles.Mage;
import entities.roles.Support;
import entities.roles.Tank;

import exception.DuplicateWandererException;

import repository.GameRepository;

import utils.BurhanLogger;
import utils.enums.LogCategory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class WandererService {
    private static final int LOSE_STREAK_LIMIT = 3;

    private final GameRepository<Wanderer> wandererRepository;
    private final AuthService authService;
    private int nextWandererId;

    public WandererService(AuthService authService) {
        this.authService = authService;
        this.wandererRepository = new GameRepository<>();
        this.nextWandererId = 1;
    }

    public void addWanderer(String name, String username, String password, double maxHp, double attack, double defense, String job) throws DuplicateWandererException {
        if (!authService.isUniqueUsername(username)) {
            throw new DuplicateWandererException(username);
        }

        if (!isJobClassValid(job)) {
            throw new IllegalArgumentException("Job class harus salah satu dari yang tertera pada list.");
        }

        int id = nextWandererId();
        Wanderer wanderer = switch (job) {
            case "1" -> new Wanderer(id, name, username, password, maxHp, attack, defense);
            case "2" -> new Tank(id, name, username, password, maxHp, attack, defense);
            case "3" -> new Mage(id, name, username, password, maxHp, attack, defense);
            case "4" -> new Assassin(id, name, username, password, maxHp, attack, defense);
            case "5" -> new Fighter(id, name, username, password, maxHp, attack, defense);
            case "6" -> new Support(id, name, username, password, maxHp, attack, defense);
            default -> throw new IllegalStateException("Job class tidak valid setelah validasi.");
        };

        wandererRepository.add(wanderer);
        authService.addUser(wanderer);
    }

    public String addWandererJobString(String job) {
        return switch (job) {
            case "1" -> "NOVICE";
            case "2" -> "TANK";
            case "3" -> "MAGE";
            case "4" -> "ASSASSIN";
            case "5" -> "FIGHTER";
            case "6" -> "SUPPORT";
            default -> throw new IllegalArgumentException("Job class harus salah satu dari yang tertera pada list.");
        };
    }

    private int nextWandererId() {
        return nextWandererId++;
    }

    public boolean removeWanderer(Wanderer wanderer) {
        if (wanderer == null) {
            return false;
        }
        boolean removedFromRepository = wandererRepository.remove(wanderer);
        boolean removedFromAuth = authService.removeUser(wanderer);
        return removedFromRepository || removedFromAuth;
    }

    public boolean handlePostBattleLoseStreak(Wanderer wanderer) {
        if (wanderer == null) {
            return false;
        }

        Predicate<Wanderer> shouldDemote = entity -> entity.getConsecutiveLosses() >= LOSE_STREAK_LIMIT
            && entity.getLevel() > 1;
        Predicate<Wanderer> shouldEliminate = entity -> entity.getConsecutiveLosses() >= LOSE_STREAK_LIMIT
            && entity.getLevel() == 1;

        if (shouldDemote.test(wanderer)) {
            wanderer.demoteOneLevel();
            wanderer.resetConsecutiveLosses();

            BurhanLogger.getInstance().log(
                LogCategory.SYSTEM,
                "Sistem",
                "Pengembara '" + wanderer.getUsername() + "' turun ke level "
                    + wanderer.getLevel() + " karena kalah 3 kali berturut-turut."
            );
            return false;
        }

        if (shouldEliminate.test(wanderer)) {
            wanderer.markEliminated();
            removeWanderer(wanderer);

            BurhanLogger.getInstance().log(
                LogCategory.SYSTEM,
                "Sistem",
                "Pengembara '" + wanderer.getUsername() + "' (" + wanderer.getId()
                    + ") dikeluarkan dari sistem BurhanQuest karena kalah 3 kali di level 1."
            );
            return true;
        }

        return false;
    }

    public ArrayList<User> getWanderers() {
        return new ArrayList<User>(wandererRepository.findAll());
    }

    public int getWanderersCount() {
        return wandererRepository.size();
    }

    public ArrayList<User> filterWandererByLevel(int min, int max) {
        return new ArrayList<User>(wandererRepository.findAll(
            wanderer -> min <= wanderer.getLevel() && wanderer.getLevel() <= max
        ));
    }

    public ArrayList<User> sortWandererByName(boolean asc) {
        ArrayList<User> result = getWanderers();

        Comparator<User> comparator = Comparator.comparing(
            wanderer -> ((Wanderer) wanderer).getName()
        );

        if (!asc) {
            comparator = comparator.reversed();
        }

        result.sort(comparator);
        return result;
    }

    public ArrayList<User> sortWandererByLevel(boolean asc) {
        ArrayList<User> result = getWanderers();

        Comparator<User> comparator = Comparator.comparingInt(
            wanderer -> ((Wanderer) wanderer).getLevel()
        );

        if (!asc) {
            comparator = comparator.reversed();
        }

        result.sort(comparator);
        return result;
    }

    public void resetAllHp() {
        wandererRepository.findAll().stream()
            .forEach(Wanderer::resetCurrentHp);
    }

    public boolean isJobClassValid(String number) {
        return number != null && (number.equals("1") || number.equals("2") || number.equals("3") || number.equals("4") || number.equals("5") || number.equals("6"));
    }

    public boolean isLevelIntervalValid(int lower, int upper) {
        return (lower <= upper) && !(lower < 1) && !(upper > 20);
    }

    public void validateWandererInput(String name, String username, String password, String maxHp, String attackPower, String defense, String job) {
        if (!authService.isNameInputValid(name)) {
            throw new IllegalArgumentException("Nama pengembara hanya boleh berisi karakter alfanumerik dan spasi, huruf pertama setiap kata harus kapital, dan selain itu tidak boleh ada huruf kapital.");
        }
        if (!authService.isUsernameInputValid(username)) {
            throw new IllegalArgumentException("Username hanya boleh berisi huruf, angka, dan underscore (_).");
        }
        if (!authService.isUniqueUsername(username)) {
            throw new IllegalArgumentException("Username sudah dipakai oleh user lain, harap masukkan username lain.");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password tidak boleh kosong.");
        }
        if (!authService.isPositiveDouble(maxHp)) {
            throw new IllegalArgumentException("HP maksimal harus bilangan positif.");
        }
        if (!authService.isPositiveDouble(attackPower)) {
            throw new IllegalArgumentException("Attack power harus bilangan positif.");
        }
        if (!authService.isPositiveDouble(defense)) {
            throw new IllegalArgumentException("Defense harus bilangan positif.");
        }
        if (!isJobClassValid(job)) {
            throw new IllegalArgumentException("Job class harus salah satu dari yang tertera pada list.");
        }
    }

    public void validateLevelIntervalInput(String lowerBound, String upperBound) {
        if (!authService.isPositiveInteger(lowerBound) || !authService.isPositiveInteger(upperBound)) {
            throw new IllegalArgumentException("Pilihan tidak valid. Harap masukkan pilihan dengan benar.");
        }

        int lower = Integer.parseInt(lowerBound);
        int upper = Integer.parseInt(upperBound);
        if (!isLevelIntervalValid(lower, upper)) {
            throw new IllegalArgumentException("Input tidak valid. Harap masukkan bilangan bulat di antara 1 dan 20 dengan batas bawah tidak melebihi batas atas.");
        }
    }

    public void validateWandererSortType(String sortType) {
        if (sortType == null || (!sortType.equals("1") && !sortType.equals("2"))) {
            throw new IllegalArgumentException("Urutan tidak valid. Harap masukkan urutan dengan benar.");
        }
    }

    public String showWanderers(ArrayList<User> wanderersToShow) {
        if (getWanderersCount() == 0) {
            return "Belum ada pengembara terdaftar\n";
        }
        return wanderersToShow.stream()
            .map(user -> ((Wanderer) user).toString())
            .collect(Collectors.joining("\n\n", "", "\n\n"));
    }
}
