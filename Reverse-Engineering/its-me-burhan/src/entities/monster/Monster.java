package entities.monster;

import entities.interfaces.Combatant;

import java.text.DecimalFormat;

public class Monster implements Combatant {
    private String monsterId;
    private String name;
    private double maxHp;
    private double currentHp;
    private double attackPower;
    private double defense;
    private int expReward;
    private int coinReward;
    private boolean bleeding;
    private int bleedDamage;
    private String bleedSourceName;
    private String customDamageNote;
    private int bleedTriggeredCount;

    public static final DecimalFormat df = new DecimalFormat("#.##");

    public Monster(int idNumber, String name, double maxHp,
                   double attackPower, double defense,
                   int expReward, int coinReward) {
        this.monsterId = "M" + idNumber;
        this.name = name;
        this.maxHp = maxHp;
        this.currentHp = maxHp;
        this.attackPower = attackPower;
        this.defense = defense;
        this.expReward = expReward;
        this.coinReward = coinReward;
        resetBattleState();
    }

    public String getMonsterId() { return this.monsterId; }
    public int getExpReward() { return this.expReward; }
    public int getCoinReward() { return this.coinReward; }
    public double getCurrentHp() { return this.currentHp; }
    public double getMaxHp() { return this.maxHp; }

    public void resetHp() {
        this.currentHp = this.maxHp;
    }

    public void applyBleed(String sourceName, int bleedDamage) {
        this.bleeding = true;
        this.bleedSourceName = sourceName;
        this.bleedDamage = bleedDamage;
    }

    public void onTurnStart() {
        if(bleeding){
            takeDamage(bleedDamage);
            bleedTriggeredCount++;

            customDamageNote = "[ASSASSIN] " + getName() + " menerima " + bleedDamage + " damage dari Bleed!";

            bleeding = false;
            bleedSourceName = "";
        }
    }

    public String consumeCustomDamageNote() {
        String note = customDamageNote;
        customDamageNote = null;
        return note;
    }

    public int getBleedTriggeredCount() {
        return bleedTriggeredCount;
    }

    public void resetBattleState() {
        bleeding = false;
        bleedDamage = 0;
        bleedSourceName = "";
        customDamageNote = null;
        bleedTriggeredCount = 0;
    }

    @Override
    public String getName() { return this.name; }

    @Override
    public double getAttackPower() { return this.attackPower; }

    @Override
    public double getDefense() { return this.defense; }

    @Override
    public void takeDamage(double damage) {
        this.currentHp -= damage;
    }

    @Override
    public boolean isDefeated() {
        return this.currentHp <= 0;
    }

    @Override
    public String getCombatInfo() {
        return getName() + " | HP: " + df.format(getCurrentHp()) + "/" + df.format(getMaxHp())
                + " | ATK: " + df.format(getAttackPower())
                + " | DEF: " + df.format(getDefense());
    }

    @Override
    public String toString() {
        return "ID Monster: " + this.monsterId + "\n" +
                "Nama Monster: " + getName() + "\n" +
                "Reward Exp: " + this.expReward + "\n" +
                "Reward Koin: " + this.coinReward + "\n" +
                "HP: " + df.format(getCurrentHp()) + "/" + df.format(getMaxHp()) + "\n" +
                "Attack: " + df.format(getAttackPower()) + " | Defense: " + df.format(getDefense());
    }
}