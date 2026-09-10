package services;

import entities.Wanderer;
import entities.monster.Monster;

import exception.InsufficientLevelException;

import quests.BountyQuest;
import quests.DailyQuest;
import quests.Quest;
import quests.RegularQuest;
import quests.enums.Difficulty;
import quests.enums.QuestStatus;

import repository.GameRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Collectors;

public class QuestService {
    private final GameRepository<Quest> questRepository;
    private final MonsterService monsterService;

    public QuestService(MonsterService monsterService) {
        this.monsterService = monsterService;
        this.questRepository = new GameRepository<>();
    }

    public void addQuest(String name, String description, Difficulty difficulty, Monster monster, String type, String bonusExp, String bonusCoin) {
        int id = nextQuestId();
        Quest quest = switch (type) {
            case "1" -> new DailyQuest(id, name, description, difficulty, monster);
            case "2" -> new RegularQuest(id, name, description, difficulty, monster);
            case "3" -> new BountyQuest(id, name, description, difficulty, monster, Integer.parseInt(bonusExp), Integer.parseInt(bonusCoin));
            default -> throw new IllegalArgumentException("Tipe quest harus salah satu dari yang tertera pada list.");
        };
        this.questRepository.add(quest);
    }

    private int nextQuestId() {
        return questRepository.size() + 1;
    }

    public ArrayList<Quest> getQuests() {
        return this.questRepository.findAll();
    }

    public int getQuestsCount() {
        return questRepository.size();
    }

    public ArrayList<Quest> filterQuestByDifficulty(Difficulty difficulty) {
        return questRepository.findAll(
            quest -> quest != null && quest.getDifficulty() == difficulty
        );
    }

    public Optional<ArrayList<Quest>> filterQuestByDifficultyName(String difficultyRaw) {
        return Difficulty.fromString(difficultyRaw)
            .map(this::filterQuestByDifficulty);
    }

    public ArrayList<Quest> filterQuestByStatus(QuestStatus status) {
        return questRepository.findAll(
            quest -> quest != null && quest.getStatus() == status
        );
    }

    public ArrayList<Quest> sortQuestByReward(boolean asc) {
        ArrayList<Quest> result = getQuests();

        Comparator<Quest> comparator = Comparator.comparingInt(
            quest -> quest.getCoinReward()
        );

        if (!asc) {
            comparator = comparator.reversed();
        }

        result.sort(comparator);
        return result;
    }

    public ArrayList<Quest> sortQuestByDifficulty(boolean asc) {
        ArrayList<Quest> result = getQuests();

        Comparator<Quest> comparator = Comparator.comparingInt(
            quest -> quest.getDifficulty().getMinWandererLevel()
        );

        if (!asc) {
            comparator = comparator.reversed();
        }

        result.sort(comparator);
        return result;
    }

    public void resetDailyQuests() {
        questRepository.findAll().stream()
            .filter(quest -> quest.getQuestType().equals("Daily"))
            .map(quest -> (DailyQuest) quest)
            .forEach(DailyQuest::reset);
    }

    public void validateQuestLevelRequirement(Wanderer wanderer, Quest quest) throws InsufficientLevelException {
        if (!wanderer.canTakeQuest(quest.getDifficulty())) {
            throw new InsufficientLevelException(wanderer.getLevel(), quest.getDifficulty().getMinWandererLevel());
        }
    }

    public boolean isQuestDifficultyValid(String questDifficulty) {
        return Difficulty.fromString(questDifficulty).isPresent();
    }

    public boolean isQuestTypeValid(String number) {
        return number != null && (number.equals("1") || number.equals("2") || number.equals("3"));
    }

    public Optional<Quest> findQuestById(String id) {
        return Optional.ofNullable(
            questRepository.find(entity -> entity != null && entity.getId().equals(id))
        );
    }

    public boolean isQuestIdFound(String questId) {
        return findQuestById(questId).isPresent();
    }

    public boolean isQuestIdValid(String questId) {
        return questId != null && questId.length() >= 2 && questId.charAt(0) == 'Q' && isQuestIdFound(questId);
    }

