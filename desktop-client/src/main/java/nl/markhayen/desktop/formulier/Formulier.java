package nl.markhayen.desktop.formulier;

import java.util.List;

public record Formulier(String formulierNaam, List<Secties> secties, List<Datums> datums, List<String> dagdelen,
                        Afhankelijkheden afhankelijkheden, Instellingen instellingen, List<Navigatie> navigatie) {
}