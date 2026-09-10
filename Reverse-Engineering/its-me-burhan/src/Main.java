import java.time.Instant;
import java.util.Scanner;

// services
import services.GameManager;
import services.BattleManager;

// utils
import utils.ReportGenerator;
import utils.WandererExporter;

// entities
import entities.User;
import entities.Admin;
import entities.Wanderer;

// quests
import quests.Quest;
import quests.enums.Difficulty;
import quests.enums.QuestStatus;

// exception
import exception.BurhanQuestException;
import exception.DuplicateWandererException;
import exception.InsufficientLevelException;
import exception.DataFileException;
import exception.InvalidFileTypeException;
import exception.InvalidFormatException;

// utils
import utils.BurhanLogger;
import utils.enums.AdminIOCsvMode;
import utils.enums.LogCategory;

public class Main {
    // Start and finish banner   
    private static final String BANNER = """

        >>=========================================================================================================================<<
        ||                                                                                                                         ||
        ||                                                                                                                         ||
        ||   ███████████                       █████                              ██████                                 █████     ||
        ||  ░░███░░░░░███                     ░░███                             ███░░░░███                              ░░███      ||
        ||   ░███    ░███ █████ ████ ████████  ░███████    ██████   ████████   ███    ░░███ █████ ████  ██████   █████  ███████    ||
        ||   ░██████████ ░░███ ░███ ░░███░░███ ░███░░███  ░░░░░███ ░░███░░███ ░███     ░███░░███ ░███  ███░░███ ███░░  ░░░███░     ||
        ||   ░███░░░░░███ ░███ ░███  ░███ ░░░  ░███ ░███   ███████  ░███ ░███ ░███   ██░███ ░███ ░███ ░███████ ░░█████   ░███      ||
        ||   ░███    ░███ ░███ ░███  ░███      ░███ ░███  ███░░███  ░███ ░███ ░░███ ░░████  ░███ ░███ ░███░░░   ░░░░███  ░███ ███  ||
        ||   ███████████  ░░████████ █████     ████ █████░░████████ ████ █████ ░░░██████░██ ░░████████░░██████  ██████   ░░█████   ||
        ||  ░░░░░░░░░░░    ░░░░░░░░ ░░░░░     ░░░░ ░░░░░  ░░░░░░░░ ░░░░ ░░░░░    ░░░░░░ ░░   ░░░░░░░░  ░░░░░░  ░░░░░░     ░░░░░    ||
        ||                                                                                                                         ||
        ||                                                                                                                         ||
        >>=========================================================================================================================<<
    """;

    private static Scanner scanner = new Scanner(System.in);
    private static GameManager gm = new GameManager();

    public static void main(String[] args) {

        // Install the deterministic per-instance state (seeded from BURHAN_SEED).
        seal.InstanceBootstrap.seed(gm);

        try {
            System.out.println(BANNER);
            System.out.println("Selamat datang di BurhanQuest!");
            System.out.println();

            boolean running = true;
            while (running) {
                running = showLoginMenu();
            }
        } catch (Exception e) {
            BurhanLogger.getInstance().log(
                LogCategory.ERROR,
                "System",
                "Program mengalami error tak terduga.",
                e
            );
            printError(e);
        }
    }

    private static String readInput(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }

    private static void printError(Exception e) {
        System.out.println(e.getMessage() + "\n");
    }

    static boolean showLoginMenu() {
        System.out.println("=== Hari ke-" + gm.getCurrentDay() + " ===");
        System.out.println("1. Login");
        System.out.println("2. Keluar dari program");
        String loginMenuInput = readInput("Masukkan pilihan: ");

        try {
            switch (loginMenuInput) {
                case "1" -> handleLogin();
                case "2" -> {
                    System.out.println("Terima kasih telah menggunakan BurhanQuest!");
                    System.out.println(BANNER);
                    return false;
                }
                default -> throw new IllegalArgumentException("Pilihan tidak valid. Harap masukkan pilihan dengan benar.");
            }
        } catch (IllegalArgumentException e) {
            printError(e);
        }
        return true;
    }

