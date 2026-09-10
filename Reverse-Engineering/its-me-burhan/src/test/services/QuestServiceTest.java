package test.services;

import entities.Wanderer;
import exception.InsufficientLevelException;

import org.junit.Before;
import org.junit.Test;

import quests.BountyQuest;
import quests.DailyQuest;
import quests.Quest;
import quests.RegularQuest;
import quests.enums.Difficulty;
import quests.enums.QuestStatus;
import services.MonsterService;
import services.QuestService;

import java.util.ArrayList;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class QuestServiceTest {
    private MonsterService monsterService;
    private QuestService questService;

    @Before
    public void setUp() {
        monsterService = new MonsterService();
        questService = new QuestService(monsterService);
        monsterService.addMonster("Slime", 50, 10, 2, 100, 50);
        monsterService.addMonster("Goblin", 100, 30, 10, 500, 300);
        monsterService.addMonster("Dragon", 300, 200, 20, 1000, 500);
    }

    private void addThreeQuests() {
        questService.addQuest("Latihan Harian", "Kalahkan slime", Difficulty.MUDAH, monsterService.fetchMonsterbyId("1"), "1", "0", "0");
        questService.addQuest("Pembersihan Hutan", "Bersihkan hutan", Difficulty.MENENGAH, monsterService.fetchMonsterbyId("2"), "2", "0", "0");
        questService.addQuest("Sarang Naga", "Kalahkan naga", Difficulty.SULIT, monsterService.fetchMonsterbyId("3"), "3", "10000", "5000");
    }

    private void assertInvalid(Runnable action, String expectedMessage) {
        try {
            action.run();
            fail("Seharusnya melempar IllegalArgumentException.");
        } catch (IllegalArgumentException e) {
            assertEquals(expectedMessage, e.getMessage());
        }
    }

    @Test
    public void testAddQuestCreatesCorrectQuestTypes() {
        addThreeQuests();

        assertTrue(questService.fetchQuestbyId("Q1") instanceof DailyQuest);
        assertTrue(questService.fetchQuestbyId("Q2") instanceof RegularQuest);
        assertTrue(questService.fetchQuestbyId("Q3") instanceof BountyQuest);
        assertEquals(3, questService.getQuestsCount());
    }

    @Test
    public void testFilterSortAndShowQuest() {
        addThreeQuests();
        Quest regularQuest = questService.fetchQuestbyId("Q2");
        regularQuest.complete();

        ArrayList<Quest> completed = questService.filterQuestByStatus(QuestStatus.SELESAI);
        assertEquals(1, completed.size());
        assertEquals("Pembersihan Hutan", completed.get(0).getName());
        assertEquals(Difficulty.MUDAH, questService.sortQuestByDifficulty(true).get(0).getDifficulty());
        assertTrue(questService.showQuests(questService.getQuests()).contains("Sarang Naga"));
        assertTrue(questService.showAvailableQuest().contains("Q1. Latihan Harian"));
    }


    @Test
    public void testFindQuestByIdReturnsOptionalForExistingAndMissingQuest() {
        addThreeQuests();

        Optional<Quest> found = questService.findQuestById("Q2");

        assertTrue(found.isPresent());
        assertEquals("Pembersihan Hutan", found.orElse(null).getName());
        assertFalse(questService.findQuestById("Q99").isPresent());
        assertFalse(questService.findQuestById(null).isPresent());
    }

    @Test
    public void testFilterQuestByDifficultyNameReturnsOptionalResult() {
        addThreeQuests();

        Optional<ArrayList<Quest>> easyQuests = questService.filterQuestByDifficultyName(" mudah ");

        assertTrue(easyQuests.isPresent());
        assertEquals(1, easyQuests.orElse(new ArrayList<Quest>()).size());
        assertEquals("Latihan Harian", easyQuests.orElse(new ArrayList<Quest>()).get(0).getName());
        assertFalse(questService.filterQuestByDifficultyName("extreme").isPresent());
        assertFalse(questService.filterQuestByDifficultyName(null).isPresent());
    }

    @Test
    public void testQuestValidationMethods() {
        addThreeQuests();

        assertTrue(questService.isQuestDifficultyValid("Sulit"));
        assertFalse(questService.isQuestDifficultyValid("extreme"));
        assertTrue(questService.isQuestTypeValid("3"));
        assertTrue(questService.isQuestIdValid("Q1"));
        assertFalse(questService.isQuestIdValid("1"));
    }

    @Test
    public void testValidateQuestLevelRequirement() {
        addThreeQuests();
        Wanderer wanderer = new Wanderer(1, "Frieren", "frieren", "staff123", 100, 50, 20);

        try {
            questService.validateQuestLevelRequirement(wanderer, questService.fetchQuestbyId("Q3"));
            fail("Seharusnya melempar InsufficientLevelException.");
        } catch (InsufficientLevelException e) {
            assertTrue(e.getMessage().contains("Level pengembara"));
        }
    }

    @Test
    public void testValidateQuestInputAcceptsValidInput() {
        questService.validateQuestInput("Quest 123", "Deskripsi valid", "mudah", "1", "1");
        questService.validateQuestInput("Quest Sulit", "Deskripsi valid", "SULIT", "3", "3");
    }

    @Test
    public void testValidateQuestInputRejectsInvalidName() {
        assertInvalid(new Runnable() {
            @Override
            public void run() {
                questService.validateQuestInput("Quest!!!", "Deskripsi valid", "mudah", "1", "1");
            }
        }, "Nama quest hanya boleh berisi karakter alfanumerik dan spasi, serta tidak boleh kosong.");

        assertInvalid(new Runnable() {
            @Override
            public void run() {
                questService.validateQuestInput("", "Deskripsi valid", "mudah", "1", "1");
            }
        }, "Nama quest hanya boleh berisi karakter alfanumerik dan spasi, serta tidak boleh kosong.");
    }

    @Test
    public void testValidateQuestInputRejectsEmptyDescription() {
        assertInvalid(new Runnable() {
            @Override
            public void run() {
                questService.validateQuestInput("Quest Valid", null, "mudah", "1", "1");
            }
        }, "Deskripsi quest tidak boleh kosong.");

        assertInvalid(new Runnable() {
            @Override
            public void run() {
                questService.validateQuestInput("Quest Valid", "", "mudah", "1", "1");
            }
        }, "Deskripsi quest tidak boleh kosong.");
    }

    @Test
    public void testValidateQuestInputRejectsInvalidDifficulty() {
        assertInvalid(new Runnable() {
            @Override
            public void run() {
                questService.validateQuestInput("Quest Valid", "Deskripsi valid", "legendary", "1", "1");
            }
        }, "Tingkat kesulitan quest hanya boleh: mudah, menengah, atau sulit.");
    }

    @Test
    public void testValidateQuestInputRejectsInvalidMonsterNumberFormat() {
        assertInvalid(new Runnable() {
            @Override
            public void run() {
                questService.validateQuestInput("Quest Valid", "Deskripsi valid", "mudah", "", "1");
            }
        }, "Nomor monster hanya boleh berisi angka dan tidak boleh kosong.");

        assertInvalid(new Runnable() {
            @Override
            public void run() {
                questService.validateQuestInput("Quest Valid", "Deskripsi valid", "mudah", "0", "1");
            }
        }, "Nomor monster hanya boleh berisi angka dan tidak boleh kosong.");

        assertInvalid(new Runnable() {
            @Override
            public void run() {
                questService.validateQuestInput("Quest Valid", "Deskripsi valid", "mudah", "M1", "1");
            }
        }, "Nomor monster hanya boleh berisi angka dan tidak boleh kosong.");
    }

    @Test
    public void testValidateQuestInputRejectsMonsterNumberOutsideList() {
        assertInvalid(new Runnable() {
            @Override
            public void run() {
                questService.validateQuestInput("Quest Valid", "Deskripsi valid", "mudah", "4", "1");
            }
        }, "Nomor monster harus salah satu dari yang tertera pada list.");
    }

    @Test
    public void testValidateQuestInputRejectsInvalidQuestType() {
        assertInvalid(new Runnable() {
            @Override
            public void run() {
                questService.validateQuestInput("Quest Valid", "Deskripsi valid", "mudah", "1", "4");
            }
        }, "Tipe quest harus salah satu dari yang tertera pada list.");

        assertInvalid(new Runnable() {
            @Override
            public void run() {
                questService.validateQuestInput("Quest Valid", "Deskripsi valid", "mudah", "1", null);
            }
        }, "Tipe quest harus salah satu dari yang tertera pada list.");
    }

    @Test
    public void testValidateBountyRewardInputAcceptsNonNegativeIntegers() {
        questService.validateBountyRewardInput("0", "0");
        questService.validateBountyRewardInput("150", "300");
    }

    @Test
    public void testValidateBountyRewardInputRejectsInvalidBonusExp() {
        assertInvalid(new Runnable() {
            @Override
            public void run() {
                questService.validateBountyRewardInput(null, "10");
            }
        }, "Bonus exp harus bilangan bulat nonnegatif.");

        assertInvalid(new Runnable() {
            @Override
            public void run() {
                questService.validateBountyRewardInput("-1", "10");
            }
        }, "Bonus exp harus bilangan bulat nonnegatif.");
    }

    @Test
    public void testValidateBountyRewardInputRejectsInvalidBonusCoin() {
        assertInvalid(new Runnable() {
            @Override
            public void run() {
                questService.validateBountyRewardInput("10", "");
            }
        }, "Bonus coin harus bilangan bulat nonnegatif.");

        assertInvalid(new Runnable() {
            @Override
            public void run() {
                questService.validateBountyRewardInput("10", "coin");
            }
        }, "Bonus coin harus bilangan bulat nonnegatif.");
    }

    @Test
    public void testValidateQuestFilterTypeAcceptsOneAndTwo() {
        questService.validateQuestFilterType("1");
        questService.validateQuestFilterType("2");
    }

    @Test
    public void testValidateQuestFilterTypeRejectsInvalidInput() {
        assertInvalid(new Runnable() {
            @Override
            public void run() {
                questService.validateQuestFilterType(null);
            }
        }, "Pilihan tidak valid. Harap masukkan pilihan dengan benar.");

        assertInvalid(new Runnable() {
            @Override
            public void run() {
                questService.validateQuestFilterType("3");
            }
        }, "Pilihan tidak valid. Harap masukkan pilihan dengan benar.");
    }

    @Test
    public void testValidateQuestStatusInputAcceptsValidStatus() {
        questService.validateQuestStatusInput("tersedia");
        questService.validateQuestStatusInput("selesai");
    }

    @Test
    public void testValidateQuestStatusInputRejectsInvalidStatus() {
        assertInvalid(new Runnable() {
            @Override
            public void run() {
                questService.validateQuestStatusInput(null);
            }
        }, "Pilihan tidak valid. Harap masukkan pilihan dengan benar.");

        assertInvalid(new Runnable() {
            @Override
            public void run() {
                questService.validateQuestStatusInput("pending");
            }
        }, "Pilihan tidak valid. Harap masukkan pilihan dengan benar.");
    }

    @Test
    public void testValidateQuestDifficultyInputAcceptsValidDifficulty() {
        questService.validateQuestDifficultyInput("mudah");
        questService.validateQuestDifficultyInput("menengah");
        questService.validateQuestDifficultyInput("sulit");
    }

    @Test
    public void testValidateQuestDifficultyInputRejectsInvalidDifficulty() {
        assertInvalid(new Runnable() {
            @Override
            public void run() {
                questService.validateQuestDifficultyInput(null);
            }
        }, "Pilihan tidak valid. Harap masukkan pilihan dengan benar.");

        assertInvalid(new Runnable() {
            @Override
            public void run() {
                questService.validateQuestDifficultyInput("");
            }
        }, "Pilihan tidak valid. Harap masukkan pilihan dengan benar.");

        assertInvalid(new Runnable() {
            @Override
            public void run() {
                questService.validateQuestDifficultyInput("extreme");
            }
        }, "Pilihan tidak valid. Harap masukkan pilihan dengan benar.");
    }

    @Test
    public void testValidateQuestSortTypeAcceptsOneAndTwo() {
        questService.validateQuestSortType("1");
        questService.validateQuestSortType("2");
    }

    @Test
    public void testValidateQuestSortTypeRejectsInvalidInput() {
        assertInvalid(new Runnable() {
            @Override
            public void run() {
                questService.validateQuestSortType(null);
            }
        }, "Urutan tidak valid. Harap masukkan urutan dengan benar.");

        assertInvalid(new Runnable() {
            @Override
            public void run() {
                questService.validateQuestSortType("3");
            }
        }, "Urutan tidak valid. Harap masukkan urutan dengan benar.");
    }

    @Test
    public void testValidateQuestIdInputAcceptsExistingQuestId() {
        addThreeQuests();
        questService.validateQuestIdInput("Q1");
        questService.validateQuestIdInput("Q3");
    }

    @Test
    public void testValidateQuestIdInputRejectsUnknownQuestId() {
        addThreeQuests();

        assertInvalid(new Runnable() {
            @Override
            public void run() {
                questService.validateQuestIdInput(null);
            }
        }, "Quest tidak ditemukan.");

        assertInvalid(new Runnable() {
            @Override
            public void run() {
                questService.validateQuestIdInput("Q99");
            }
        }, "Quest tidak ditemukan.");
    }
}
