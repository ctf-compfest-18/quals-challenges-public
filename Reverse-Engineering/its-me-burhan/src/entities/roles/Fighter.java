package entities.roles;

import entities.Wanderer;

public class Fighter extends Wanderer {
    private int furyStacks;
    private int fury1Count, fury2Count, fury3Count, resetCount;

    public Fighter(int idNumber, String name, String username,
                   String password, double maxHp, double attack, double defense) {
        super(idNumber, name, username, password, maxHp, attack, defense);
        resetBattleState();
    }

    @Override
    public void onTurnStart() {
        String skillMessage = "[FIGHTER] Fury stacks aktif! (Total: " + furyStacks + ")";
        logSkillActivation(skillMessage);
        if(furyStacks == 1){
            setCustomDamageNote("[FIGHTER]: Fury Stacks (I) aktif, Damage bertambah sebesar 10% ATK!");
        } else if(furyStacks == 2){
            setCustomDamageNote("[FIGHTER]: Fury Stacks (II) aktif, Damage bertambah sebesar 20% ATK!");
        } else if(furyStacks == 3){
            setCustomDamageNote("[FIGHTER]: Fury Stacks (III) aktif, Damage bertambah sebesar 30% ATK!");
        }
    }

    @Override
    public double modifyDamageDealt(double baseDamage) {
        if(furyStacks == 1) fury1Count++;
        else if(furyStacks == 2) fury2Count++;
        else if(furyStacks == 3) fury3Count++;

        return baseDamage + (0.1 * getAttackPower() * furyStacks);
    }

    @Override
    public void onTurnEnd(double result) {
        furyStacks++;

        if(furyStacks > 3){
            furyStacks = 0;
            resetCount++;
            setCustomDamageNote("[FIGHTER] Stacks full, Reset ke 0!");
        } else{
            setCustomDamageNote("[FIGHTER] Stacks bertambah!");
        }
    }

    @Override
    public void resetBattleState() {
        super.resetBattleState();
        furyStacks = 0;
        fury1Count = fury2Count = fury3Count = resetCount = 0;
    }

    @Override
    public String getPassiveSummary() {
        return "- Fury Stacks I aktif: " + fury1Count + " kali\n" +
               "- Fury Stacks II aktif: " + fury2Count + " kali\n" +
               "- Fury Stacks III aktif: " + fury3Count + " kali\n" +
               "- Reset Fury Stack: " + resetCount + " kali\n";
    }

    @Override
    public String getJobName() {
        return "FIGHTER";
    }
}