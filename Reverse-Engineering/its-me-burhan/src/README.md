# BurhanQuest

A console-based guild/quest management game (Frieren-themed) written in Java.

- Login as admin or as a registered wanderer.
- Manage quests, monsters, and wanderers; filter, sort, and run battle simulations.
- Export/import quest and wanderer data (CSV/TXT).

## Build & run
```
javac -d out $(find . -name '*.java' -not -path './test/*')
java -cp out Main
```