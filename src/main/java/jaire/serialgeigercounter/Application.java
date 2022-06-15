package jaire.serialgeigercounter;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Objects;

public class Application extends javafx.application.Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("view.fxml"));
        Scene scene = new Scene(fxmlLoader.load());
        Controller controller = fxmlLoader.getController();

        controller.setHostServices(getHostServices());
        controller.setStage(stage);

        Image icon = new Image(Objects.requireNonNull(getClass().getResourceAsStream("icon.png"), "No icon resource"));
        stage.getIcons().add(icon);

        stage.setTitle("Serial Geiger Counter");

        stage.setOnCloseRequest(e -> {
            controller.exit(null);
        });

        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}