    public void validateQuestInput(String name, String description, String difficulty, String monsterNumber, String type) {
        if (!isAlphaNumericSpace(name)) {
            throw new IllegalArgumentException("Nama quest hanya boleh berisi karakter alfanumerik dan spasi, serta tidak boleh kosong.");
        }
        if (description == null || description.isEmpty()) {
            throw new IllegalArgumentException("Deskripsi quest tidak boleh kosong.");
        }
        if (!isQuestDifficultyValid(difficulty)) {
            throw new IllegalArgumentException("Tingkat kesulitan quest hanya boleh: mudah, menengah, atau sulit.");
        }
        if (!isPositiveInteger(monsterNumber)) {
            throw new IllegalArgumentException("Nomor monster hanya boleh berisi angka dan tidak boleh kosong.");
        }
        if (Integer.parseInt(monsterNumber) > monsterService.getMonstersCount()) {
            throw new IllegalArgumentException("Nomor monster harus salah satu dari yang tertera pada list.");
        }
        if (!isQuestTypeValid(type)) {
            throw new IllegalArgumentException("Tipe quest harus salah satu dari yang tertera pada list.");
        }
    }

    public void validateBountyRewardInput(String bonusExp, String bonusCoin) {
        if (!isNonNegativeInteger(bonusExp)) {
            throw new IllegalArgumentException("Bonus exp harus bilangan bulat nonnegatif.");
        }
        if (!isNonNegativeInteger(bonusCoin)) {
            throw new IllegalArgumentException("Bonus coin harus bilangan bulat nonnegatif.");
        }
    }

    public void validateQuestFilterType(String filterType) {
        if (filterType == null || (!filterType.equals("1") && !filterType.equals("2"))) {
            throw new IllegalArgumentException("Pilihan tidak valid. Harap masukkan pilihan dengan benar.");
        }
    }

    public void validateQuestStatusInput(String status) {
        if (QuestStatus.fromString(status) == null) {
            throw new IllegalArgumentException("Pilihan tidak valid. Harap masukkan pilihan dengan benar.");
        }
    }

    public void validateQuestDifficultyInput(String difficulty) {
        if (!isQuestDifficultyValid(difficulty)) {
            throw new IllegalArgumentException("Pilihan tidak valid. Harap masukkan pilihan dengan benar.");
        }
    }

    public void validateQuestSortType(String sortType) {
        if (sortType == null || (!sortType.equals("1") && !sortType.equals("2"))) {
            throw new IllegalArgumentException("Urutan tidak valid. Harap masukkan urutan dengan benar.");
        }
    }

    public void validateQuestIdInput(String questId) {
        if (!isQuestIdFound(questId)) {
            throw new IllegalArgumentException("Quest tidak ditemukan.");
        }
    }

    public Quest fetchQuestbyId(String id) {
        return findQuestById(id)
            .orElseThrow(() -> new IllegalArgumentException("Quest tidak ditemukan."));
    }

    public String showQuests(ArrayList<Quest> questsToShow) {
        if (getQuestsCount() == 0) {
            return "Belum ada quest terdaftar\n";
        }
        return questsToShow.stream()
            .map(Quest::toString)
            .collect(Collectors.joining("\n\n", "", "\n\n"));
    }

    public String showAvailableQuest() {
        return questRepository.findAll().stream()
            .map(quest -> quest.getId() + ". " + quest.getName() + " (" + quest.getQuestType() + ") - Difficulty: " + quest.getDifficulty().getDisplayName())
            .collect(Collectors.joining("\n", "", questRepository.isEmpty() ? "" : "\n"));
    }

    private boolean isNonNegativeInteger(String number) {
        if (number == null || number.isEmpty()) {
            return false;
        }
        return number.matches("^\\d+$");
    }

    private boolean isPositiveInteger(String number) {
        return isNonNegativeInteger(number) && !number.equals("0");
    }

    private boolean isAlphaNumericSpace(String str) {
        return str != null
            && !str.isEmpty()
            && str.chars().allMatch(ch -> Character.isLetterOrDigit(ch) || ch == ' ');
    }
}
