package test.services;

import entities.Wanderer;
import exception.DuplicateWandererException;

import org.junit.Before;
import org.junit.Test;

import quests.Quest;
import quests.enums.Difficulty;
import quests.enums.QuestStatus;
import services.AuthService;
import services.GameDayService;
import services.MonsterService;
import services.QuestService;
import services.WandererService;

import static org.junit.Assert.assertEquals;

public class GameDayServiceTest {
    private WandererService wandererService;
    private MonsterService monsterService;
    private QuestService questService;
    private GameDayService gameDayService;

    @Before
    public void setUp() {
        AuthService authService = new AuthService();
        wandererService = new WandererService(authService);
        monsterService = new MonsterService();
        questService = new QuestService(monsterService);
        gameDayService = new GameDayService(wandererService, questService);
    }

    @Test
    public void testAdvanceDayIncrementsDayAndResetsData() throws DuplicateWandererException {
        wandererService.addWanderer("Frieren", "frieren", "staff123", 100, 50, 20, "3");
        monsterService.addMonster("Slime", 50, 10, 2, 100, 50);
        questService.addQuest("Latihan Harian", "Kalahkan slime", Difficulty.MUDAH, monsterService.fetchMonsterbyId("1"), "1", "0", "0");

        Wanderer wanderer = (Wanderer) wandererService.getWanderers().get(0);
        Quest dailyQuest = questService.fetchQuestbyId("Q1");
        wanderer.takeDamage(40);
        dailyQuest.complete();

        gameDayService.advanceDay();

        assertEquals(2, gameDayService.getCurrentDay());
        assertEquals(wanderer.getMaxHp(), wanderer.getCurrentHp(), 0.001);
        assertEquals(QuestStatus.TERSEDIA, dailyQuest.getStatus());
    }
}
