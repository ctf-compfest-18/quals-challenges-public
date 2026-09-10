package quests;

import quests.enums.Difficulty;
import quests.interfaces.Repeatable;

import entities.monster.Monster;

public class DailyQuest extends Quest implements Repeatable {
    private int timesCompleted;

    public DailyQuest(int idNumber, String name, String description,
                      Difficulty difficulty, Monster monster) {
        super(idNumber, name, description, difficulty, monster);
        // TODO
        this.timesCompleted = 0;
    }

    @Override
    public String getQuestType() {
        return "Daily";
    }

    @Override
    public void complete() {
        // TODO: lakukan increment pada timesCompleted
        this.timesCompleted++;
    }

    @Override
    public void reset() {
        // TODO
        this.timesCompleted = 0;
    }

    @Override
    public int getTimesCompleted() { return this.timesCompleted; }

    @Override
    public String toString() {
        // TODO
        return ("ID Quest: " + getId() + "\n" +
                "Nama Quest: " + getName() + "\n" +
                "Tipe Quest: " + getQuestType() + "\n" +
                "Deskripsi Quest: " + getDescription() + "\n" +
                "Tingkat Kesulitan: " + getDifficulty().getDisplayName() + "\n" +
                "Monster: " + getMonster().getName() + "\n" +
                "Level Minimum: " + getDifficulty().getMinWandererLevel() + "\n" +
                "Reward Koin: " + getCoinReward() + "\n" +
                "Reward Exp: " + getExpReward() + "\n" +
                "Status: " + getStatus().getDisplayName() + "\n" + 
                "Sudah Diselesaikan: " + getTimesCompleted() + " kali"
            );
    }
}