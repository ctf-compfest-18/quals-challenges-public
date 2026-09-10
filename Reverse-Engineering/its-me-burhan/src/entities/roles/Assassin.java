package entities.roles;

import entities.Wanderer;
import entities.monster.Monster;

import java.util.Random;

public class Assassin extends Wanderer {
    private int bleedAppliedCount;
    private Random rng;

    public Assassin(int idNumber, String name, String username,
                    String password, double maxHp, double attack, double defense) {
        this(idNumber, name, username, password, maxHp, attack, defense, new Random());
    }

    public Assassin(int idNumber, String name, String username,
                    String password, double maxHp, double attack, double defense, Random rng) {
        super(idNumber, name, username, password, maxHp, attack, defense);
        this.rng = rng;
        resetBattleState();
    }

    @Override
    public double modifyDamageDealt(double baseDamage) {
        getCurrentTarget()
            .filter(target -> rng.nextDouble() < 0.5)
            .ifPresent(target -> {
                int dmg = (int)(0.2 * getAttackPower());
                target.applyBleed(getName(), dmg);
                bleedAppliedCount++;
                setCustomDamageNote("[ASSASSIN] " + target.getName() + " terkena Bleed!");

                String skillMessage = "[ASSASSIN] Berhasil memberikan efek Bleed kepada " + target.getName() + "!";
                logSkillActivation(skillMessage);
            });

        return baseDamage;
    }

    @Override
    public void resetBattleState() {
        super.resetBattleState();
        bleedAppliedCount = 0;
    }

    @Override
    public String getPassiveSummary() {
        return "- Bleed berhasil diterapkan: " + bleedAppliedCount + " kali\n";
    }

    @Override
    public String getJobName() {
        return "ASSASSIN";
    }
}