package nl.markhayen.desktop.model;

import java.util.List;

public record SpreadSheet(SpreadSheetProperties properties, List<Sheets> sheets) {
}
