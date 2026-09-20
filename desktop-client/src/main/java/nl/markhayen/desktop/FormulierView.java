package nl.markhayen.desktop;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import nl.markhayen.desktop.formulier.Cell;
import nl.markhayen.desktop.formulier.Datums;
import nl.markhayen.desktop.formulier.Formulier;
import nl.markhayen.desktop.formulier.Navigatie;
import nl.markhayen.desktop.remote.GoogleDriveService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

class FormulierView {

    private static final Logger log = LoggerFactory.getLogger(FormulierView.class);
    private static final Resource FXML = new ClassPathResource("/fxml/formulier-view.fxml");

    private FormulierView() {
    }

    static Node build(String spreadsheetId, Formulier formulier, GoogleDriveService googleDrive) {
        Parent root;
        try (var fxmlInputStream = FXML.getInputStream()) {
            root = new FXMLLoader().load(fxmlInputStream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        Map<String, Object> pending = new LinkedHashMap<>();
        Map<String, String> original = new HashMap<>();

        ((Label) root.lookup("#naam")).setText(formulier.formulierNaam());
        ((VBox) root.lookup("#instellingenBody")).getChildren()
                .setAll(instellingenView(formulier, pending, original));
        ((VBox) root.lookup("#datumsBody")).getChildren().setAll(datumsTable(formulier.datums(), pending, original));
        ((VBox) root.lookup("#dagdelenBody")).getChildren()
                .setAll(cellListView(formulier.dagdelen(), pending, original, "(geen dagdelen)"));
        ((VBox) root.lookup("#afhankelijkhedenBody")).getChildren().setAll(cellListView(
                formulier.afhankelijkheden() == null ? List.of() : formulier.afhankelijkheden().aantalKindermenus(),
                pending, original, "(geen afhankelijkheden)"));
        ((VBox) root.lookup("#navigatieBody")).getChildren()
                .setAll(navigatieTable(formulier.navigatie(), pending, original));

        var save = (Button) root.lookup("#save");
        save.setOnAction(_ -> save(spreadsheetId, googleDrive, save, pending, original));

        var scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        return scroll;
    }

    private static void save(String spreadsheetId, GoogleDriveService googleDrive, Button save,
                              Map<String, Object> pending, Map<String, String> original) {
        if (pending.isEmpty()) {
            return;
        }
        var changes = Map.copyOf(pending);
        save.setDisable(true);
        Threads.offTheFxThread(() -> {
            googleDrive.updateCells(spreadsheetId, changes);
            Threads.onTheFxThread(() -> {
                changes.forEach((a1, value) -> {
                    original.put(a1, (String) value);
                    if (Objects.equals(pending.get(a1), value)) {
                        pending.remove(a1);
                    }
                });
                save.setDisable(false);
            });
        }, ex -> save.setDisable(false));
    }

    /**
     * Records {@code newValue} as a pending, unsaved change for {@code a1}, unless it matches the
     * value the cell had when the formulier was loaded, in which case any earlier pending edit for
     * that cell is dropped.
     */
    private static void track(Map<String, Object> pending, Map<String, String> original, String a1,
                               String newValue) {
        if (a1 == null) {
            return;
        }
        if (Objects.equals(newValue, original.get(a1))) {
            pending.remove(a1);
        } else {
            pending.put(a1, newValue);
        }
    }

    private static void recordOriginal(Map<String, String> original, Cell<?> cell) {
        if (cell != null && cell.a1() != null) {
            original.putIfAbsent(cell.a1(), raw(cell.value()));
        }
    }

    private static Node instellingenView(Formulier formulier, Map<String, Object> pending,
                                          Map<String, String> original) {
        var instellingen = formulier.instellingen();
        if (instellingen == null) {
            return new Label("(geen instellingen)");
        }
        var grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(4);
        int row = 0;
        row = checkBoxRow(grid, row, "Actief", instellingen.actief(), pending, original);
        row = textRow(grid, row, "Logo", instellingen.logoFilename(), pending, original);
        row = textRow(grid, row, "Naam afzender", instellingen.naamAfzender(), pending, original);
        row = textRow(grid, row, "E-mail onderwerp", instellingen.emailOnderwerp(), pending, original);
        row = textRow(grid, row, "Antwoord e-mail", instellingen.antwoordEmail(), pending, original);
        row = textRow(grid, row, "Kopie naar", instellingen.kopieNaar(), pending, original);
        row = textRow(grid, row, "API token", instellingen.apiToken(), pending, original);
        textRow(grid, row, "Resultaat spreadsheet", instellingen.resultaatSpreadsheetId(), pending, original);
        return grid;
    }

    private static int textRow(GridPane grid, int row, String label, Cell<String> cell,
                                Map<String, Object> pending, Map<String, String> original) {
        var key = new Label(label + ":");
        key.getStyleClass().add("subtle");
        var field = new TextField(cell.value() == null ? "" : cell.value());
        recordOriginal(original, cell);
        field.textProperty().addListener((_, _, newValue) -> track(pending, original, cell.a1(), newValue));
        grid.addRow(row, key, field);
        return row + 1;
    }

    private static int checkBoxRow(GridPane grid, int row, String label, Cell<Boolean> cell,
                                    Map<String, Object> pending, Map<String, String> original) {
        var key = new Label(label + ":");
        key.getStyleClass().add("subtle");
        var box = new CheckBox();
        recordOriginal(original, cell);
        box.setSelected(Boolean.TRUE.equals(cell.value()));
        box.selectedProperty().addListener((_, _, selected) -> track(pending, original, cell.a1(), raw(selected)));
        grid.addRow(row, key, box);
        return row + 1;
    }

    private static Node cellListView(List<Cell<String>> cells, Map<String, Object> pending,
                                      Map<String, String> original, String emptyText) {
        if (cells.isEmpty()) {
            return new Label(emptyText);
        }
        var box = new VBox(4);
        for (Cell<String> cell : cells) {
            var field = new TextField(cell.value() == null ? "" : cell.value());
            recordOriginal(original, cell);
            field.textProperty().addListener((_, _, newValue) -> track(pending, original, cell.a1(), newValue));
            box.getChildren().add(field);
        }
        return box;
    }

    private static Node datumsTable(List<Datums> datums, Map<String, Object> pending, Map<String, String> original) {
        if (datums.isEmpty()) {
            return new Label("(geen datums)");
        }
        var items = FXCollections.observableArrayList(datums);
        var table = new TableView<>(items);
        table.setEditable(true);
        table.getColumns().add(editableColumn("Kort", Datums::kort, Function.identity(),
                (d, c) -> new Datums(c, d.lang(), d.start(), d.eind()), items, pending, original));
        table.getColumns().add(editableColumn("Lang", Datums::lang, Function.identity(),
                (d, c) -> new Datums(d.kort(), c, d.start(), d.eind()), items, pending, original));
        table.getColumns().add(editableColumn("Start", Datums::start, FormulierView::parseDateTime,
                (d, c) -> new Datums(d.kort(), d.lang(), c, d.eind()), items, pending, original));
        table.getColumns().add(editableColumn("Eind", Datums::eind, FormulierView::parseDateTime,
                (d, c) -> new Datums(d.kort(), d.lang(), d.start(), c), items, pending, original));
        table.setPrefHeight(rowHeight(datums.size()));
        return table;
    }

    private static Node navigatieTable(List<Navigatie> navigatie, Map<String, Object> pending,
                                        Map<String, String> original) {
        if (navigatie.isEmpty()) {
            return new Label("(geen navigatie)");
        }
        var items = FXCollections.observableArrayList(navigatie);
        var table = new TableView<>(items);
        table.setEditable(true);
        table.getColumns().add(editableColumn("Volgorde", Navigatie::volgorde, FormulierView::parseInteger,
                (n, c) -> new Navigatie(c, n.sectie(), n.titel(), n.validatie(), n.conditieVeld(), n.condities(),
                        n.actief(), n.stap()), items, pending, original));
        table.getColumns().add(editableColumn("Sectie", Navigatie::sectie, Function.identity(),
                (n, c) -> new Navigatie(n.volgorde(), c, n.titel(), n.validatie(), n.conditieVeld(), n.condities(),
                        n.actief(), n.stap()), items, pending, original));
        table.getColumns().add(editableColumn("Titel", Navigatie::titel, Function.identity(),
                (n, c) -> new Navigatie(n.volgorde(), n.sectie(), c, n.validatie(), n.conditieVeld(), n.condities(),
                        n.actief(), n.stap()), items, pending, original));
        table.getColumns().add(editableColumn("Validatie", Navigatie::validatie, FormulierView::parseBoolean,
                (n, c) -> new Navigatie(n.volgorde(), n.sectie(), n.titel(), c, n.conditieVeld(), n.condities(),
                        n.actief(), n.stap()), items, pending, original));
        table.getColumns().add(editableColumn("Stap", Navigatie::stap, Function.identity(),
                (n, c) -> new Navigatie(n.volgorde(), n.sectie(), n.titel(), n.validatie(), n.conditieVeld(),
                        n.condities(), n.actief(), c), items, pending, original));
        table.setPrefHeight(rowHeight(navigatie.size()));
        return table;
    }

    /**
     * A text-editable table column bound to a {@link Cell}-valued record field. Editing a row
     * parses the typed text via {@code parse} and replaces the row in {@code items} with a copy
     * carrying the edited {@link Cell} (same A1, new value) via {@code setter}, and records the
     * raw typed text as a pending change.
     */
    private static <T, V> TableColumn<T, String> editableColumn(String title, Function<T, Cell<V>> getter,
                                                                  Function<String, V> parse,
                                                                  BiFunction<T, Cell<V>, T> setter,
                                                                  ObservableList<T> items, Map<String, Object> pending,
                                                                  Map<String, String> original) {
        for (T item : items) {
            recordOriginal(original, getter.apply(item));
        }
        var column = new TableColumn<T, String>(title);
        column.setCellValueFactory(data -> new SimpleStringProperty(raw(getter.apply(data.getValue()).value())));
        column.setCellFactory(TextFieldTableCell.forTableColumn());
        column.setOnEditCommit(event -> {
            int index = event.getTablePosition().getRow();
            T oldItem = items.get(index);
            Cell<V> cell = getter.apply(oldItem);
            String newValue = event.getNewValue();
            items.set(index, setter.apply(oldItem, new Cell<>(parse.apply(newValue), cell.a1())));
            track(pending, original, cell.a1(), newValue);
        });
        return column;
    }

    private static Integer parseInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException _) {
            log.warn("Could not parse integer from '{}'", value);
            return null;
        }
    }

    private static Boolean parseBoolean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.trim().toUpperCase()) {
            case "TRUE", "WAAR", "1" -> Boolean.TRUE;
            case "FALSE", "ONWAAR", "0" -> Boolean.FALSE;
            default -> {
                log.warn("Could not parse boolean from '{}'", value);
                yield null;
            }
        };
    }

    private static LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            return LocalDateTime.parse(trimmed);
        } catch (DateTimeParseException _) {
            try {
                return LocalDate.parse(trimmed).atStartOfDay();
            } catch (DateTimeParseException _) {
                log.warn("Could not parse date/time from '{}'", value);
                return null;
            }
        }
    }

    private static double rowHeight(int rowCount) {
        return 32d + rowCount * 28;
    }

    /**
     * The string form a value would have on the spreadsheet, e.g. {@code TRUE}/{@code FALSE} for
     * booleans - used both to seed the "original" baseline and as the text sent back on save.
     */
    private static String raw(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Boolean b) {
            return Boolean.TRUE.equals(b) ? "TRUE" : "FALSE";
        }
        return value.toString();
    }

}
