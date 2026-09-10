package entities.roles;

import entities.Wanderer;

public class Support extends Wanderer {
    private int healCount;
    private double totalHeal;

    public Support(int idNumber, String name, String username,
                   String password, double maxHp, double attack, double defense) {
        super(idNumber, name, username, password, maxHp, attack, defense);
        resetBattleState();
    }

    @Override
    public void onTurnStart() {
        double before = getCurrentHp();
        double heal = 0.1 * getMaxHp();

        setCurrentHp(before + heal);

        double actual = getCurrentHp() - before;

        if(actual > 0){
            healCount++;
            totalHeal += actual;
            setCustomDamageNote("[SUPPORT]: " + getName() + " memulihkan " + actual + " HP!");

            String skillMessage = "[SUPPORT] Berhasil memulihkan " + Wanderer.df.format(actual) + " HP!";
            logSkillActivation(skillMessage);

        }
    }

    @Override
    public void resetBattleState() {
        super.resetBattleState();
        healCount = 0;
        totalHeal = 0;
    }

    @Override
    public String getPassiveSummary() {
        return "- Heal aktif: " + healCount + " kali\n" +
               "- Total HP dipulihkan: " + (int)totalHeal + "\n";
    }

    @Override
    public String getJobName() {
        return "SUPPORT";
    }
}