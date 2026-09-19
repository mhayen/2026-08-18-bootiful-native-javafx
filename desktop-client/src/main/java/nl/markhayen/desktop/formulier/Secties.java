package nl.markhayen.desktop.formulier;

import java.util.List;

public record Secties(String titel, String sectie, String sectieNaam, List<Velden> velden) {
}