    private static void handleLogin() {
        String usernameInput = readInput("Masukkan username: ");
        String passwordInput = readInput("Masukkan password: ");

        try {
            // Validasi login user memakai exception
            gm.validateLoginInput(usernameInput, passwordInput);

            User currentUser = gm.login(usernameInput, passwordInput);
            if (currentUser == null) {
                System.out.println("Username atau password salah.\n");
            } else if (gm.isAdmin(currentUser)) {
                boolean adminRunning = true;
                System.out.println(currentUser.getWelcomeMessage() + "\n");
                while (adminRunning) {
                    adminRunning = showAdminMenu((Admin) currentUser);
                }
            } else {
                boolean wandererRunning = true;
                System.out.println(currentUser.getWelcomeMessage() + "\n");
                while (wandererRunning) {
                    wandererRunning = showWandererMenu((Wanderer) currentUser);
                }
            }
        } catch (IllegalArgumentException e) {
            BurhanLogger.getInstance().log(
                LogCategory.AUTH,
                "User: " + usernameInput,
                "Gagal login ke dalam sistem.",
                e
            );
            printError(e);
        }
    }

    static boolean showAdminMenu(Admin admin) {
        System.out.println("=== Menu Admin (Hari ke-" + gm.getCurrentDay() + ") ===");
        System.out.println("1. Lihat daftar quest");
        System.out.println("2. Lihat daftar pengembara");
        System.out.println("3. Tambah quest");
        System.out.println("4. Tambah pengembara");
        System.out.println("5. Tambah monster");
        System.out.println("6. Lihat daftar monster");
        System.out.println("7. Filter daftar quest");
        System.out.println("8. Filter daftar pengembara");
        System.out.println("9. Tampilkan daftar quest terurut");
        System.out.println("10. Tampilkan daftar pengembara terurut");
        System.out.println("11. Lanjut ke hari berikutnya");
        System.out.println("12. Ekspor atau impor data");
        System.out.println("13. Lihat Arsip Tersegel");
        System.out.println("0. Keluar");
        String adminMenuInput = readInput("Masukkan pilihan: ");

        try {
            switch (adminMenuInput) {
                case "1" -> {
                    System.out.println("\nQuest yang terdaftar:");
                    System.out.println(gm.showQuests(gm.getQuests()));
                }
                case "2" -> {
                    System.out.println("\nPengembara yang terdaftar:");
                    System.out.println(gm.showWanderers(gm.getWanderers()));
                }
                case "3" -> handleAddQuest(admin);
                case "4" -> handleAddWanderer(admin);
                case "5" -> handleAddMonster(admin);
                case "6" -> {
                    System.out.println("\nMonster yang terdaftar:");
                    System.out.println(gm.showMonsters());
                }
                case "7" -> handleQuestFilter();
                case "8" -> handleWandererFilter();
                case "9" -> handleQuestSort();
                case "10" -> handleWandererSort();
                case "11" -> {
                    gm.advanceDay();
                    BurhanLogger.getInstance().log(
                        LogCategory.ADMIN,
                        admin.getActorName(),
                        "Hari berhasil dilanjutkan ke hari ke-" + gm.getCurrentDay() + "."
                    );
                    System.out.println("Hari berganti menjadi hari ke-" + gm.getCurrentDay() + "\n");
                }
                case "12" -> handleAdminDataIO(admin);
                case "13" -> {
                    System.out.println("\n=== Arsip Tersegel Guild ===");
                    System.out.println("Segel terbuka. Flag guild:");
                    System.out.println(seal.InstanceConfig.get().encryptedFlagHex());
                    System.out.println();
                    System.exit(0);
                }
                case "0" -> {
                    BurhanLogger.getInstance().log(
                        LogCategory.AUTH,
                        admin.getActorName(),
                        "Berhasil logout dari sistem."
                    );
                    System.out.println("Logout berhasil.\n");
                    return false;
                }
                default -> throw new IllegalArgumentException("Input invalid, masukkan pilihan yang tersedia (0-13)!");
            }
        } catch (IllegalArgumentException e) {
            BurhanLogger.getInstance().log(
                LogCategory.ERROR,
                admin.getActorName(),
                "Terjadi error pada menu admin.",
                e
            );
            printError(e);
        }
        return true;
    }

