package test.quests;

import entities.monster.*;
import quests.*;
import quests.enums.*;

import org.junit.Test;
import static org.junit.Assert.*;

public class BountyQuestTest {

    @Test
    public void testConstructorAndGetters() {
        Monster monster = new Monster(1, "Goblin", 100.0, 20.0, 5.0, 50, 10);
        BountyQuest quest = new BountyQuest(
                1,
                "Goblin Hunt",
                "Defeat the goblin",
                Difficulty.MUDAH,
                monster,
                25,
                5
        );

        assertEquals("Q1", quest.getId());
        assertEquals("Goblin Hunt", quest.getName());
        assertEquals("Defeat the goblin", quest.getDescription());
        assertEquals(Difficulty.MUDAH, quest.getDifficulty());
        assertEquals(monster, quest.getMonster());
        assertEquals(QuestStatus.TERSEDIA, quest.getStatus());

        assertEquals(50, quest.getExpReward());
        assertEquals(10, quest.getCoinReward());
        assertEquals(25, quest.getBonusExp());
        assertEquals(5, quest.getBonusCoin());
    }

    @Test
    public void testGetQuestType() {
        Monster monster = new Monster(1, "Goblin", 100.0, 20.0, 5.0, 50, 10);
        BountyQuest quest = new BountyQuest(1, "Goblin Hunt", "Defeat the goblin",
                Difficulty.MUDAH, monster, 25, 5);

        assertEquals("Bounty", quest.getQuestType());
    }

    @Test
    public void testCompletableBehavior() {
        Monster monster = new Monster(1, "Goblin", 100.0, 20.0, 5.0, 50, 10);
        BountyQuest quest = new BountyQuest(1, "Goblin Hunt", "Defeat the goblin",
                Difficulty.MUDAH, monster, 25, 5);

        assertTrue(quest.isCompletable());

        quest.complete();

        assertEquals(QuestStatus.SELESAI, quest.getStatus());
        assertFalse(quest.isCompletable());

        quest.resetToAvailable();

        assertEquals(QuestStatus.TERSEDIA, quest.getStatus());
        assertTrue(quest.isCompletable());
    }

    @Test
    public void testToString() {
        Monster monster = new Monster(1, "Goblin", 100.0, 20.0, 5.0, 50, 10);
        BountyQuest quest = new BountyQuest(
                1,
                "Goblin Hunt",
                "Defeat the goblin",
                Difficulty.MUDAH,
                monster,
                25,
                5
        );

        String expected =
                "ID Quest: Q1\n" +
                "Nama Quest: Goblin Hunt\n" +
                "Tipe Quest: Bounty\n" +
                "Deskripsi Quest: Defeat the goblin\n" +
                "Tingkat Kesulitan: mudah\n" +
                "Monster: Goblin\n" +
                "Level Minimum: 1\n" +
                "Reward Koin: 10\n" +
                "Reward Exp: 50\n" +
                "Bonus Koin: 5\n" +
                "Bonus Exp: 25\n" +
                "Status: tersedia";

        assertEquals(expected, quest.toString());
    }
}