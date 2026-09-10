package services;

import entities.User;
import entities.Wanderer;
import entities.monster.Monster;

import exception.DuplicateWandererException;
import exception.InsufficientLevelException;

import quests.Quest;
import quests.enums.Difficulty;
import quests.enums.QuestStatus;

import java.util.ArrayList;
import java.util.Optional;

public class GameManager {
    private final AuthService authService;
    private final WandererService wandererService;
    private final MonsterService monsterService;
    private final QuestService questService;
    private final GameDayService gameDayService;

    public GameManager() {
        this.authService = new AuthService();
        this.wandererService = new WandererService(authService);
        this.monsterService = new MonsterService();
        this.questService = new QuestService(monsterService);
        this.gameDayService = new GameDayService(wandererService, questService);
    }

    public User login(String username, String password) {
        return authService.login(username, password);
    }

    public void addWanderer(String name, String username, String password, double maxHp, double attack, double defense, String job) throws DuplicateWandererException {
        wandererService.addWanderer(name, username, password, maxHp, attack, defense, job);
    }

    public boolean handlePostBattleLoseStreak(Wanderer wanderer) {
        return wandererService.handlePostBattleLoseStreak(wanderer);
    }

    public String addWandererJobString(String job) {
        return wandererService.addWandererJobString(job);
    }

    public boolean isUsernameTaken(String username) {
        return authService.isUsernameTaken(username);
    }

    public void addMonster(String name, double maxHp, double attackPower, double defense, int expReward, int coinReward) {
        monsterService.addMonster(name, maxHp, attackPower, defense, expReward, coinReward);
    }

    public void addQuest(String name, String description, Difficulty difficulty, Monster monster, String type, String bonusExp, String bonusCoin) {
        questService.addQuest(name, description, difficulty, monster, type, bonusExp, bonusCoin);
    }

    public ArrayList<Quest> filterQuestByDifficulty(Difficulty difficulty) {
        return questService.filterQuestByDifficulty(difficulty);
    }

    public Optional<ArrayList<Quest>> filterQuestByDifficultyName(String difficultyRaw) {
        return questService.filterQuestByDifficultyName(difficultyRaw);
    }

    public ArrayList<Quest> filterQuestByStatus(QuestStatus status) {
        return questService.filterQuestByStatus(status);
    }

    public ArrayList<User> filterWandererByLevel(int min, int max) {
        return wandererService.filterWandererByLevel(min, max);
    }

    public ArrayList<Quest> sortQuestByReward(boolean asc) {
        return questService.sortQuestByReward(asc);
    }

    public ArrayList<Quest> sortQuestByDifficulty(boolean asc) {
        return questService.sortQuestByDifficulty(asc);
    }

    public ArrayList<User> sortWandererByName(boolean asc) {
        return wandererService.sortWandererByName(asc);
    }

    public ArrayList<User> sortWandererByLevel(boolean asc) {
        return wandererService.sortWandererByLevel(asc);
    }

    public void advanceDay() {
        gameDayService.advanceDay();
    }

    public boolean isNonNegativeInteger(String number) {
        return authService.isNonNegativeInteger(number);
    }

    public boolean isPositiveInteger(String number) {
        return authService.isPositiveInteger(number);
    }

    public boolean isPositiveDouble(String number) {
        return authService.isPositiveDouble(number);
    }

    public boolean isAlphaNumericSpace(String str) {
        return authService.isAlphaNumericSpace(str);
    }

    public boolean isNameInputValid(String nameInput) {
        return authService.isNameInputValid(nameInput);
    }

    public boolean isUsernameInputValid(String usernameInput) {
        return authService.isUsernameInputValid(usernameInput);
    }

    public boolean isUniqueUsername(String username) {
        return authService.isUniqueUsername(username);
    }

    public void validateQuestLevelRequirement(Wanderer wanderer, Quest quest) throws InsufficientLevelException {
        questService.validateQuestLevelRequirement(wanderer, quest);
    }

    public boolean isJobClassValid(String number) {
        return wandererService.isJobClassValid(number);
    }

    public boolean isValidLoginInput(String usernameInput, String passwordInput) {
        return authService.isValidLoginInput(usernameInput, passwordInput);
    }

    public boolean isAdmin(User user) {
        return authService.isAdmin(user);
    }

