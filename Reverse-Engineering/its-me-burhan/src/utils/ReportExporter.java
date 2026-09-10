package utils;

import entities.Wanderer;
import entities.monster.Monster;

import exception.DataFileException;
import exception.InvalidFormatException;

import services.GameManager;

import quests.Quest;

import utils.enums.AdminIOCsvMode;

import java.util.List;
import java.util.stream.Collectors;

public class ReportExporter {

    private final GameManager gameManager;
    private final ConsoleTablePrinter printer;

    public ReportExporter(GameManager gameManager, ConsoleTablePrinter printer) {
        this.gameManager = gameManager;
        this.printer = printer;
    }

    public void previewExport(AdminIOCsvMode mode) throws DataFileException {
        if (mode == AdminIOCsvMode.QUEST) {
            printer.printPreviewTable("Data yang akan diekspor:", getQuestPreviewHeaders(), getQuestPreviewRows());
        } else if (mode == AdminIOCsvMode.WANDERER) {
            printer.printPreviewTable("Data yang akan diekspor:", getWandererPreviewHeaders(), getWandererPreviewRows());
        } else {
            throw new InvalidFormatException("preview", "mode ekspor tidak dikenal");
        }
    }

    public void exportToCsv(String path, AdminIOCsvMode mode) throws DataFileException {
        DataFileWriter writer = DataFileWriter.forCsv(path);
        try {
            if (mode == AdminIOCsvMode.QUEST) {
                writeQuestCsv(writer);
            } else if (mode == AdminIOCsvMode.WANDERER) {
                writeWandererCsv(writer);
            } else {
                throw new InvalidFormatException(path, "mode ekspor tidak dikenal");
            }
        } finally {
            writer.close();
        }
    }

    public void exportToTxt(String path, AdminIOCsvMode mode) throws DataFileException {
        DataFileWriter writer = DataFileWriter.forTxt(path);
        try {
            if (mode == AdminIOCsvMode.QUEST) {
                writeQuestTxt(writer);
            } else if (mode == AdminIOCsvMode.WANDERER) {
                writeWandererTxt(writer);
            } else {
                throw new InvalidFormatException(path, "mode ekspor tidak dikenal");
            }
        } finally {
            writer.close();
        }
    }

    private void writeQuestCsv(DataFileWriter writer) {
        writer.writeCsvRow(CsvFieldCodec.list("id", "name", "description", "difficulty", "type", "monster_id", "monster_name", "exp_reward", "coin_reward", "bonus_exp", "bonus_coin", "minimum_level", "status"));
        gameManager.getQuests().stream()
            .map(this::toQuestCsvRow)
            .forEach(writer::writeCsvRow);
    }

    private List<String> toQuestCsvRow(Quest quest) {
        Monster monster = quest.getMonster();
        return CsvFieldCodec.list(
                quest.getId(),
                quest.getName(),
                quest.getDescription(),
                CsvFieldCodec.difficultyToCsv(quest.getDifficulty()),
                CsvFieldCodec.questTypeToCsv(quest),
                monster.getMonsterId(),
                monster.getName(),
                String.valueOf(quest.getExpReward()),
                String.valueOf(quest.getCoinReward()),
                String.valueOf(quest.getBonusExp()),
                String.valueOf(quest.getBonusCoin()),
                String.valueOf(quest.getDifficulty().getMinWandererLevel()),
                CsvFieldCodec.statusToCsv(quest.getStatus())
        );
    }

    private void writeWandererCsv(DataFileWriter writer) {
        writer.writeCsvRow(CsvFieldCodec.list("id", "name", "username", "job_class", "level", "exp", "coins", "current_hp", "max_hp", "attack_power", "defense"));
        gameManager.getWanderers().stream()
            .map(user -> (Wanderer) user)
            .map(this::toWandererCsvRow)
            .forEach(writer::writeCsvRow);
    }

    private List<String> toWandererCsvRow(Wanderer wanderer) {
        return CsvFieldCodec.list(
                wanderer.getId(),
                wanderer.getName(),
                wanderer.getUsername(),
                CsvFieldCodec.jobToCsv(wanderer),
                String.valueOf(wanderer.getLevel()),
                String.valueOf(wanderer.getExp()),
                String.valueOf(wanderer.getCoins()),
                CsvFieldCodec.formatNumber(wanderer.getCurrentHp()),
                CsvFieldCodec.formatNumber(wanderer.getMaxHp()),
                CsvFieldCodec.formatNumber(wanderer.getAttackPower()),
                CsvFieldCodec.formatNumber(wanderer.getDefense())
        );
    }

