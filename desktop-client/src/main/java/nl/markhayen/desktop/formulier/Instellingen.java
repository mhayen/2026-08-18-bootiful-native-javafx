package nl.markhayen.desktop.formulier;

public record Instellingen(Cell<Boolean> actief, Cell<String> logoFilename, Cell<String> naamAfzender,
                           Cell<String> emailOnderwerp, Cell<String> antwoordEmail, Cell<String> kopieNaar,
                           Cell<String> apiToken, Cell<String> resultaatSpreadsheetId) {
}
