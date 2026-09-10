package entities;

import entities.interfaces.Combatant;
import entities.monster.Monster;

import quests.Quest;
import quests.enums.Difficulty;

import utils.BurhanLogger;
import utils.enums.LogCategory;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Optional;

public class Wanderer extends User implements Combatant {
    private String id;
    private int level;
    private int exp;
    private int coins;
    private double currentHp;
    private double maxHp;
    private double attack;
    private double defense;
    private Monster currentTarget;
    private double currentMultiplier;
    private String customDamageNote;
    private ArrayList<Quest> completedQuestHistory;
    private int totalBattles;
    private int totalWins;
    private int totalLosses;
    private int consecutiveLosses;
    private boolean eliminated;
    
    private static final int MAX_EXP = 1_310_720_000; // Max exp untuk pengembara (level 20)

    public static final DecimalFormat df = new DecimalFormat("#.##");

    public Wanderer(int idNumber, String name, String username,
                    String password, double maxHp, double attack, double defense) {
        super(name, username, password);
        this.id = "P" + idNumber;
        this.level = 1;
        this.exp = 0;
        this.coins = 0;
        this.currentHp = maxHp;
        this.maxHp = maxHp;
        this.attack = attack;
        this.defense = defense;
        this.completedQuestHistory = new ArrayList<>();
        this.totalBattles = 0;
        this.totalWins = 0;
        this.totalLosses = 0;
        this.consecutiveLosses = 0;
        this.eliminated = false;
        resetBattleState();
    }

    public String getId() { return this.id; }
    public int getLevel() { return this.level; }
    public int getExp() { return this.exp; }
    public int getCoins() { return this.coins; }
    public double getMaxHp() { return this.maxHp; }

    public boolean canTakeQuest(Difficulty difficulty) {
        return getLevel() >= difficulty.getMinWandererLevel();
    }

    public void addExp(int amount) {
        this.exp += amount;
    }

    public void addCoins(int amount) {
        this.coins += amount;
    }

    // Additive helper used only by instance seeding. Sets level/exp/coins directly
    // so a per-instance starting state can be installed deterministically.
    public void seedProgress(int targetLevel, int coinAmount) {
        this.level = Math.max(1, Math.min(targetLevel, 20));
        this.exp = getLowerBoundExpForLevel(this.level);
        this.coins = Math.max(0, coinAmount);
        resetCurrentHp();
    }

    public int getNextLevelExp(int currentLevel) {
        if (currentLevel == 1) {
            return 5000;
        }
        return 2 * getNextLevelExp(currentLevel - 1);
    }

    public void completeQuest(int questExp, int questCoins) {
        this.exp = Math.min(getExp() + questExp, MAX_EXP); // Update exp        
        this.coins += questCoins; // Update koin
        
        while (getExp() >= getNextLevelExp(getLevel()) && getLevel() < 20) {
            this.level++;
        }
    }

    public void resetCurrentHp() {
        this.currentHp = this.maxHp;
    }

    public void setCurrentHp(double hp) {
        this.currentHp = Math.min(hp, this.maxHp);
    }

    public double getCurrentHp() {
        return this.currentHp;
    }

    public void setBattleContext(Monster target, double multiplier) {
        this.currentTarget = target;
        this.currentMultiplier = multiplier;
    }

    protected Optional<Monster> getCurrentTarget() {
        return Optional.ofNullable(this.currentTarget);
    }

    protected double getCurrentMultiplier() {
        return this.currentMultiplier;
    }

    protected void setCustomDamageNote(String customDamageNote) {
        this.customDamageNote = customDamageNote;
    }

    public String consumeCustomDamageNote() {
        String note = this.customDamageNote;
        this.customDamageNote = null;
        return note;
    }

    public String getPassiveSummary() {
        return "";
    }

    public String getJobName() {
        if (getClass().equals(Wanderer.class)) {
            return "Novice";
        }
        return getClass().getSimpleName().toUpperCase();
    }

    @Override
    public String getActorName() {
        return "Pengembara (" + getJobName() + "): " + getUsername();
    }

    @Override
    public String getWelcomeMessage() {
        return "Login berhasil! Selamat datang, " + getName() + ".";
    }

    @Override
    public String getName() {
        return super.getName();
    }

    @Override
    public double getAttackPower() {
        return this.attack;
    }

