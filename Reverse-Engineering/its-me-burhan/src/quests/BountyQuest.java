package quests;

import quests.enums.Difficulty;
import quests.interfaces.Rewardable;

import entities.monster.Monster;

public class BountyQuest extends Quest implements Rewardable {
    private final int bonusExp;
    private final int bonusCoin;

    public BountyQuest(int idNumber, String name, String description,
                       Difficulty difficulty, Monster monster,
                       int bonusExp, int bonusCoin) {
        super(idNumber, name, description, difficulty, monster);
        // TODO
        this.bonusExp = bonusExp;
        this.bonusCoin = bonusCoin;
    }

    @Override
    public String getQuestType() {
        return "Bounty";
    }

    @Override
    public int getBonusExp() { return this.bonusExp; }

    @Override
    public int getBonusCoin() { return this.bonusCoin; }

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
                "Bonus Koin: " + getBonusCoin() + "\n" +
                "Bonus Exp: " + getBonusExp() + "\n" +
                "Status: " + getStatus().getDisplayName()
            );
    }
}