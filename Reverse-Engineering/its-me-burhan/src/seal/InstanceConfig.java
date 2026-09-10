package seal;

import entities.Wanderer;
import quests.Quest;

import java.util.stream.Collectors;

public final class InstanceConfig {

    public record MonsterSpec(String name, double hp, double atk, double def, int exp, int coin) {}
    public record QuestSpec(String name, String difficulty, String type, int monsterIndex) {}

    private static InstanceConfig instance;

    private static String dec(int... x) {
        char[] c = new char[x.length];
        for (int i = 0; i < x.length; i++) {
            c[i] = (char) (x[i] ^ 0x5A);
        }
        return new String(c);
    }

    public static final int QUEST_COUNT = 18;
    private static final int PATH_LEN = 3;

    private static final int TRANSFORM_COUNT = 7;
    private static final int CHAIN_LEN = 7;

    private static final long PATH_SPACE = 18L * 17L * 16L;
    private static final long PERM_SPACE = 5040L;
    private static final long ITEM_SPACE = 362880L;

    private static final String[] MON_NAMES = {
        "Goblin Hutan", "Golem Batu", "Naga Rawa", "Iblis Salju", "Penjaga Tersegel",
        "Serigala Kelabu", "Kelelawar Gua", "Laba-laba Racun", "Ksatria Berkarat",
        "Elemental Api", "Hantu Reruntuhan", "Ular Berbisa", "Beruang Gunung",
        "Harpy Angin", "Troll Jembatan", "Lich Beku", "Chimera Padang", "Kraken Danau"
    };
    private static final String[] QST_NAMES = {
        "Pembersihan Hutan", "Reruntuhan Batu", "Teror Rawa", "Badai Beku", "Segel Kuno",
        "Perburuan Kelabu", "Ekspedisi Gua", "Sarang Racun", "Besi Berkarat",
        "Kobaran Abadi", "Bisikan Puing", "Jalur Berbisa", "Puncak Gunung",
        "Tebing Berangin", "Jembatan Tua", "Ritual Beku", "Padang Terlarang", "Kedalaman Danau"
    };

    private final SeedRng rng;
    private final String seed;
    private final String flag;

    private final int coins;
    private final int level;

    private final double wandererMaxHp;
    private final double wandererAttack;
    private final double wandererDefense;

    private final MonsterSpec[] monsters;
    private final QuestSpec[] quests;

    private final long pathIndex;
    private final int[] questOrder;
    private final String path1;
    private final String path2;
    private final String path3;

    private final int coinsFinal;
    private final long permIndex;
    private final int[] perm;
    private final long itemIndex;
    private final int[] itemOrder;
    private final int rot;
    private final int[] target;

    private InstanceConfig() {
        String envSeed = System.getenv(dec(24,15,8,18,27,20,5,9,31,31,30));
        String envFlag = System.getenv(dec(24,15,8,18,27,20,5,28,22,27,29));

        if (envSeed == null || envSeed.isEmpty()) {
            System.err.println("WARN: seed not set; using dev default. Team instances MUST set it.");
            envSeed = "DEV-SEED";
        }
        if (envFlag == null || envFlag.isEmpty()) {
            envFlag = "BURHAN{flag_not_set_export_BURHAN_FLAG_to_override}";
        }
        this.seed = envSeed;
        this.flag = envFlag;
        this.rng = new SeedRng(this.seed);

        this.coins = rng.bounded("coins", 1000, 9999);
        this.level = rng.bounded("level", 8, 16);

        this.wandererMaxHp = rng.bounded("whp", 9000, 9999);
        this.wandererAttack = rng.bounded("watk", 900, 999);
        this.wandererDefense = rng.bounded("wdef", 400, 500);

        this.monsters = new MonsterSpec[QUEST_COUNT];
        for (int i = 0; i < QUEST_COUNT; i++) {
            monsters[i] = new MonsterSpec(
                MON_NAMES[i],
                rng.bounded("mhp" + i, 220, 620),
                rng.bounded("matk" + i, 25, 70),
                rng.bounded("mdef" + i, 5, 30),
                rng.bounded("mexp" + i, 1000, 9000),
                rng.bounded("mcoin" + i, 120, 980));
        }

        this.quests = new QuestSpec[QUEST_COUNT];
        for (int i = 0; i < QUEST_COUNT; i++) {
            String difficulty = (i % 3 == 0) ? "mudah" : "menengah";
            quests[i] = new QuestSpec(QST_NAMES[i], difficulty, "2", i);
        }

        this.pathIndex = SeedRng.beLong(
            SeedRng.sha256(SeedRng.concat(SeedRng.be4(level), SeedRng.be4(coins)))) % PATH_SPACE;
        this.questOrder = SeedRng.kthPermutation(pathIndex, QUEST_COUNT, PATH_LEN);
        String q0 = "Q" + (questOrder[0] + 1);
        String q1 = "Q" + (questOrder[1] + 1);
        String q2 = "Q" + (questOrder[2] + 1);
        this.path1 = q0;
        this.path2 = q0 + ">" + q1;
        this.path3 = q0 + ">" + q1 + ">" + q2;

        int sum = this.coins;
        for (int idx : questOrder) {
            sum += monsters[idx].coin();
        }
        this.coinsFinal = sum;

        byte[] joined = new byte[0];
        for (byte[] item : items()) {
            joined = SeedRng.concat(joined, item);
        }

        this.permIndex = SeedRng.beLong(SeedRng.sha256(joined)) % PERM_SPACE;
        this.perm = SeedRng.kthPermutation(permIndex, TRANSFORM_COUNT, CHAIN_LEN);

        this.itemIndex = SeedRng.beLong(
            SeedRng.sha256(SeedRng.concat(SeedRng.be4(coins), SeedRng.be4(level)))) % ITEM_SPACE;
        this.itemOrder = SeedRng.kthPermutation(itemIndex, 9, 9);

        this.rot = (int) (permIndex % 32);

        this.target = deriveTarget();
    }