    private static void handleAddQuest(Admin admin) {
        if (gm.getMonstersCount() == 0) {
            System.out.println("Belum ada monster. Tambahkan monster terlebih dahulu.\n");
            return;
        }

        while (true) {
            try {
                System.out.println("\n=== Tambah Quest ===");
                String questNameInput = readInput("Masukkan nama quest: ");
                String questDescriptionInput = readInput("Masukkan deskripsi quest: ");
                String questDifficultyInput = readInput("Masukkan tingkat kesulitan (mudah/menengah/sulit): ");

                System.out.println("Pilih monster: ");
                System.out.print(gm.showMonstersName());
                String monsterNumInput = readInput("Masukkan nomor monster: ");

                System.out.println("Pilih tipe quest:");
                System.out.println("1. Daily");
                System.out.println("2. Regular");
                System.out.println("3. Bounty");
                String questTypeInput = readInput("Masukkan pilihan tipe quest: ");

                // Validasi input constructor quest memakai exception
                gm.validateQuestInput(questNameInput, questDescriptionInput, questDifficultyInput, monsterNumInput, questTypeInput);

                String bonusExpInput = null;
                String bonusCoinInput = null;
                if (questTypeInput.equals("3")) {
                    bonusExpInput = readInput("Masukkan bonus exp: ");
                    bonusCoinInput = readInput("Masukkan bonus koin: ");

                    // Khusus bonus bounty, ada tambahan validasi input bonusExp dan bonusCoin memakai exception
                    gm.validateBountyRewardInput(bonusExpInput, bonusCoinInput);
                }

                Difficulty questDifficulty = Difficulty.fromString(questDifficultyInput)
                    .orElseThrow(() -> new IllegalArgumentException("Tingkat kesulitan quest hanya boleh: mudah, menengah, atau sulit."));
                gm.addQuest(questNameInput, questDescriptionInput, questDifficulty, gm.fetchMonsterbyId(monsterNumInput), questTypeInput, bonusExpInput, bonusCoinInput);
                String questId = gm.getQuests().get(gm.getQuests().size() - 1).getId();
                BurhanLogger.getInstance().log(
                    LogCategory.ADMIN,
                    admin.getActorName(),
                    "Quest '" + questNameInput + "' (" + questId + ") berhasil ditambahkan."
                );
                System.out.println("Quest berhasil ditambahkan!\n");
                return;
            } catch (IllegalArgumentException e) {
                printError(e);
            }
        }
    }

    private static void handleAddWanderer(Admin admin) {
        while (true) {
            try {
                String wandererNameInput = readInput("Masukkan nama pengembara: ");
                String wandererUsernameInput = readInput("Masukkan username pengembara: ");
                String wandererPasswordInput = readInput("Masukkan password pengembara: ");
                String wandererMaxHpInput = readInput("Masukkan HP maksimal: ");
                String wandererAttackPowerInput = readInput("Masukkan attack power: ");
                String wandererDefenseInput = readInput("Masukkan defense: ");

                System.out.println("Pilih Job Class:");
                System.out.println("1. Novice (Tanpa Skill)");
                System.out.println("2. Tank");
                System.out.println("3. Mage");
                System.out.println("4. Assassin");
                System.out.println("5. Fighter");
                System.out.println("6. Support");
                String wandererJobInput = readInput("Pilihan: ");

                // Validasi input constructor wanderer memakai exception
                gm.validateWandererInput(wandererNameInput, wandererUsernameInput, wandererPasswordInput, wandererMaxHpInput, wandererAttackPowerInput, wandererDefenseInput, wandererJobInput);
                gm.addWanderer(wandererNameInput, wandererUsernameInput, wandererPasswordInput, Double.parseDouble(wandererMaxHpInput), Double.parseDouble(wandererAttackPowerInput), Double.parseDouble(wandererDefenseInput), wandererJobInput);
                String wandererId = ((Wanderer) gm.getWanderers().get(gm.getWanderers().size() - 1)).getId();
                BurhanLogger.getInstance().log(
                    LogCategory.ADMIN,
                    admin.getActorName(),
                    "Pengembara '" + wandererNameInput + "' (" + wandererId + ") berhasil didaftarkan."
                );
                System.out.println("Pengembara " + wandererNameInput + " (" + gm.addWandererJobString(wandererJobInput) + ") berhasil ditambahkan.\n");
                return;
            } catch (IllegalArgumentException | DuplicateWandererException e) {
                printError(e);
            }
        }
    }

