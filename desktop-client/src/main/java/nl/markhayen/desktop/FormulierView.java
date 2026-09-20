package nl.markhayen.desktop;

import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import nl.markhayen.desktop.formulier.Datums;
import nl.markhayen.desktop.formulier.Formulier;
import nl.markhayen.desktop.formulier.Navigatie;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

class FormulierView {

    private static final Resource FXML = new ClassPathResource("/fxml/formulier-view.fxml");

    private FormulierView() {
    }

    static Node build(Formulier formulier) {
        Parent root;
        try (var fxmlInputStream = FXML.getInputStream()) {
            root = new FXMLLoader().load(fxmlInputStream);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        ((Label) root.lookup("#naam")).setText(formulier.formulierNaam());
        ((VBox) root.lookup("#instellingenBody")).getChildren().setAll(instellingenView(formulier));
        ((VBox) root.lookup("#datumsBody")).getChildren().setAll(datumsTable(formulier.datums()));
        ((Label) root.lookup("#dagdelen")).setText(String.join(", ", formulier.dagdelen()));
        ((Label) root.lookup("#afhankelijkheden")).setText(formulier.afhankelijkheden() == null ? ""
                : String.join(", ", formulier.afhankelijkheden().aantalKindermenus()));
        ((VBox) root.lookup("#navigatieBody")).getChildren().setAll(navigatieTable(formulier.navigatie()));

        var scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        return scroll;
    }

    private static Node instellingenView(Formulier formulier) {
        var instellingen = formulier.instellingen();
        if (instellingen == null) {
            return new Label("(geen instellingen)");
        }
        var rows = List.of(
                Map.entry("Actief", str(instellingen.actief())),
                Map.entry("Logo", str(instellingen.logoFilename())),
                Map.entry("Naam afzender", str(instellingen.naamAfzender())),
                Map.entry("E-mail onderwerp", str(instellingen.emailOnderwerp())),
                Map.entry("Antwoord e-mail", str(instellingen.antwoordEmail())),
                Map.entry("Kopie naar", str(instellingen.kopieNaar())),
                Map.entry("API token", str(instellingen.apiToken())),
                Map.entry("Resultaat spreadsheet", str(instellingen.resultaatSpreadsheetId())));

        var grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(4);
        for (int i = 0; i < rows.size(); i++) {
            var entry = rows.get(i);
            var key = new Label(entry.getKey() + ":");
            key.getStyleClass().add("subtle");
            grid.addRow(i, key, new Label(entry.getValue()));
        }
        return grid;
    }

    private static Node datumsTable(List<Datums> datums) {
        if (datums.isEmpty()) {
            return new Label("(geen datums)");
        }
        var table = new TableView<Datums>();
        table.getColumns().add(column("Kort", d -> str(d.kort())));
        table.getColumns().add(column("Lang", d -> str(d.lang())));
        table.getColumns().add(column("Start", d -> str(d.start())));
        table.getColumns().add(column("Eind", d -> str(d.eind())));
        table.getItems().addAll(datums);
        table.setPrefHeight(rowHeight(datums.size()));
        return table;
    }

    private static Node navigatieTable(List<Navigatie> navigatie) {
        if (navigatie.isEmpty()) {
            return new Label("(geen navigatie)");
        }
        var table = new TableView<Navigatie>();
        table.getColumns().add(column("Volgorde", n -> str(n.volgorde())));
        table.getColumns().add(column("Sectie", n -> str(n.sectie())));
        table.getColumns().add(column("Titel", n -> str(n.titel())));
        table.getColumns().add(column("Validatie", n -> str(n.validatie())));
        table.getColumns().add(column("Stap", n -> str(n.stap())));
        table.getItems().addAll(navigatie);
        table.setPrefHeight(rowHeight(navigatie.size()));
        return table;
    }

    private static <T> TableColumn<T, String> column(String title, Function<T, String> accessor) {
        var column = new TableColumn<T, String>(title);
        column.setCellValueFactory(data -> new SimpleStringProperty(accessor.apply(data.getValue())));
        return column;
    }

    private static double rowHeight(int rowCount) {
        return 32d + rowCount * 28;
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }

}
