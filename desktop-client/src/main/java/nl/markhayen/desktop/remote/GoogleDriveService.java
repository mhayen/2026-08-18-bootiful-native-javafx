package nl.markhayen.desktop.remote;

import nl.markhayen.desktop.formulier.Formulier;
import nl.markhayen.desktop.formulier.FormulierMapper;
import nl.markhayen.desktop.model.DriveFile;
import nl.markhayen.desktop.model.RunScriptResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class GoogleDriveService {

    private static final String SPREADSHEETS_QUERY =
            "mimeType='application/vnd.google-apps.spreadsheet' and trashed=false";

    private final GoogleDrive drive;
    private final FormulierMapper formulierMapper;

    private final String implementationId;

    public GoogleDriveService(GoogleDrive drive, FormulierMapper formulierMapper, @Value("${implementation-id}") String implementationId) {
        this.drive = drive;
        this.formulierMapper = formulierMapper;
        this.implementationId = implementationId;
    }

    public List<DriveFile> listSpreadsheets() {
        var response = drive.search(SPREADSHEETS_QUERY);
        return response.files().stream()
                .sorted(Comparator.comparing(DriveFile::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public Formulier fetchFormulier(String spreadsheetId) {
        var spreadSheet = drive.downloadSpreadSheet(spreadsheetId);
        return formulierMapper.toFormulier(spreadSheet);
    }

    public void updateCells(String spreadsheetId, Map<String, Object> valuesByA1Notation) {
        valuesByA1Notation.forEach((a1Notation, value) -> {
            var body = Map.of(
                    "range", a1Notation,
                    "majorDimension", "ROWS",
                    "values", List.of(List.of(value)));
            drive.updateDataInSpreadSheet(spreadsheetId, a1Notation, "USER_ENTERED", body);
        });
    }

    public String runUpdateTeksten(String formulierNaam) {
        Map<String, Object> parameters = Map.of("function", "updateTeksten",
                "parameters", List.of(formulierNaam));
        RunScriptResponse executed = drive.runScript(implementationId, parameters);
        if (executed.error() != null) {
            throw new RuntimeException(executed.error().message());
        }
        return executed.response().result();
    }

    public String runFunctionMetParameter() {
        Map<String, Object> parameters = Map.of("function", "runFunctionMetParameter",
                "parameters", List.of("input"));
        RunScriptResponse executed = drive.runScript(implementationId, parameters);
        if (executed.error() != null) {
            IO.println(executed.error());
            throw new RuntimeException(executed.error().message());
        }
        return executed.response().result();
    }
}