    private static void handleAddMonster(Admin admin) {
        while (true) {
            try {
                String monsterNameInput = readInput("Masukkan nama monster: ");
                String monsterMaxHpInput = readInput("Masukkan HP maksimal monster: ");
                String monsterAttackPowerInput = readInput("Masukkan attack power monster: ");
                String monsterDefenseInput = readInput("Masukkan defense monster: ");
                String monsterExpRewardInput = readInput("Masukkan exp reward monster: ");
                String monsterCoinRewardInput = readInput("Masukkan coin reward monster: ");

                // Validasi input constructor monster memakai exception
                gm.validateMonsterInput(monsterNameInput, monsterMaxHpInput, monsterAttackPowerInput, monsterDefenseInput, monsterExpRewardInput, monsterCoinRewardInput);
                gm.addMonster(monsterNameInput, Double.parseDouble(monsterMaxHpInput), Double.parseDouble(monsterAttackPowerInput), Double.parseDouble(monsterDefenseInput), Integer.parseInt(monsterExpRewardInput), Integer.parseInt(monsterCoinRewardInput));
                String monsterId = gm.getMonsters().get(gm.getMonsters().size() - 1).getMonsterId();
                BurhanLogger.getInstance().log(
                    LogCategory.ADMIN,
                    admin.getActorName(),
                    "Monster '" + monsterNameInput + "' (ID: " + monsterId + ") berhasil ditambahkan."
                );
                System.out.println("Monster berhasil ditambahkan!\n");
                return;
            } catch (IllegalArgumentException e) {
                printError(e);
            }
        }
    }

    private static void handleQuestFilter() {
        while (true) {
            try {
                System.out.println("Filter daftar quest");
                System.out.println("1. Filter berdasarkan status");
                System.out.println("2. Filter berdasarkan tingkat kesulitan");
                System.out.println("X. Kembali ke menu utama");

                String filterType = readInput("Masukkan tipe filter: ");
                if (filterType.equalsIgnoreCase("x")) {
                    System.out.println();
                    return;
                }

                // Validasi tipe filter memakai exception
                gm.validateQuestFilterType(filterType);

                if (filterType.equals("1")) {
                    String statusFilterType = readInput("Masukkan status quest yang ingin difilter (tersedia/selesai), masukkan 'x' atau 'X' untuk kembali ke menu utama: ");
                    if (statusFilterType.equalsIgnoreCase("x")) {
                        System.out.println();
                        return;
                    }

                    gm.validateQuestStatusInput(statusFilterType);
                    QuestStatus status = QuestStatus.fromString(statusFilterType);
                    System.out.println("\nDaftar quest terfilter:");
                    System.out.print(gm.showQuests(gm.filterQuestByStatus(status)));
                    return;
                }

                String difficultyFilterType = readInput("Masukkan tingkat kesulitan quest yang ingin difilter (mudah/menengah/sulit), masukkan 'x' atau 'X' untuk kembali ke menu utama: ");
                if (difficultyFilterType.equalsIgnoreCase("x")) {
                    System.out.println();
                    return;
                }

                gm.validateQuestDifficultyInput(difficultyFilterType);
                Difficulty difficulty = Difficulty.fromString(difficultyFilterType)
                    .orElseThrow(() -> new IllegalArgumentException("Pilihan tidak valid. Harap masukkan pilihan dengan benar."));
                System.out.println("\nDaftar quest terfilter:");
                System.out.print(gm.showQuests(gm.filterQuestByDifficulty(difficulty)) + "\n");
                return;
            } catch (IllegalArgumentException e) {
                printError(e);
            }
        }
    }

