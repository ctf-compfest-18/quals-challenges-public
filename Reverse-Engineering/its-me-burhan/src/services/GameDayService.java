package services;

import entities.Wanderer;
import quests.DailyQuest;

public class GameDayService {
    private int currentDay;
    private final WandererService wandererService;
    private final QuestService questService;

    public GameDayService(WandererService wandererService, QuestService questService) {
        this.currentDay = 1;
        this.wandererService = wandererService;
        this.questService = questService;
    }

    public void advanceDay() {
        this.currentDay++;
        wandererService.getWanderers().stream()
                .map(user -> (Wanderer) user)
                .forEach(Wanderer::resetCurrentHp);
        questService.getQuests().stream()
                .filter(quest -> quest instanceof DailyQuest)
                .map(quest -> (DailyQuest) quest)
                .forEach(DailyQuest::reset);
    }

    public int getCurrentDay() {
        return this.currentDay;
    }
}
