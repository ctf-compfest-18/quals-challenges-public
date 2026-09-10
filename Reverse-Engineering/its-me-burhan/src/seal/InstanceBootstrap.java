package seal;

import entities.Admin;
import entities.User;
import entities.Wanderer;
import entities.monster.Monster;

import exception.DuplicateWandererException;

import quests.enums.Difficulty;

import services.AuthService;
import services.BattleManager;
import services.GameManager;

import utils.BurhanLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;

public final class InstanceBootstrap {

    private InstanceBootstrap() {
    }

    public static void seed(GameManager gm) {
        InstanceConfig cfg = InstanceConfig.get();

        seedRandomness(cfg);
        seedMonsters(gm, cfg);
        seedQuests(gm, cfg);
        seedWanderer(gm, cfg);
        installSeededAdmin(gm, cfg);
        writeArchiveToken(cfg);
    }

    private static void seedRandomness(InstanceConfig cfg) {
        long battleSeed = new SeedRng(cfg.seed()).digestLong("battle-rng");
        BattleManager.seedRandomness(battleSeed);
    }

    private static void seedMonsters(GameManager gm, InstanceConfig cfg) {
        for (InstanceConfig.MonsterSpec m : cfg.monsters()) {
            gm.addMonster(m.name(), m.hp(), m.atk(), m.def(), m.exp(), m.coin());
        }
    }

    private static void seedQuests(GameManager gm, InstanceConfig cfg) {
        ArrayList<Monster> monsters = gm.getMonsters();
        for (InstanceConfig.QuestSpec q : cfg.quests()) {
            Difficulty difficulty = Difficulty.fromString(q.difficulty())
                .orElseThrow(() -> new IllegalStateException("Difficulty seed tidak valid: " + q.difficulty()));
            Monster monster = monsters.get(q.monsterIndex());
            gm.addQuest(q.name(), "Quest tersegel milik guild.", difficulty, monster, q.type(), "0", "0");
        }
    }

    private static void seedWanderer(GameManager gm, InstanceConfig cfg) {
        try {
            gm.addWanderer("Frieren", "frieren", "frieren",
                    cfg.wandererMaxHp(), cfg.wandererAttack(), cfg.wandererDefense(), "1");
        } catch (DuplicateWandererException e) {
            return;
        }

        ArrayList<User> wanderers = gm.getWanderers();
        Wanderer wanderer = (Wanderer) wanderers.get(wanderers.size() - 1);
        wanderer.seedProgress(cfg.level(), cfg.coins());
    }

    private static void installSeededAdmin(GameManager gm, InstanceConfig cfg) {
        AuthService auth = gm.getAuthService();
        for (User user : new ArrayList<>(auth.getUsers())) {
            if (auth.isAdmin(user)) {
                auth.removeUser(user);
            }
        }
        auth.addUser(new SealedAdmin());
    }

    private static void writeArchiveToken(InstanceConfig cfg) {
        Path logPath = Path.of(BurhanLogger.getInstance().getFilePath());
        try {
            Files.writeString(
                logPath,
                cfg.archiveLogLine("") + System.lineSeparator(),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            );
        } catch (IOException e) {
            System.err.println("Peringatan: gagal menulis sigil arsip ke log.");
        }
    }
}
