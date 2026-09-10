package entities.roles;

import entities.Wanderer;

public class Tank extends Wanderer {
    private int shieldCount;

    public Tank(int idNumber, String name, String username,
                String password, double maxHp, double attack, double defense) {
        super(idNumber, name, username, password, maxHp, attack, defense);
        this.shieldCount = 0;
    }

    @Override
    public double modifyDamageTaken(double incomingDamage) {
        if (getCurrentHp() <= 0.3 * getMaxHp()) {
            shieldCount++;

            String skillMessage = "[TANK] Shield menyala! Damage Terpotong 50%";
            setCustomDamageNote(skillMessage);
            logSkillActivation(skillMessage);

            return incomingDamage * 0.5;
        }
        return incomingDamage;
    }

    @Override
    public void resetBattleState() {
        super.resetBattleState();
        shieldCount = 0;
    }

    @Override
    public String getPassiveSummary() {
        return "- Shield aktif: " + shieldCount + " kali\n";
    }

    @Override
    public String getJobName() {
        return "TANK";
    }
}