package entities.interfaces;

public interface Combatant {
    String getName();
    double getAttackPower();
    double getDefense();
    void takeDamage(double damage);
    boolean isDefeated();
    String getCombatInfo();
}