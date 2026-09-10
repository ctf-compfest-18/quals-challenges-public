package services;

import entities.Wanderer;
import entities.monster.Monster;

import quests.Quest;
import quests.enums.Difficulty;

import utils.BurhanLogger;
import utils.enums.LogCategory;

import java.util.Scanner;

public class BattleManager {
    private static final Scanner scanner = new Scanner(System.in);

    // Seedable source so role-skill randomness (and anything else) is reproducible
    // for a given instance seed. Installed at boot by InstanceBootstrap.
    private static java.util.Random battleRandom = new java.util.Random();

    public static void seedRandomness(long seed) {
        battleRandom = new java.util.Random(seed);
    }

    public static java.util.Random battleRandom() {
        return battleRandom;
    }

    public static double[] getMultiplier(Difficulty difficulty){
        double[] multiplier = {0,0};

        if(difficulty == Difficulty.MUDAH){
            multiplier[0] = 1.25;
            multiplier[1] = 0.75;
        }else if(difficulty == Difficulty.MENENGAH){
            multiplier[0] = 1.00;
            multiplier[1] = 1.00;
        }else{
            multiplier[0] = 0.75;
            multiplier[1] = 1.25;
        }

        return multiplier;
    }

    public static void simulateBattle(Wanderer wanderer, Quest quest) {
        double wandererHp = wanderer.getCurrentHp();

        Monster monster = quest.getMonster();
        BurhanLogger logger = BurhanLogger.getInstance();
        logger.log(
            LogCategory.BATTLE,
            wanderer.getActorName(),
            "Memulai pertarungan melawan " + monster.getName() + " (Quest: " + quest.getId() + ")."
        );

        monster.resetHp();

        wanderer.resetBattleState();
        monster.resetBattleState();

        double monsterHp = monster.getCurrentHp();

        double wandererTotalDamageGiven = 0;
        double wandererTotalDamageTaken = 0;

        Difficulty difficulty = quest.getDifficulty();
        double[] multiplier = getMultiplier(difficulty);
        double atkMult = multiplier[0];
        double defMult = multiplier[1];

        System.out.println("=== Battle Dimulai ===");
        System.out.println(wanderer.getCombatInfo());
        System.out.println("vs");
        System.out.println(monster.getCombatInfo());
        System.out.println("Quest: " + quest.getName() + " (" + difficulty.name() + ")\n");

        int turn = 0;

        while(!wanderer.isDefeated() && !monster.isDefeated()){
            turn++;

            if(turn != 1){
                System.out.println("\nTekan Enter untuk melanjutkan...");
                scanner.nextLine();
            }

            System.out.println("--- Turn " + turn + " ---");

            if(turn % 2 == 1){
                wanderer.onTurnStart();

                String startNote = wanderer.consumeCustomDamageNote();
                if(startNote != null) System.out.println(startNote);

                wanderer.setBattleContext(monster, atkMult);

                double baseDamage = Math.max(1, wanderer.getAttackPower() - monster.getDefense()) * atkMult;
                double finalDamage = wanderer.modifyDamageDealt(baseDamage);

                String attackNote = wanderer.consumeCustomDamageNote();
                if(attackNote != null) System.out.println(attackNote);
                
                System.out.println(wanderer.getName() + " menyerang " + monster.getName() + "!");

                monster.takeDamage(finalDamage);

                System.out.println("Damage ke " + monster.getName() + ": " + Wanderer.df.format(finalDamage) + " (atk x" + atkMult + ")");

                wanderer.onTurnEnd(finalDamage);

                String endNote = wanderer.consumeCustomDamageNote();
                if(endNote != null) System.out.println(endNote);

                System.out.println(monster.getName() + " HP: " + Wanderer.df.format(Math.max(0, monster.getCurrentHp())) + "/" + Wanderer.df.format(monster.getMaxHp()));

            } else {
                monster.onTurnStart();

                String monsterNote = monster.consumeCustomDamageNote();
                if(monsterNote != null) System.out.println(monsterNote);

                if(!monster.isDefeated()){
                    System.out.println(monster.getName() + " menyerang " + wanderer.getName() + "!");

                    double baseDamage = Math.max(1, monster.getAttackPower() - wanderer.getDefense()) * defMult;
                    double finalDamage = wanderer.modifyDamageTaken(baseDamage);

                    wanderer.takeDamage(finalDamage);

                    String note = wanderer.consumeCustomDamageNote();
                    if(note != null) System.out.println(note);

                    System.out.println("Damage ke " + wanderer.getName() + ": " + Wanderer.df.format(finalDamage) + " (def x" + defMult + ")");
                    System.out.println(wanderer.getName() + " HP: " + Wanderer.df.format(Math.max(0, wanderer.getCurrentHp())) + "/" + Wanderer.df.format(wanderer.getMaxHp()));
                }
            }
        }

        boolean win = !wanderer.isDefeated();
        wanderer.recordBattleResult(win);
        logger.log(
            LogCategory.BATTLE,
            wanderer.getActorName(),
            "Pertarungan selesai. Hasil: " + (win ? "Menang" : "Kalah") + "."
        );

        System.out.println("\n=== Battle Selesai ===");
        System.out.print(wanderer.getName() + " ");

        wandererTotalDamageGiven = monsterHp - monster.getCurrentHp();
        wandererTotalDamageTaken = wandererHp - wanderer.getCurrentHp();

        if(win){
            System.out.println("menang!");
            int receivedExp = quest.getExpReward() + quest.getBonusExp();
            int receivedCoin = quest.getCoinReward() + quest.getBonusCoin();
            System.out.println(wanderer.getName() + " mendapatkan " + receivedExp + " exp dan " + receivedCoin + " koin!");
            wanderer.completeQuest(receivedExp, receivedCoin);
            quest.complete();
            wanderer.addCompletedQuest(quest);
        }else{
            System.out.println("kalah!");
            wanderer.setCurrentHp(wandererHp);
            System.out.println("HP dipulihkan ke kondisi sebelum battle.");
            quest.resetToAvailable();
            System.out.println("Status quest: " + quest.getStatus().getDisplayName());
        }

        System.out.println("\n=== Battle Summary ===");
        System.out.println("Total Turn: " + turn);
        System.out.println("Total Damage Diberikan " + wanderer.getName() + ": " + Wanderer.df.format(wandererTotalDamageGiven));
        System.out.println("Total Damage Diterima " + wanderer.getName() + ": " + Wanderer.df.format(wandererTotalDamageTaken));

        String summary = wanderer.getPassiveSummary();

        if(!summary.isEmpty() || monster.getBleedTriggeredCount() > 0){
            System.out.println("Passive Trigger Summary:");
            System.out.print(summary);

            if(monster.getBleedTriggeredCount() > 0){
                System.out.println("- Bleed terpicu: " + monster.getBleedTriggeredCount() + " kali");
            }
        }

        System.out.println("Hasil Akhir: " + (win ? "Menang" : "Kalah") + "\n");

        if (win) {
            System.out.println(
                seal.InstanceConfig.get().battleSigilLine(seal.InstanceConfig.pathOf(wanderer)));
            System.out.println();
        }
    }
}