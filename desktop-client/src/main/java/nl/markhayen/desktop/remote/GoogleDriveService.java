package nl.markhayen.desktop.remote;

import nl.markhayen.desktop.formulier.Formulier;
import nl.markhayen.desktop.formulier.FormulierMapper;
import nl.markhayen.desktop.model.DriveFile;
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

    public String runScript() {
        Map<String, Object> parameters = Map.of("function", "runFunctionMetParameter",
                "parameters", List.of("input"));
        Map<String, Object> executed = drive.runScript(implementationId, parameters);
        IO.println(executed.get("response").toString());
        return executed.get("response").toString();
    }
}
