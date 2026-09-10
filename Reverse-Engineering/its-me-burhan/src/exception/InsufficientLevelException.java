package exception;

public class InsufficientLevelException extends BurhanQuestException {
    private final int currentLevel;
    private final int minimumLevel;

    public InsufficientLevelException(int currentLevel, int minimumLevel) {
        super("Level pengembara tidak mencukupi. Level saat ini: " 
                + currentLevel + ", level minimum quest: " + minimumLevel + ".");
        this.currentLevel = currentLevel;
        this.minimumLevel = minimumLevel;
    }

    public int getCurrentLevel() {
        return currentLevel;
    }

    public int getMinimumLevel() {
        return minimumLevel;
    }
}