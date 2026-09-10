package quests;

import quests.enums.Difficulty;
import quests.enums.QuestStatus;
import quests.interfaces.Completable;

import entities.monster.Monster;

public abstract class Quest implements Completable {
    private final String id;
    private final String name;
    private final String description;
    private final Difficulty difficulty;
    private final Monster monster;
    private QuestStatus status;

    protected Quest(int idNumber, String name, String description,
                    Difficulty difficulty, Monster monster) {
        // TODO
        this.id = "Q" + idNumber;
        this.name = name;
        this.description = description;
        this.difficulty = difficulty;
        this.monster = monster;
        this.status = QuestStatus.TERSEDIA;
    }

    public abstract String getQuestType();

    public String getDescription(){
        // TODO
        return this.description;
    }

    public int getExpReward() {
        // TODO
        return this.monster.getExpReward();
    }

    public int getCoinReward() {
        // TODO
        return this.monster.getCoinReward();
    }

    public int getBonusExp() { return 0; }

    public int getBonusCoin() { return 0; }

    @Override
    public boolean isCompletable() { return status == QuestStatus.TERSEDIA; }

    @Override
    public void complete() {
        // TODO
        status = QuestStatus.SELESAI;
    }

    public void resetToAvailable() {
        // TODO
        status = QuestStatus.TERSEDIA;
    }

    public String getId() { return this.id; }
    public String getName() { return this.name; }
    public Difficulty getDifficulty() { return this.difficulty; }
    public Monster getMonster() { return this.monster; }
    public QuestStatus getStatus() { return this.status; }

    @Override
    public abstract String toString();
}