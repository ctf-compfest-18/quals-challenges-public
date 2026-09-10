package utils;

import entities.monster.Monster;

import exception.BurhanQuestException;
import exception.DataFileException;
import exception.DuplicateWandererException;
import exception.InvalidFormatException;

import services.GameManager;

import quests.enums.Difficulty;

import utils.enums.AdminIOCsvMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ReportImporter {

    private final GameManager gameManager;
    private final ConsoleTablePrinter printer;

    public ReportImporter(GameManager gameManager, ConsoleTablePrinter printer) {
        this.gameManager = gameManager;
        this.printer = printer;
    }

    public void importFromCsv(String path, AdminIOCsvMode mode) throws BurhanQuestException {
        CsvTable table = readCsvTable(path);
        printPreview(table);

        if (mode == AdminIOCsvMode.QUEST) {
            validateQuestRows(path, table)
                .forEach(row -> gameManager.addQuest(row.name, row.description, row.difficulty, row.monster, row.typeCode, row.bonusExp, row.bonusCoin));
        } else if (mode == AdminIOCsvMode.WANDERER) {
            try {
                validateWandererRows(path, table)
                    .forEach(this::addImportedWanderer);
            } catch (ImportActionException e) {
                throw e.getBurhanQuestException();
            }
        } else {
            throw new InvalidFormatException(path, "mode impor tidak dikenal");
        }
    }

    private void addImportedWanderer(WandererImportRow row) {
        try {
            gameManager.addWanderer(row.name, row.username, row.password, row.maxHp, row.attack, row.defense, row.jobCode);
        } catch (DuplicateWandererException e) {
            throw new ImportActionException(e);
        }
    }

    private CsvTable readCsvTable(String path) throws DataFileException {
        DataFileReader reader = new DataFileReader(path);
        try {
            List<List<String>> rawRows = reader.readAll();
            if (rawRows.isEmpty()) {
                throw new InvalidFormatException(path, "file kosong");
            }
            return new CsvTable(rawRows.get(0), rawRows.subList(1, rawRows.size()));
        } finally {
            reader.close();
        }
    }

    private void printPreview(CsvTable table) {
        printer.printPreviewTable("Data yang akan disimpan:", table.headers, table.rows);
    }

    private ArrayList<QuestImportRow> validateQuestRows(String path, CsvTable table) throws DataFileException {
        ArrayList<Map<String, String>> mappedRows = mapRows(path, table);

        try {
            return IntStream.range(0, mappedRows.size())
                .mapToObj(i -> validateQuestRow(path, mappedRows.get(i), i + 2))
                .collect(Collectors.toCollection(ArrayList::new));
        } catch (ImportActionException e) {
            throw (DataFileException) e.getBurhanQuestException();
        }
    }

    private QuestImportRow validateQuestRow(String path, Map<String, String> row, int rowNumber) {
        try {
            String name = require(path, row, rowNumber, "name", "questname", "nama", "namaquest");
            String description = require(path, row, rowNumber, "description", "deskripsi", "questdescription", "deskripsiquest");
            String difficultyInput = require(path, row, rowNumber, "difficulty", "kesulitan", "tingkatkesulitan").toLowerCase();
            String typeInput = require(path, row, rowNumber, "type", "questtype", "tipe", "tipequest");
            String monsterInput = require(path, row, rowNumber, "monster", "monsterid", "monstername", "namamonster");
            String bonusExp = CsvFieldCodec.defaultIfBlank(get(row, "bonusexp", "bonus_exp"), "0");
            String bonusCoin = CsvFieldCodec.defaultIfBlank(get(row, "bonuscoin", "bonus_coin", "bonuskoin", "bonus_koin"), "0");
            String status = get(row, "status", "queststatus", "statusquest");

            if (name == null || !name.matches("^[A-Za-z0-9 ]+$")) {
                throw new InvalidFormatException(path, "nama quest pada baris " + rowNumber + " tidak valid");
            }
            if (description == null || description.isEmpty()) {
                throw new InvalidFormatException(path, "deskripsi quest pada baris " + rowNumber + " tidak boleh kosong");
            }
            if (!gameManager.isQuestDifficultyValid(difficultyInput)) {
                throw new InvalidFormatException(path, "difficulty pada baris " + rowNumber + " tidak valid");
            }
            if (!typeInput.equals(typeInput.toLowerCase()) || (!typeInput.equals("daily") && !typeInput.equals("regular") && !typeInput.equals("bounty"))) {
                throw new InvalidFormatException(path, "type pada baris " + rowNumber + " harus daily, regular, atau bounty dalam lowercase");
            }
            if (!gameManager.isNonNegativeInteger(bonusExp) || !gameManager.isNonNegativeInteger(bonusCoin)) {
                throw new InvalidFormatException(path, "bonus reward pada baris " + rowNumber + " harus bilangan bulat non-negatif");
            }
            if (status != null && !status.trim().isEmpty() && !status.equalsIgnoreCase("tersedia") && !status.equalsIgnoreCase("selesai")) {
                throw new InvalidFormatException(path, "status pada baris " + rowNumber + " tidak valid");
            }

            Monster monster = findMonster(monsterInput);
            if (monster == null) {
                throw new InvalidFormatException(path, "monster pada baris " + rowNumber + " tidak ditemukan");
            }

            QuestImportRow importRow = new QuestImportRow();
            importRow.name = name;
            importRow.description = description;
            importRow.difficulty = Difficulty.fromString(difficultyInput)
                .orElseThrow(() -> new IllegalStateException("Difficulty sudah divalidasi tetapi tidak dapat dipetakan."));
            importRow.monster = monster;
            importRow.typeCode = CsvFieldCodec.questTypeToCode(typeInput);
            importRow.bonusExp = bonusExp;
            importRow.bonusCoin = bonusCoin;
            return importRow;
        } catch (DataFileException e) {
            throw new ImportActionException(e);
        }
    }

    private ArrayList<WandererImportRow> validateWandererRows(String path, CsvTable table) throws BurhanQuestException {
        ArrayList<Map<String, String>> mappedRows = mapRows(path, table);
        Set<String> importedUsernames = new HashSet<>();
        ArrayList<WandererImportRow> result;

        try {
            result = IntStream.range(0, mappedRows.size())
                .mapToObj(i -> validateWandererRow(path, mappedRows.get(i), i + 2, importedUsernames))
                .collect(Collectors.toCollection(ArrayList::new));
        } catch (ImportActionException e) {
            throw e.getBurhanQuestException();
        }

        String duplicateUsername = result.stream()
            .map(row -> row.username)
            .filter(gameManager::isUsernameTaken)
            .findFirst()
            .orElse(null);
        if (duplicateUsername != null) {
            throw new DuplicateWandererException(duplicateUsername);
        }

        return result;
    }

    private WandererImportRow validateWandererRow(String path, Map<String, String> row, int rowNumber, Set<String> importedUsernames) {
        try {
            String name = require(path, row, rowNumber, "name", "wanderername", "nama", "namapengembara");
            String username = require(path, row, rowNumber, "username", "user_name");
            String password = require(path, row, rowNumber, "password", "kataSandi", "katasandi");
            String maxHpInput = get(row, "maxhp", "max_hp", "hp", "healthpoint");
            String attackInput = require(path, row, rowNumber, "attack", "attackpower", "attack_power", "atk");
            String defenseInput = require(path, row, rowNumber, "defense", "def");
            String jobInput = CsvFieldCodec.defaultIfBlank(get(row, "job", "jobclass", "job_class", "class"), "1");

            if (!name.matches("^[A-Za-z ]+$")) {
                throw new InvalidFormatException(path, "nama pengembara pada baris " + rowNumber + " tidak valid");
            }
            if (!username.matches("^[A-Za-z0-9]+$")) {
                throw new InvalidFormatException(path, "username pada baris " + rowNumber + " tidak valid");
            }
            if (!password.matches("^[A-Za-z0-9]+$")) {
                throw new InvalidFormatException(path, "password pada baris " + rowNumber + " tidak valid");
            }
            if (!importedUsernames.add(username.toLowerCase())) {
                throw new InvalidFormatException(path, "username pada baris " + rowNumber + " duplikat di file impor");
            }

            double maxHp = parseMaxHp(path, maxHpInput, rowNumber);
            if (!gameManager.isPositiveDouble(attackInput) || !gameManager.isPositiveDouble(defenseInput)) {
                throw new InvalidFormatException(path, "stat pengembara pada baris " + rowNumber + " harus bilangan positif");
            }

            String jobCode = CsvFieldCodec.jobToCode(jobInput);
            if (jobCode == null) {
                throw new InvalidFormatException(path, "job class pada baris " + rowNumber + " tidak valid");
            }

            WandererImportRow importRow = new WandererImportRow();
            importRow.name = name;
            importRow.username = username;
            importRow.password = password;
            importRow.maxHp = maxHp;
            importRow.attack = Double.parseDouble(attackInput);
            importRow.defense = Double.parseDouble(defenseInput);
            importRow.jobCode = jobCode;
            return importRow;
        } catch (BurhanQuestException e) {
            throw new ImportActionException(e);
        }
    }

    private double parseMaxHp(String path, String value, int rowNumber) throws InvalidFormatException {
        if (value == null || value.isEmpty()) {
            return 1.0;
        }
        double maxHp;
        try {
            maxHp = Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new InvalidFormatException(path, "max_hp pada baris " + rowNumber + " harus bilangan non-negatif");
        }
        if (maxHp < 0) {
            throw new InvalidFormatException(path, "max_hp pada baris " + rowNumber + " harus bilangan non-negatif");
        }
        if (maxHp == 0) {
            return 1.0;
        }
        return maxHp;
    }

    private ArrayList<Map<String, String>> mapRows(String path, CsvTable table) throws DataFileException {
        String emptyHeader = table.headers.stream()
            .filter(header -> CsvFieldCodec.normalizeKey(header).isEmpty())
            .findFirst()
            .orElse(null);
        if (emptyHeader != null) {
            throw new InvalidFormatException(path, "header tidak boleh kosong");
        }

        Set<String> uniqueHeaders = new HashSet<>();
        String duplicateHeader = table.headers.stream()
            .filter(header -> !uniqueHeaders.add(CsvFieldCodec.normalizeKey(header)))
            .findFirst()
            .orElse(null);
        if (duplicateHeader != null) {
            throw new InvalidFormatException(path, "header duplikat: " + duplicateHeader);
        }

        ArrayList<String> normalizedHeaders = table.headers.stream()
            .map(CsvFieldCodec::normalizeKey)
            .collect(Collectors.toCollection(ArrayList::new));

        return table.rows.stream()
            .map(rawRow -> mapRawRow(normalizedHeaders, rawRow))
            .collect(Collectors.toCollection(ArrayList::new));
    }

    private Map<String, String> mapRawRow(ArrayList<String> normalizedHeaders, List<String> rawRow) {
        return IntStream.range(0, normalizedHeaders.size())
            .boxed()
            .collect(Collectors.toMap(
                normalizedHeaders::get,
                rawRow::get,
                (existing, replacement) -> replacement,
                HashMap::new
            ));
    }

    private Monster findMonster(String value) {
        if (value == null) {
            return null;
        }

        String trimmedValue = value.trim();
        if (trimmedValue.isEmpty()) {
            return null;
        }

        String normalizedInput = trimmedValue.toLowerCase();
        String normalizedNumericInput = normalizeMonsterNumber(normalizedInput);

        return gameManager.getMonsters().stream()
            .filter(monster -> monster != null)
            .filter(monster -> matchesMonster(monster, trimmedValue, normalizedInput, normalizedNumericInput))
            .findFirst()
            .orElse(null);
    }

    private boolean matchesMonster(Monster monster, String trimmedValue, String normalizedInput, String normalizedNumericInput) {
        return matchesMonsterId(monster.getMonsterId(), normalizedInput, normalizedNumericInput)
            || matchesMonsterName(monster.getName(), trimmedValue);
    }

    private boolean matchesMonsterId(String monsterId, String normalizedInput, String normalizedNumericInput) {
        if (monsterId == null) {
            return false;
        }
        String normalizedMonsterId = monsterId.trim().toLowerCase();
        String normalizedNumericMonsterId = normalizeMonsterNumber(normalizedMonsterId);
        return normalizedMonsterId.equals(normalizedInput) || normalizedNumericMonsterId.equals(normalizedNumericInput);
    }

    private boolean matchesMonsterName(String monsterName, String trimmedValue) {
        return monsterName != null && monsterName.trim().equalsIgnoreCase(trimmedValue);
    }

    private String normalizeMonsterNumber(String value) {
        return value.length() > 1 && value.startsWith("m") ? value.substring(1) : value;
    }

    private String require(String path, Map<String, String> row, int rowNumber, String... keys) throws InvalidFormatException {
        String value = get(row, keys);

        if (value == null || value.trim().isEmpty()) {
            throw new InvalidFormatException(path);
        }

        return value.trim();
    }

    private String get(Map<String, String> row, String... keys) {
        return Arrays.stream(keys)
            .map(CsvFieldCodec::normalizeKey)
            .filter(row::containsKey)
            .map(row::get)
            .findFirst()
            .map(value -> value == null ? "" : value.trim())
            .orElse("");
    }

    private static class CsvTable {
        private final List<String> headers;
        private final List<List<String>> rows;

        private CsvTable(List<String> headers, List<List<String>> rows) {
            this.headers = headers;
            this.rows = rows;
        }
    }

    private static class QuestImportRow {
        private String name;
        private String description;
        private Difficulty difficulty;
        private Monster monster;
        private String typeCode;
        private String bonusExp;
        private String bonusCoin;
    }

    private static class WandererImportRow {
        private String name;
        private String username;
        private String password;
        private double maxHp;
        private double attack;
        private double defense;
        private String jobCode;
    }

    private static class ImportActionException extends RuntimeException {
        private final BurhanQuestException burhanQuestException;

        private ImportActionException(BurhanQuestException burhanQuestException) {
            super(burhanQuestException);
            this.burhanQuestException = burhanQuestException;
        }

        private BurhanQuestException getBurhanQuestException() {
            return burhanQuestException;
        }
    }
}
