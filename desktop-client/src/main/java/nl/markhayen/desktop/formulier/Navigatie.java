package nl.markhayen.desktop.formulier;

public record Navigatie(Cell<Integer> volgorde, Cell<String> sectie, Cell<String> titel, Cell<Boolean> validatie,
                        Cell<String> conditieVeld, Cell<String> condities, Cell<Boolean> actief, Cell<String> stap) {
}
