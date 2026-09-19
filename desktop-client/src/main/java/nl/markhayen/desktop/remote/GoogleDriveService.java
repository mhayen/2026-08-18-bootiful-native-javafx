package nl.markhayen.desktop.remote;

import nl.markhayen.desktop.formulier.Formulier;
import nl.markhayen.desktop.formulier.FormulierMapper;
import nl.markhayen.desktop.model.DriveFile;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class GoogleDriveService {

    private static final String SPREADSHEETS_QUERY =
            "mimeType='application/vnd.google-apps.spreadsheet' and trashed=false";

    private final GoogleDrive drive;
    private final FormulierMapper formulierMapper;

    public GoogleDriveService(GoogleDrive drive, FormulierMapper formulierMapper) {
        this.drive = drive;
        this.formulierMapper = formulierMapper;
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
}