    public boolean isQuestDifficultyValid(String questDifficulty) {
        return questService.isQuestDifficultyValid(questDifficulty);
    }

    public boolean isQuestTypeValid(String number) {
        return questService.isQuestTypeValid(number);
    }

    public boolean isQuestIdFound(String questId) {
        return questService.isQuestIdFound(questId);
    }

    public boolean isQuestIdValid(String questId) {
        return questService.isQuestIdValid(questId);
    }

    public boolean isLevelIntervalValid(int lower, int upper) {
        return wandererService.isLevelIntervalValid(lower, upper);
    }

    public boolean isSortOrderValid(String order) {
        return authService.isSortOrderValid(order);
    }

    public void validateLoginInput(String usernameInput, String passwordInput) {
        authService.validateLoginInput(usernameInput, passwordInput);
    }

    public void validateQuestInput(String name, String description, String difficulty, String monsterNumber, String type) {
        questService.validateQuestInput(name, description, difficulty, monsterNumber, type);
    }

    public void validateBountyRewardInput(String bonusExp, String bonusCoin) {
        questService.validateBountyRewardInput(bonusExp, bonusCoin);
    }

    public void validateWandererInput(String name, String username, String password, String maxHp, String attackPower, String defense, String job) {
        wandererService.validateWandererInput(name, username, password, maxHp, attackPower, defense, job);
    }

    public void validateMonsterInput(String name, String maxHp, String attackPower, String defense, String expReward, String coinReward) {
        monsterService.validateMonsterInput(name, maxHp, attackPower, defense, expReward, coinReward);
    }

    public void validateQuestFilterType(String filterType) {
        questService.validateQuestFilterType(filterType);
    }

    public void validateQuestStatusInput(String status) {
        questService.validateQuestStatusInput(status);
    }

    public void validateQuestDifficultyInput(String difficulty) {
        questService.validateQuestDifficultyInput(difficulty);
    }

    public void validateLevelIntervalInput(String lowerBound, String upperBound) {
        wandererService.validateLevelIntervalInput(lowerBound, upperBound);
    }

    public void validateQuestSortType(String sortType) {
        questService.validateQuestSortType(sortType);
    }

    public void validateWandererSortType(String sortType) {
        wandererService.validateWandererSortType(sortType);
    }

    public void validateSortOrderInput(String orderType) {
        authService.validateSortOrderInput(orderType);
    }

    public void validateQuestIdInput(String questId) {
        questService.validateQuestIdInput(questId);
    }

    public int getCurrentDay() {
        return gameDayService.getCurrentDay();
    }

    public ArrayList<User> getWanderers() {
        return wandererService.getWanderers();
    }

    public ArrayList<Monster> getMonsters() {
        return monsterService.getMonsters();
    }

    public ArrayList<Quest> getQuests() {
        return questService.getQuests();
    }

    public int getWanderersCount() {
        return wandererService.getWanderersCount();
    }

    public int getMonstersCount() {
        return monsterService.getMonstersCount();
    }

    public int getQuestsCount() {
        return questService.getQuestsCount();
    }

    public Optional<Monster> findMonsterById(String id) {
        return monsterService.findMonsterById(id);
    }

    public Optional<Monster> findMonsterByName(String name) {
        return monsterService.findMonsterByName(name);
    }

    public Optional<Quest> findQuestById(String id) {
        return questService.findQuestById(id);
    }

    public Monster fetchMonsterbyId(String id) {
        return monsterService.fetchMonsterbyId(id);
    }

    public Quest fetchQuestbyId(String id) {
        return questService.fetchQuestbyId(id);
    }

    public String showWanderers(ArrayList<User> wanderers) {
        return wandererService.showWanderers(wanderers);
    }

    public String showQuests(ArrayList<Quest> quests) {
        return questService.showQuests(quests);
    }

    public String showMonsters() {
        return monsterService.showMonsters();
    }

    public String showMonstersName() {
        return monsterService.showMonstersName();
    }

    public String showAvailableQuest() {
        return questService.showAvailableQuest();
    }

    public AuthService getAuthService() {
        return authService;
    }

    public WandererService getWandererService() {
        return wandererService;
    }

    public MonsterService getMonsterService() {
        return monsterService;
    }

    public QuestService getQuestService() {
        return questService;
    }

    public GameDayService getGameDayService() {
        return gameDayService;
    }
}
