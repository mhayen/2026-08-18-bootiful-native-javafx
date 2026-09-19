package nl.markhayen.desktop.formulier;

public record Instellingen(Boolean actief, String logoFilename, String naamAfzender, String emailOnderwerp,
                           String antwoordEmail, String kopieNaar, String apiToken, String resultaatSpreadsheetId) {
}