    private void writeQuestTxt(DataFileWriter writer) {
        writer.writeLine("=== Report Quest BurhanQuest ===");
        writer.writeLine("Total quest: " + gameManager.getQuestsCount());
        writer.writeLine("");

        List<String> headers = CsvFieldCodec.list("ID", "Nama", "Difficulty", "Type", "Monster", "Min Lv", "EXP", "Coin", "Status");
        List<List<String>> rows = gameManager.getQuests().stream()
            .map(quest -> CsvFieldCodec.list(
                    quest.getId(),
                    quest.getName(),
                    quest.getDifficulty().getDisplayName(),
                    quest.getQuestType(),
                    quest.getMonster().getName(),
                    String.valueOf(quest.getDifficulty().getMinWandererLevel()),
                    String.valueOf(quest.getExpReward() + quest.getBonusExp()),
                    String.valueOf(quest.getCoinReward() + quest.getBonusCoin()),
                    quest.getStatus().getDisplayName()
            ))
            .collect(Collectors.toList());
        writer.writeTable(headers, rows);
    }

    private void writeWandererTxt(DataFileWriter writer) {
        writer.writeLine("=== Report Pengembara BurhanQuest ===");
        writer.writeLine("Total pengembara: " + gameManager.getWanderersCount());
        writer.writeLine("");

        List<String> headers = CsvFieldCodec.list("ID", "Nama", "Username", "Job", "Lv", "EXP", "Coin", "HP", "ATK", "DEF");
        List<List<String>> rows = gameManager.getWanderers().stream()
            .map(user -> (Wanderer) user)
            .map(wanderer -> CsvFieldCodec.list(
                    wanderer.getId(),
                    wanderer.getName(),
                    wanderer.getUsername(),
                    CsvFieldCodec.jobToCsv(wanderer),
                    String.valueOf(wanderer.getLevel()),
                    String.valueOf(wanderer.getExp()),
                    String.valueOf(wanderer.getCoins()),
                    CsvFieldCodec.formatNumber(wanderer.getCurrentHp()) + "/" + CsvFieldCodec.formatNumber(wanderer.getMaxHp()),
                    CsvFieldCodec.formatNumber(wanderer.getAttackPower()),
                    CsvFieldCodec.formatNumber(wanderer.getDefense())
            ))
            .collect(Collectors.toList());
        writer.writeTable(headers, rows);
    }

    private List<String> getQuestPreviewHeaders() {
        return CsvFieldCodec.list("ID Quest", "Nama Quest", "Deskripsi", "Kesulitan", "Tipe", "Monster", "Min. Lv", "Status");
    }

    private List<List<String>> getQuestPreviewRows() {
        return gameManager.getQuests().stream()
            .map(quest -> {
                Monster monster = gameManager.fetchMonsterbyId(quest.getMonster().getMonsterId().substring(1));
                return CsvFieldCodec.list(
                        quest.getId(),
                        quest.getName(),
                        quest.getDescription(),
                        quest.getDifficulty().getDisplayName(),
                        quest.getQuestType(),
                        monster == null ? "" : monster.getName(),
                        String.valueOf(quest.getDifficulty().getMinWandererLevel()),
                        quest.getStatus().getDisplayName()
                );
            })
            .collect(Collectors.toList());
    }

    private List<String> getWandererPreviewHeaders() {
        return CsvFieldCodec.list("ID", "Nama", "Username", "Level", "Exp", "Koin", "HP Saat Ini", "HP Maksimal", "Attack", "Defense");
    }

    private List<List<String>> getWandererPreviewRows() {
        return gameManager.getWanderers().stream()
            .map(user -> (Wanderer) user)
            .map(wanderer -> CsvFieldCodec.list(
                    wanderer.getId(),
                    wanderer.getName(),
                    wanderer.getUsername(),
                    String.valueOf(wanderer.getLevel()),
                    String.valueOf(wanderer.getExp()),
                    String.valueOf(wanderer.getCoins()),
                    CsvFieldCodec.formatNumber(wanderer.getCurrentHp()),
                    CsvFieldCodec.formatNumber(wanderer.getMaxHp()),
                    CsvFieldCodec.formatNumber(wanderer.getAttackPower()),
                    CsvFieldCodec.formatNumber(wanderer.getDefense())
            ))
            .collect(Collectors.toList());
    }
}
