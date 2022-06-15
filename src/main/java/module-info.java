module jaire.serialgeigercounter {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.swing;
    requires com.fazecast.jSerialComm;
    requires java.desktop;

    opens jaire.serialgeigercounter to javafx.fxml;
    exports jaire.serialgeigercounter;
}