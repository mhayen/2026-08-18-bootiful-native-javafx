package nl.markhayen.desktop.model;

import java.util.List;

public record Sheets(SheetProperties properties, List<Data> data) {
}
