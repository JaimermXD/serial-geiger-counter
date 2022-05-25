module jaire.serialgeigercounter {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.fazecast.jSerialComm;

    opens jaire.serialgeigercounter to javafx.fxml;
    exports jaire.serialgeigercounter;
}