    @Override
    public double getDefense() {
        return this.defense;
    }

    @Override
    public void takeDamage(double damage) {
        this.currentHp -= damage;
    }

    public void onTurnStart() {
    }

    public double modifyDamageDealt(double baseDamage) {
        return baseDamage;
    }

    public double modifyDamageTaken(double incomingDamage) {
        return incomingDamage;
    }

    public void onTurnEnd(double result) {
    }

    public void resetBattleState() {
        this.currentTarget = null;
        this.currentMultiplier = 1.0;
        this.customDamageNote = null;
    }

    @Override
    public boolean isDefeated() {
        return getCurrentHp() <= 0;
    }

    @Override
    public String getCombatInfo() {
        return getName() + " | HP: " + df.format(getCurrentHp()) + "/" + df.format(getMaxHp())
                + " | ATK: " + df.format(getAttackPower())
                + " | DEF: " + df.format(getDefense());
    }

    @Override
    public String toString() {
        return "ID Pengembara: " + getId() + "\n" +
                "Nama Pengembara: " + getName() + "\n" +
                "Username: " + getUsername() + "\n" +
                "Gelar Pengembara: " + getJobTitle() + "\n" + // SUBTASK 7 - KREATIVITAS
                "Level Pengembara: " + getLevel() + "\n" +
                "Exp Pengembara: " + getExp() + "\n" +
                "Koin Didapatkan: " + getCoins() + "\n" +
                "HP: " + df.format(getCurrentHp()) + "/" + df.format(getMaxHp()) + "\n" +
                "Attack: " + df.format(getAttackPower()) + " | Defense: " + df.format(getDefense()) +
                // SUBTASK 7 - KREATIVITAS
                "\nStatistik Battle:\n" +
                "Total Battle: " + getTotalBattles() + "\n" +
                "Menang: " + getTotalWins() + "\n" +
                "Kalah: " + getTotalLosses() + "\n" +
                "Win Rate: " + df.format(getWinRate()) + "%";
    }

    public void addCompletedQuest(Quest quest) {
        if (quest != null) {
            this.completedQuestHistory.add(quest);
        }
    }

    public ArrayList<Quest> getCompletedQuestHistory() {
        return new ArrayList<>(this.completedQuestHistory);
    }

    protected void logSkillActivation(String skillMessage) {
        if (skillMessage == null || skillMessage.isBlank()) {
            return;
        }

        BurhanLogger.getInstance().log(
            LogCategory.BATTLE,
            getActorName(),
            skillMessage
        );
    }

    // SUBTASK 7 - KREATIVITAS
    public String getTitle() {
        if (getLevel() >= 20) {
            return "Pahlawan BurhanQuest";
        } else if (getLevel() >= 15) {
            return "Legenda";
        } else if (getLevel() >= 10) {
            return "Ksatria Guild";
        } else if (getLevel() >= 5) {
            return "Petualang";
        }
        return "Pemula";
    }

    public String getJobTitle() {
        return getJobName() + " - " + getTitle();
    }

    public void recordBattleResult(boolean win) {
        this.totalBattles++;

        if (win) {
            this.totalWins++;
            resetConsecutiveLosses();
        } else {
            this.totalLosses++;
            this.consecutiveLosses++;
        }
    }

    public int getConsecutiveLosses() {
        return this.consecutiveLosses;
    }

    public void resetConsecutiveLosses() {
        this.consecutiveLosses = 0;
    }

    public int getLowerBoundExpForLevel(int targetLevel) {
        if (targetLevel <= 1) {
            return 0;
        }
        return getNextLevelExp(targetLevel - 1);
    }

    public void demoteOneLevel() {
        if (this.level > 1) {
            this.level--;
            this.exp = getLowerBoundExpForLevel(this.level);
        }
    }

    public void markEliminated() {
        this.eliminated = true;
    }

    public boolean isEliminated() {
        return this.eliminated;
    }

    public int getTotalBattles() {
        return this.totalBattles;
    }

    public int getTotalWins() {
        return this.totalWins;
    }

    public int getTotalLosses() {
        return this.totalLosses;
    }

    public double getWinRate() {
        if (this.totalBattles == 0) {
            return 0;
        }

        return (double) this.totalWins / this.totalBattles * 100;
    }
}