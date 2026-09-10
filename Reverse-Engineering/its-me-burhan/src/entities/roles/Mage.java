package entities.roles;

import entities.Wanderer;

public class Mage extends Wanderer {
    private boolean overcharged;
    private boolean usedBurstThisTurn;
    private int overchargeCount;
    private int burstCount;

    public Mage(int idNumber, String name, String username,
                String password, double maxHp, double attack, double defense) {
        super(idNumber, name, username, password, maxHp, attack, defense);
        resetBattleState();
    }

    @Override
    public void onTurnStart() {
        usedBurstThisTurn = false;
    }

    @Override
    public double modifyDamageDealt(double baseDamage) {
        if (overcharged) {
            overcharged = false;
            usedBurstThisTurn = true;
            burstCount++;
            
            String skillMessage = "[MAGE] Arcane Burst aktif!";
            setCustomDamageNote(skillMessage);
            logSkillActivation(skillMessage);

            return getAttackPower() * 2;
        }
        return baseDamage;
    }

    @Override
    public void onTurnEnd(double result) {
        if (!usedBurstThisTurn) {
            overcharged = true;
            overchargeCount++;
            setCustomDamageNote("[MAGE] Mana terkumpul! Overcharged untuk serangan berikutnya.");
        }
    }

    @Override
    public void resetBattleState() {
        super.resetBattleState();
        overcharged = false;
        usedBurstThisTurn = false;
        overchargeCount = 0;
        burstCount = 0;
    }

    @Override
    public String getPassiveSummary() {
        return "- Overcharged terkumpul: " + overchargeCount + " kali\n" +
               "- Arcane Burst aktif: " + burstCount + " kali\n";
    }

    @Override
    public String getJobName() {
        return "MAGE";
    }
}