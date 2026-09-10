package test.repository;

import org.junit.Test;

import repository.GameRepository;

import java.util.ArrayList;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class GameRepositoryTest {
    @Test
    public void testAddGetSizeIsEmptyAndContains() {
        GameRepository<String> repository = new GameRepository<>();

        assertTrue(repository.isEmpty());

        repository.add("Slime");
        repository.add("Goblin");

        assertFalse(repository.isEmpty());
        assertEquals(2, repository.size());
        assertEquals("Slime", repository.get(0));
        assertEquals("Goblin", repository.get(1));
        assertTrue(repository.contains("Slime"));
        assertFalse(repository.contains("Dragon"));
    }

    @Test
    public void testRemoveReturnsTrueOnlyWhenEntityExists() {
        GameRepository<String> repository = new GameRepository<>();
        repository.add("Slime");
        repository.add("Goblin");

        assertTrue(repository.remove("Slime"));
        assertFalse(repository.remove("Dragon"));
        assertEquals(1, repository.size());
        assertEquals("Goblin", repository.get(0));
    }

    @Test
    public void testFindReturnsFirstMatchingEntityOrNull() {
        GameRepository<Integer> repository = new GameRepository<>();
        repository.add(10);
        repository.add(25);
        repository.add(40);

        Integer found = repository.find(new Predicate<Integer>() {
            public boolean test(Integer number) {
                return number > 20;
            }
        });

        Integer notFound = repository.find(new Predicate<Integer>() {
            public boolean test(Integer number) {
                return number > 100;
            }
        });

        assertEquals(Integer.valueOf(25), found);
        assertNull(notFound);
    }

    @Test
    public void testFindAllWithPredicateReturnsMatchingEntities() {
        GameRepository<Integer> repository = new GameRepository<>();
        repository.add(1);
        repository.add(2);
        repository.add(3);
        repository.add(4);

        ArrayList<Integer> evenNumbers = repository.findAll(new Predicate<Integer>() {
            public boolean test(Integer number) {
                return number % 2 == 0;
            }
        });

        assertEquals(2, evenNumbers.size());
        assertEquals(Integer.valueOf(2), evenNumbers.get(0));
        assertEquals(Integer.valueOf(4), evenNumbers.get(1));
    }

    @Test
    public void testFindAllWithoutPredicateReturnsCopy() {
        GameRepository<String> repository = new GameRepository<>();
        repository.add("Quest A");
        repository.add("Quest B");

        ArrayList<String> entities = repository.findAll();
        entities.clear();

        assertEquals(2, repository.size());
        assertEquals("Quest A", repository.get(0));
        assertEquals("Quest B", repository.get(1));
    }

    @Test
    public void testClearRemovesAllEntities() {
        GameRepository<String> repository = new GameRepository<>();
        repository.add("Frieren");
        repository.add("Fern");

        repository.clear();

        assertTrue(repository.isEmpty());
        assertEquals(0, repository.size());
    }
}