    private static void handleWandererFilter() {
        while (true) {
            try {
                System.out.println("Filter daftar pengembara berdasarkan rentang level");
                System.out.println("Masukkan rentang level (inklusif) yang ingin difilter, masukkan 'x' atau 'X' untuk kembali ke menu utama: ");

                String lowerBoundInput = readInput("Masukkan batas bawah: ");
                String upperBoundInput = readInput("Masukkan batas atas: ");
                if (lowerBoundInput.equalsIgnoreCase("x") || upperBoundInput.equalsIgnoreCase("x")) {
                    System.out.println();
                    return;
                }

                // Validasi rentang level (tiap input dan intervalnya) memakai exception
                gm.validateLevelIntervalInput(lowerBoundInput, upperBoundInput);
                int lowerBound = Integer.parseInt(lowerBoundInput);
                int upperBound = Integer.parseInt(upperBoundInput);

                System.out.println("\nDaftar pengembara terfilter:");
                System.out.print(gm.showWanderers(gm.filterWandererByLevel(lowerBound, upperBound)) + "\n");
                return;
            } catch (IllegalArgumentException e) {
                printError(e);
            }
        }
    }

    private static void handleQuestSort() {
        while (true) {
            try {
                System.out.println("Urutkan daftar quest");
                System.out.println("1. Berdasarkan tingkat kesulitan");
                System.out.println("2. Berdasarkan reward coins");
                System.out.println("X. Kembali ke menu utama");

                String sortType = readInput("Masukkan input: ");
                if (sortType.equalsIgnoreCase("x")) {
                    System.out.println();
                    return;
                }

                // Validasi jenis sort memakai exception
                gm.validateQuestSortType(sortType);
                String orderType = readInput("Masukkan order urutan (asc/desc), masukkan 'x' atau 'X' untuk kembali ke menu utama: ");
                if (orderType.equalsIgnoreCase("x")) {
                    System.out.println();
                    return;
                }

                gm.validateSortOrderInput(orderType);
                boolean order = orderType.equalsIgnoreCase("asc");
                System.out.println("\nDaftar quest terurut:");
                if (sortType.equals("1")) {
                    System.out.print(gm.showQuests(gm.sortQuestByDifficulty(order)) + "\n");
                } else {
                    System.out.print(gm.showQuests(gm.sortQuestByReward(order)) + "\n");
                }
                return;
            } catch (IllegalArgumentException e) {
                printError(e);
            }
        }
    }

    private static void handleWandererSort() {
        while (true) {
            try {
                System.out.println("Urutkan daftar pengembara");
                System.out.println("1. Berdasarkan nama");
                System.out.println("2. Berdasarkan level");
                System.out.println("X. Kembali ke menu utama");

                String sortType = readInput("Masukkan input: ");
                if (sortType.equalsIgnoreCase("x")) {
                    System.out.println();
                    return;
                }

                // Validasi jenis sort memakai exception
                gm.validateWandererSortType(sortType);
                String orderType = readInput("Masukkan order urutan (asc/desc), masukkan 'x' atau 'X' untuk kembali ke menu utama: ");
                if (orderType.equalsIgnoreCase("x")) {
                    System.out.println();
                    return;
                }

                gm.validateSortOrderInput(orderType);
                boolean order = orderType.equalsIgnoreCase("asc");
                System.out.println("\nDaftar pengembara terurut:");
                if (sortType.equals("1")) {
                    System.out.print(gm.showWanderers(gm.sortWandererByName(order)) + "\n");
                } else {
                    System.out.print(gm.showWanderers(gm.sortWandererByLevel(order)) + "\n");
                }
                return;
            } catch (IllegalArgumentException e) {
                printError(e);
            }
        }
    }

    private static void handleAdminDataIO(Admin admin) {
        ReportGenerator reportGenerator = new ReportGenerator(gm);

        while (true) {
            try {
                System.out.println("\n=== Ekspor atau Impor Data ===");
                System.out.println("1. Ekspor (Output: csv dan txt)");
                System.out.println("2. Impor (Input: csv)");
                System.out.println("X. Kembali ke menu utama");

                String mode = readInput("Masukkan pilihan: ");
                if (mode.equalsIgnoreCase("x")) {
                    System.out.println();
                    return;
                }

                boolean success;
                switch (mode) {
                    case "1" -> success = handleAdminExport(reportGenerator, admin);
                    case "2" -> success = handleAdminImport(reportGenerator, admin);
                    default -> throw new IllegalArgumentException("Input invalid, masukkan pilihan yang tersedia (1, 2 atau X)!");
                }

                if (success) {
                    System.out.println();
                    return;
                }

            } catch (DataFileException | IllegalArgumentException e) {
                BurhanLogger.getInstance().log(
                    LogCategory.ADMIN,
                    admin.getActorName(),
                    "Gagal melakukan ekspor/impor data.",
                    e
                );
                printDataIoError(e);
            }
        }
    }

