package nl.markhayen.desktop.formulier;

public record Navigatie(Integer volgorde, String sectie, String titel, Boolean validatie, String conditieVeld,
                        String condities, Boolean actief, String stap) {
}