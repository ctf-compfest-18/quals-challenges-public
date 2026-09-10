package test.quests;

import entities.monster.*;
import quests.*;
import quests.enums.*;

import org.junit.Test;
import static org.junit.Assert.*;

public class DailyQuestTest {

    @Test
    public void testConstructorAndInitialState() {
        Monster monster = new Monster(1, "Goblin", 100.0, 20.0, 5.0, 50, 10);
        DailyQuest quest = new DailyQuest(1, "Daily Hunt", "Kill goblin", Difficulty.MUDAH, monster);

        assertEquals("Q1", quest.getId());
        assertEquals("Daily Hunt", quest.getName());
        assertEquals("Kill goblin", quest.getDescription());
        assertEquals(Difficulty.MUDAH, quest.getDifficulty());
        assertEquals(monster, quest.getMonster());
        assertEquals(QuestStatus.TERSEDIA, quest.getStatus());

        assertEquals(0, quest.getTimesCompleted());
    }

    @Test
    public void testGetQuestType() {
        Monster monster = new Monster(1, "Goblin", 100.0, 20.0, 5.0, 50, 10);
        DailyQuest quest = new DailyQuest(1, "Daily Hunt", "Kill goblin", Difficulty.MUDAH, monster);

        assertEquals("Daily", quest.getQuestType());
    }

    @Test
    public void testCompleteIncrementsTimesCompleted() {
        Monster monster = new Monster(1, "Goblin", 100.0, 20.0, 5.0, 50, 10);
        DailyQuest quest = new DailyQuest(1, "Daily Hunt", "Kill goblin", Difficulty.MUDAH, monster);

        quest.complete();
        quest.complete();

        assertEquals(2, quest.getTimesCompleted());
    }

    @Test
    public void testResetTimesCompleted() {
        Monster monster = new Monster(1, "Goblin", 100.0, 20.0, 5.0, 50, 10);
        DailyQuest quest = new DailyQuest(1, "Daily Hunt", "Kill goblin", Difficulty.MUDAH, monster);

        quest.complete();
        quest.complete();
        quest.reset();

        assertEquals(0, quest.getTimesCompleted());
    }

    @Test
    public void testInheritedStatusNotChangedByComplete() {
        // IMPORTANT: your complete() overrides parent → status does NOT change
        Monster monster = new Monster(1, "Goblin", 100.0, 20.0, 5.0, 50, 10);
        DailyQuest quest = new DailyQuest(1, "Daily Hunt", "Kill goblin", Difficulty.MUDAH, monster);

        quest.complete();

        assertEquals(QuestStatus.TERSEDIA, quest.getStatus());
        assertTrue(quest.isCompletable());
    }

    @Test
    public void testToString() {
        Monster monster = new Monster(1, "Goblin", 100.0, 20.0, 5.0, 50, 10);
        DailyQuest quest = new DailyQuest(1, "Daily Hunt", "Kill goblin", Difficulty.MUDAH, monster);

        String expected =
                "ID Quest: Q1\n" +
                "Nama Quest: Daily Hunt\n" +
                "Tipe Quest: Daily\n" +
                "Deskripsi Quest: Kill goblin\n" +
                "Tingkat Kesulitan: mudah\n" +
                "Monster: Goblin\n" +
                "Level Minimum: 1\n" +
                "Reward Koin: 10\n" +
                "Reward Exp: 50\n" +
                "Status: tersedia\n" +
                "Sudah Diselesaikan: 0 kali";

        assertEquals(expected, quest.toString());
    }
}