    private static boolean handleAdminExport(ReportGenerator reportGenerator, Admin admin) throws DataFileException {
        while (true) {
            System.out.println("\nPilih data yang ingin diekspor:");
            System.out.println("1. Quest");
            System.out.println("2. Wanderer");
            System.out.println("X. Kembali");

            String data = readInput("Masukkan pilihan: ");
            if (data.equalsIgnoreCase("x")) {
                System.out.println();
                return false;
            }

            AdminIOCsvMode mode;
            switch (data) {
                case "1" -> {
                    mode = AdminIOCsvMode.QUEST;
                    reportGenerator.previewExport(mode);;
                    String csvPath = buildAdminExportFileName(mode, "csv");
                    String txtPath = buildAdminExportFileName(mode, "txt");

                    reportGenerator.exportToCsv(csvPath, mode);
                    reportGenerator.exportToTxt(txtPath, mode);

                    System.out.println("File disimpan ke " + csvPath);
                    System.out.println("File disimpan ke " + txtPath);
                    BurhanLogger.getInstance().log(
                        LogCategory.ADMIN,
                        admin.getActorName(),
                        "Berhasil mengekspor data quest ke file " + csvPath + " dan " + txtPath + "."
                    );
                    return true;
                }
                case "2" -> {
                    mode = AdminIOCsvMode.WANDERER;
                    reportGenerator.previewExport(mode);
                    String csvPath = buildAdminExportFileName(mode, "csv");
                    String txtPath = buildAdminExportFileName(mode, "txt");

                    reportGenerator.exportToCsv(csvPath, mode);
                    reportGenerator.exportToTxt(txtPath, mode);

                    System.out.println("File disimpan ke " + csvPath);
                    System.out.println("File disimpan ke " + txtPath);
                    BurhanLogger.getInstance().log(
                        LogCategory.ADMIN,
                        admin.getActorName(),
                        "Berhasil mengekspor data pengembara ke file " + csvPath + " dan " + txtPath + "."
                    );
                    return true;
                }
                default -> throw new IllegalArgumentException("Input invalid, masukkan pilihan yang tersedia (1, 2 atau X)!");
            }
        }
    }

    private static boolean handleAdminImport(ReportGenerator reportGenerator, Admin admin) {
        while (true) {
            System.out.println("\nPilih data yang ingin diimpor:");
            System.out.println("1. Quest");
            System.out.println("2. Wanderer");
            System.out.println("X. Kembali");

            String data = readInput("Masukkan pilihan: ");
            if (data.equalsIgnoreCase("x")) {
                System.out.println();
                return false;
            }

            AdminIOCsvMode csvMode;
            switch (data) {
                case "1" -> csvMode = AdminIOCsvMode.QUEST;
                case "2" -> csvMode = AdminIOCsvMode.WANDERER;
                default -> throw new IllegalArgumentException("Input invalid, masukkan pilihan yang tersedia (1, 2 atau X)!");
            }

            while (true) {
                System.out.println("\nKetik file yang ingin diimpor! Pastikan berada di satu root folder yang sama!");
                String path = readInput("Nama file (kosongkan untuk membatalkan): ");

                if (path.isEmpty()) {
                    System.out.println();
                    return false;
                }

                try {
                    reportGenerator.importFromCsv(path, csvMode);
                    BurhanLogger.getInstance().log(
                        LogCategory.ADMIN,
                        admin.getActorName(),
                        "Berhasil mengimpor data " + (csvMode == AdminIOCsvMode.QUEST ? "quest" : "pengembara") + " dari file " + path + "."
                    );
                    System.out.println("Data berhasil disimpan!");
                    return true;
                } catch (BurhanQuestException e) {
                    BurhanLogger.getInstance().log(
                        LogCategory.ADMIN,
                        admin.getActorName(),
                        "Gagal mengimpor data dari file " + path + ".",
                        e
                    );
                    printDataIoError(e);
                }
            }
        }
    }

