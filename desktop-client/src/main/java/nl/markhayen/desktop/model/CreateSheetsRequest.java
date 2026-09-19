package nl.markhayen.desktop.model;

import java.util.List;

public record CreateSheetsRequest(SpreadSheetProperties properties, List<Sheets> sheets) {}