    private byte[][] items() {
        return new byte[][] {
            SeedRng.be4(level),
            SeedRng.be4(coins),
            SeedRng.be4(battleSigil(path1)),
            SeedRng.hexToBytes(archiveSigil(path1)),
            SeedRng.be4(battleSigil(path2)),
            SeedRng.hexToBytes(exportSigil(path2)),
            SeedRng.be4(battleSigil(path3)),
            SeedRng.hexToBytes(archiveSigil(path3)),
            SeedRng.be4(coinsFinal)
        };
    }

    // target = 16 five-bit groups of a SHA-256 chain over the itemOrder-shuffled
    // items. No per-item transform: SHA already makes this one-way, so the old
    // applyTransform(perm[i], item) layer was replay busywork, not security.
    private int[] deriveTarget() {
        byte[][] raw = items();
        byte[][] ordered = new byte[raw.length][];
        for (int i = 0; i < raw.length; i++) {
            ordered[i] = raw[itemOrder[i]];
        }
        byte[] h = SeedRng.sha256(ordered[0]);
        for (int i = 1; i < ordered.length; i++) {
            h = SeedRng.chain(h, ordered[i]);
        }
        return SeedRng.fiveBitGroups(h, 16);
    }

    public boolean checkAdminPassword(String submitted) {
        if (submitted == null || submitted.length() != 16) {
            return false;
        }
        String alpha = SeedRng.alphabetFor(rot);
        int[] idx = new int[16];
        for (int i = 0; i < 16; i++) {
            int k = alpha.indexOf(submitted.charAt(i));
            if (k < 0) {
                return false;
            }
            idx[i] = k;
        }
        int[] g = idx;
        for (int i = 0; i < perm.length; i++) {
            g = SeedRng.applyTransform32(perm[i], g);
        }
        return java.util.Arrays.equals(g, target);
    }

    public byte[] encryptedFlag() {
        byte[] h = flag.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        for (int i = 0; i < perm.length; i++) {
            h = SeedRng.applyTransform(perm[i], h);
        }
        return h;
    }

    public String encryptedFlagHex() {
        byte[] c = encryptedFlag();
        StringBuilder sb = new StringBuilder(c.length * 2);
        for (byte x : c) {
            sb.append(String.format("%02x", x & 0xff));
        }
        return sb.toString();
    }

    public static synchronized InstanceConfig get() {
        if (instance == null) {
            instance = new InstanceConfig();
        }
        return instance;
    }

    public static String pathOf(Wanderer wanderer) {
        if (wanderer == null) {
            return "";
        }
        return wanderer.getCompletedQuestHistory().stream()
            .map(Quest::getId)
            .collect(Collectors.joining(">"));
    }

    public int battleSigil(String path) { return rng.bounded("battle:" + path, 10000, 99999); }
    public String archiveSigil(String path) { return rng.hexToken("archive:" + path, 6); }
    public String exportSigil(String path) { return rng.hexToken("export:" + path, 6); }

    public String battleSigilLine(String path) { return "sigil-pertempuran [" + path + "]: " + battleSigil(path); }
    public String archiveLogLine(String path) { return "sigil-arsip: " + archiveSigil(path); }
    public String exportSigilLine(String path) { return "sigil-ekspor: " + exportSigil(path); }

    public String flag() { return flag; }
    public String seed() { return seed; }
    public int coins() { return coins; }
    public int level() { return level; }
    public int coinsFinal() { return coinsFinal; }
    public double wandererMaxHp() { return wandererMaxHp; }
    public double wandererAttack() { return wandererAttack; }
    public double wandererDefense() { return wandererDefense; }
    public MonsterSpec[] monsters() { return monsters; }
    public QuestSpec[] quests() { return quests; }
    public String requiredPath() { return path3; }
}