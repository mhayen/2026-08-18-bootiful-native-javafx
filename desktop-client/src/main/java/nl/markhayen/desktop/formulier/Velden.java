package nl.markhayen.desktop.formulier;

public record Velden(String veldnaam, String sectie, String sectieNaam, String subsectie, String naam, String afkorting,
                     String afkortingKleur, Integer volgorde, String soort, Boolean alleenlezen, Boolean actief,
                     Boolean verplicht, String tekst, String beschrijving, String afhankelijk, String keuzes,
                     String keuzewaarden, Integer exportvolgorde) {
}