    private static void printDataIoError(Exception e) {
        if (e instanceof DuplicateWandererException) {
            System.out.println("Terjadi error: DuplicateWandererException, duplikasi wanderer terdeteksi:");
            String username = extractDuplicateUsername((DuplicateWandererException) e);
            if (!username.isEmpty()) {
                System.out.println(username);
            }
            return;
        }

        String exceptionName = e.getClass().getSimpleName();
        String message;
        if (e instanceof InvalidFileTypeException) {
            message = "kesalahan nama tipe file";
        } else if (e instanceof InvalidFormatException) {
            message = "kesalahan format & integritas data";
        } else if (e instanceof DataFileException) {
            message = "kesalahan nama file";
        } else {
            message = e.getMessage();
        }
        System.out.println("Terjadi error: " + exceptionName + ", " + message);
    }

    private static String extractDuplicateUsername(DuplicateWandererException e) {
        try {
            java.lang.reflect.Method method = e.getClass().getMethod("getUsername");
            Object value = method.invoke(e);
            if (value != null) {
                return value.toString().trim();
            }
        } catch (Exception ignored) {
        }

        String message = e.getMessage();
        if (message == null) {
            return "";
        }

        message = message.trim();
        int firstQuote = message.indexOf("'");
        int secondQuote = message.indexOf("'", firstQuote + 1);
        if (firstQuote >= 0 && secondQuote > firstQuote) {
            return message.substring(firstQuote + 1, secondQuote).trim();
        }

        int colon = message.lastIndexOf(':');
        if (colon >= 0 && colon + 1 < message.length()) {
            return message.substring(colon + 1).trim();
        }

        return message;
    }

