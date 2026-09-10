package repository;

import java.util.ArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class GameRepository<T> {
    private final ArrayList<T> entities;

    public GameRepository() {
        this.entities = new ArrayList<>();
    }

    public void add(T entity) {
        entities.add(entity);
    }

    public boolean remove(T entity) {
        return entities.remove(entity);
    }

    public T find(Predicate<T> predicate) {
        return entities.stream()
                .filter(predicate)
                .findFirst()
                .orElse(null);
    }

    public ArrayList<T> findAll(Predicate<T> predicate) {
        return entities.stream()
                .filter(predicate)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public ArrayList<T> findAll() {
        return new ArrayList<>(entities);
    }

    public T get(int index) {
        return entities.get(index);
    }

    public int size() {
        return entities.size();
    }

    public boolean isEmpty() {
        return entities.isEmpty();
    }

    public boolean contains(T entity) {
        return entities.contains(entity);
    }

    public void clear() {
        entities.clear();
    }
}
