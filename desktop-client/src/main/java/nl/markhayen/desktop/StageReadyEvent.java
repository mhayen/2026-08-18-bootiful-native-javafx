package nl.markhayen.desktop;

import javafx.stage.Stage;
import org.springframework.context.ApplicationEvent;

class StageReadyEvent extends ApplicationEvent {

	StageReadyEvent(Stage stage) {
		super(stage);
	}

	Stage stage() {
		return (Stage) getSource();
	}

}
