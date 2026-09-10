package quests;

import quests.enums.Difficulty;

import entities.monster.Monster;

public class RegularQuest extends Quest {

    public RegularQuest(int idNumber, String name, String description,
                        Difficulty difficulty, Monster monster) {
        super(idNumber, name, description, difficulty, monster);
    }

    @Override
    public String getQuestType() {
        return "Regular";
    }

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
                "Status: " + getStatus().getDisplayName()
            );
    }
}