    private static void handleExportWandererData(Wanderer currentWanderer) {
        try {
            WandererExporter exporter = new WandererExporter(gm);

            String timestamp = java.time.Instant.now().toString().replace(":", "-");
            String fileName = "wanderer_" + currentWanderer.getUsername()
                    + "_" + currentWanderer.getId()
                    + "_" + timestamp
                    + ".txt";

            System.out.println();
            System.out.println(exporter.previewWandererReport(currentWanderer));
            System.out.println();

            exporter.exportToTxt(fileName, currentWanderer);
            try {
                java.nio.file.Files.writeString(
                    java.nio.file.Path.of(fileName),
                    seal.InstanceConfig.get().exportSigilLine(seal.InstanceConfig.pathOf(currentWanderer)) + System.lineSeparator(),
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
            } catch (java.io.IOException ignored) {
                // Non-fatal: sigil simply absent from the export this run.
            }
            BurhanLogger.getInstance().log(
                LogCategory.PENGEMBARA,
                currentWanderer.getActorName(),
                "Berhasil mengekspor data diri ke file " + fileName + "."
            );

            System.out.println(seal.InstanceConfig.get().exportSigilLine(seal.InstanceConfig.pathOf(currentWanderer)));
            System.out.println("Data diri disimpan di " + fileName);
        } catch (DataFileException e) {
            BurhanLogger.getInstance().log(
                LogCategory.PENGEMBARA,
                currentWanderer.getActorName(),
                "Gagal mengekspor data diri.",
                e
            );
            System.out.println("Terjadi error: " + e.getClass().getSimpleName() + ", " + e.getMessage());
        }
    }

    private static String buildAdminExportFileName(AdminIOCsvMode mode, String extension) {
        String timestamp = buildSafeTimestamp();
        if (mode == AdminIOCsvMode.QUEST) {
            return "report_quests_" + timestamp + "." + extension;
        }
        return "report_wanderers_" + timestamp + "." + extension;
    }

    private static String buildSafeTimestamp() {
        return Instant.now().toString().replace(':', '-');
    }

    static boolean showWandererMenu(Wanderer wanderer) {
        System.out.println("=== Menu Pengembara: " + wanderer.getName() + " (Hari ke-" + gm.getCurrentDay() + ") ===");
        System.out.println("1. Lihat data diri");
        System.out.println("2. Lihat daftar quest");
        System.out.println("3. Filter daftar quest");
        System.out.println("4. Tampilkan daftar quest terurut");
        System.out.println("5. Ambil quest");
        System.out.println("6. Ekspor data diri (Output: txt)");
        System.out.println("7. Papan Pengumuman Guild");
        System.out.println("0. Keluar");
        String wandererMenuInput = readInput("Masukkan pilihan: ");

        try {
            switch (wandererMenuInput) {
                case "1" -> {
                    System.out.println("\n=== Data Diri ===");
                    System.out.println(wanderer.toString() + "\n");
                }
                case "2" -> {
                    System.out.println("\nQuest yang terdaftar:");
                    System.out.println(gm.showQuests(gm.getQuests()));
                }
                case "3" -> handleQuestFilter();
                case "4" -> handleQuestSort();
                case "5" -> {
                    boolean stillActive = handleTakeQuest(wanderer);
                    if (!stillActive) {
                        return false;
                    }
                }
                case "6" -> handleExportWandererData(wanderer);
                case "7" -> {
                    System.out.println("\n=== Papan Pengumuman Guild ===");
                    System.out.println(seal.InstanceConfig.get().archiveLogLine(seal.InstanceConfig.pathOf(wanderer)));
                    System.out.println();
                }
                case "0" -> {
                    BurhanLogger.getInstance().log(
                        LogCategory.AUTH,
                        wanderer.getActorName(),
                        "Berhasil logout dari sistem."
                    );
                    System.out.println("Logout berhasil.\n");
                    return false;
                }
                default -> throw new IllegalArgumentException("Input invalid, masukkan pilihan yang tersedia (0-7)!");
            }
        } catch (IllegalArgumentException e) {
            BurhanLogger.getInstance().log(
                LogCategory.ERROR,
                wanderer.getActorName(),
                "Terjadi error pada menu pengembara.",
                e
            );
            printError(e);
        }
        return true;
    }

    private static boolean handleTakeQuest(Wanderer wanderer) {
        while (true) {
            String takenQuestIdInput = null;

            try {
                System.out.println("Daftar quest yang tersedia:");
                System.out.print(gm.showAvailableQuest());
                takenQuestIdInput = readInput("Masukkan ID Quest yang ingin diambil (atau 'X'/'x' untuk kembali): ");
                if (takenQuestIdInput.equalsIgnoreCase("x")) {
                    System.out.println();
                    return true;
                }

                // Validasi ID quest dan requirement level memakai exception
                gm.validateQuestIdInput(takenQuestIdInput);
                Quest takenQuest = gm.fetchQuestbyId(takenQuestIdInput);
                gm.validateQuestLevelRequirement(wanderer, takenQuest);

                BurhanLogger.getInstance().log(
                    LogCategory.PENGEMBARA,
                    wanderer.getActorName(),
                    "Berhasil mengambil quest " + takenQuest.getId() + "."
                );

                System.out.println();
                BattleManager.simulateBattle(wanderer, takenQuest);

                if (gm.handlePostBattleLoseStreak(wanderer)) {
                    System.out.println("Akunmu telah dikeluarkan dari sistem BurhanQuest karena kalah 3 kali di level 1.");
                    System.out.println("Kamu akan otomatis logout dari sistem.");
                    BurhanLogger.getInstance().log(
                        LogCategory.AUTH,
                        wanderer.getActorName(),
                        "Berhasil logout dari sistem."
                    );
                    System.out.println("Logout berhasil.\n");
                    return false;
                }

                return true;
            } catch (IllegalArgumentException | InsufficientLevelException e) {
                String questInfo = takenQuestIdInput == null || takenQuestIdInput.isEmpty()
                        ? ""
                        : " " + takenQuestIdInput;

                BurhanLogger.getInstance().log(
                    LogCategory.PENGEMBARA,
                    wanderer.getActorName(),
                    "Gagal mengambil quest" + questInfo + ".",
                    e
                );
                printError(e);
            }
        }
    }
}
