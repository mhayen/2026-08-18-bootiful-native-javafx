package nl.markhayen.desktop.formulier;

import java.time.LocalDateTime;

public record Datums(Cell<String> kort, Cell<String> lang, Cell<LocalDateTime> start, Cell<LocalDateTime> eind) {
}
