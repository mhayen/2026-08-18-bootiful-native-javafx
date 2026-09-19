package nl.markhayen.desktop.remote;

import javafx.scene.control.TextArea;
import nl.markhayen.desktop.Threads;
import nl.markhayen.desktop.model.Data;
import nl.markhayen.desktop.model.DriveFile;
import nl.markhayen.desktop.model.RowData;
import nl.markhayen.desktop.model.SheetProperties;
import nl.markhayen.desktop.model.Sheets;
import nl.markhayen.desktop.model.SpreadSheet;
import nl.markhayen.desktop.model.SpreadSheetProperties;
import nl.markhayen.desktop.model.UserEnteredValue;
import nl.markhayen.desktop.model.Values;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static java.util.stream.Collectors.joining;

@Service
public class GoogleDriveService {
    private GoogleDrive drive;
    private JsonMapper jsonMapper;

    public GoogleDriveService(GoogleDrive drive, JsonMapper jsonMapper) {
        this.drive = drive;
        this.jsonMapper = jsonMapper;
    }

    public void downloadFormulier(TextArea output, String fileId) {
        IO.println("downloading " + fileId);
        SpreadSheet spreadSheet = drive.downloadSpreadSheet(fileId);
        Threads.onTheFxThread(() -> output.setText("downloaded " + fileId + " " + spreadSheet.properties().title()));

        spreadSheet.sheets().forEach(s -> IO.println("Title " + s.properties().title()));
        spreadSheet.sheets().forEach(s -> IO.println("size " + s.data().size()));
        List<Data> data = spreadSheet.sheets().getFirst().data();
        data.getFirst().rowData().forEach(r -> IO.println(r.values().size()));


        String downloaded = drive.downloadSpreadSheetAsString(fileId);
        JsonNode root = jsonMapper.readTree(downloaded);
        try {
            Files.createFile(Paths.get("downloaded.json"));
            Files.writeString(Paths.get("downloaded.json"), root.toPrettyString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Threads.onTheFxThread(() -> output.setText("saved json"));

    }

    public void listFiles(TextArea output) {
        try {
            var list = this.drive.list();
            String kind = list.get("kind").toString();

            Threads.onTheFxThread(() -> output.setText("kind: " + kind));
        } catch (Exception ex) {
            IO.println(ex.getMessage());
        }
    }

    public void searchFiles(TextArea output) {
        try {
            var response = this.drive.search("name = 'formulier-menukeuzekerst'");
            response.files().forEach(IO::println);
            String ids = response.files().stream().map(DriveFile::id).collect(joining("\n"));

            Threads.onTheFxThread(() -> output.setText("ids: " + ids));
        } catch (Exception ex) {
            IO.println(ex.getMessage());
        }
    }

    public void createSheet(TextArea output) {
        try {
            SpreadSheetProperties properties = new SpreadSheetProperties("My first spread sheet");
            SheetProperties sheetProperties = new SheetProperties("sheet 1", 1);
            new Sheets(sheetProperties, List.of(new Data(List.of(new RowData(List.of(new Values(new UserEnteredValue("My first value"))))))));
            var r = new SpreadSheet(properties, List.of());

            var list = drive.createSheets(r);
            String spreadsheetId = list.get("spreadsheetId").toString();
            Threads.onTheFxThread(() -> output.setText("spreadsheetId: " + spreadsheetId));
        } catch (Exception ex) {
            IO.println(ex.getMessage());
        }
    }
}
