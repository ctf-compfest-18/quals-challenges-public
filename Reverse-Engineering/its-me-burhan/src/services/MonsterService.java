package services;

import entities.monster.Monster;

import repository.GameRepository;

import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class MonsterService {
    private final GameRepository<Monster> monsterRepository;

    public MonsterService() {
        this.monsterRepository = new GameRepository<>();
    }

    public void addMonster(String name, double maxHp, double attackPower, double defense, int expReward, int coinReward) {
        Monster monster = new Monster(nextMonsterId(), name, maxHp, attackPower, defense, expReward, coinReward);
        this.monsterRepository.add(monster);
    }

    private int nextMonsterId() {
        return monsterRepository.size() + 1;
    }

    public ArrayList<Monster> getMonsters() {
        return this.monsterRepository.findAll();
    }

    public int getMonstersCount() {
        return monsterRepository.size();
    }

    public Optional<Monster> findMonsterById(String id) {
        String normalizedId = normalizeMonsterId(id);
        if (!isPositiveInteger(normalizedId)) {
            return Optional.empty();
        }

        String targetId = "M" + normalizedId;
        return Optional.ofNullable(
            monsterRepository.find(monster -> monster != null && monster.getMonsterId().equalsIgnoreCase(targetId))
        );
    }

    public Optional<Monster> findMonsterByName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return Optional.empty();
        }

        String normalizedName = name.trim();
        return Optional.ofNullable(
            monsterRepository.find(monster -> monster != null && monster.getName().equalsIgnoreCase(normalizedName))
        );
    }

    public Monster fetchMonsterbyId(String id) {
        return findMonsterById(id)
            .orElseThrow(() -> new IllegalArgumentException("Monster tidak ditemukan."));
    }

    private String normalizeMonsterId(String id) {
        if (id == null) {
            return null;
        }

        String trimmedId = id.trim();
        if (trimmedId.length() > 1 && (trimmedId.charAt(0) == 'M' || trimmedId.charAt(0) == 'm')) {
            return trimmedId.substring(1);
        }
        return trimmedId;
    }

    public void validateMonsterInput(String name, String maxHp, String attackPower, String defense, String expReward, String coinReward) {
        if (!isNameInputValid(name)) {
            throw new IllegalArgumentException("Nama monster hanya boleh berisi karakter alfanumerik dan spasi, huruf pertama setiap kata harus kapital, dan selain itu tidak boleh ada huruf kapital.");
        }
        if (!isPositiveDouble(maxHp)) {
            throw new IllegalArgumentException("HP maksimal harus bilangan positif.");
        }
        if (!isPositiveDouble(attackPower)) {
            throw new IllegalArgumentException("Attack power harus bilangan positif.");
        }
        if (!isPositiveDouble(defense)) {
            throw new IllegalArgumentException("Defense harus bilangan positif.");
        }
        if (!isNonNegativeInteger(expReward)) {
            throw new IllegalArgumentException("Exp reward harus bilangan bulat nonnegatif.");
        }
        if (!isNonNegativeInteger(coinReward)) {
            throw new IllegalArgumentException("Coin reward harus bilangan bulat nonnegatif.");
        }
    }

    public String showMonsters() {
        if (getMonstersCount() == 0) {
            return "Belum ada monster terdaftar\n";
        }
        return monsterRepository.findAll().stream()
            .map(Monster::toString)
            .collect(Collectors.joining("\n\n", "", "\n\n"));
    }

    public String showMonstersName() {
        ArrayList<Monster> monsters = monsterRepository.findAll();
        return IntStream.range(0, monsters.size())
            .mapToObj(i -> (i + 1) + ". " + monsters.get(i).getName())
            .collect(Collectors.joining("\n", "", monsters.isEmpty() ? "" : "\n"));
    }

    private boolean isNonNegativeInteger(String number) {
        if (number == null || number.isEmpty()) {
            return false;
        }
        return number.matches("^\\d+$");
    }

    private boolean isPositiveInteger(String number) {
        return isNonNegativeInteger(number) && !number.equals("0");
    }

    private boolean isPositiveDouble(String number) {
        if (number == null || number.isEmpty()) {
            return false;
        }
        if (!number.matches("^(\\d+\\.\\d+|\\d+|\\.\\d+)$")) {
            return false;
        }
        return Double.parseDouble(number) > 0;
    }


    private boolean isAlphaNumericSpace(String str) {
        return str != null
            && !str.isEmpty()
            && str.chars().allMatch(ch -> Character.isLetterOrDigit(ch) || ch == ' ');
    }

    private boolean isNameInputValid(String nameInput) {
        return nameInput != null
            && !nameInput.isEmpty()
            && isAlphaNumericSpace(nameInput)
            && IntStream.range(0, nameInput.length())
                .filter(i -> Character.isLetter(nameInput.charAt(i)) && (i == 0 || nameInput.charAt(i - 1) == ' '))
                .allMatch(i -> Character.isUpperCase(nameInput.charAt(i)));
    }
}
