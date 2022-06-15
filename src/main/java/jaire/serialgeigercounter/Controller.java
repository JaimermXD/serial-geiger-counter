package jaire.serialgeigercounter;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortEvent;
import com.fazecast.jSerialComm.SerialPortMessageListener;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.SnapshotParameters;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class Controller implements Initializable {
    @FXML
    private ChoiceBox<String> portsDropdown;

    @FXML
    private Button connectButton;

    @FXML
    private Button disconnectButton;

    @FXML
    private Button sendButton;

    @FXML
    private Button clearButton;

    @FXML
    private TextField inputTextField;

    @FXML
    private TextArea outputTextArea;

    @FXML
    private LineChart<String, Integer> lineChart;

    @FXML
    private CategoryAxis xAxis;

    @FXML
    private NumberAxis yAxis;

    public SerialPort selectedPort;
    private final Queue<Map<String, Object>> readings = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private Future<?> future;
    private HostServices hostServices;
    private Stage mainStage;
    private Path outputDir = Paths.get(new JFileChooser().getFileSystemView().getDefaultDirectory().toString(), "serial-geiger-counter");
    private final int maxReadingsDisplayed = 40;

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        refresh(null);
        portConnected(false);
        outputTextArea.setEditable(false);
        lineChart.setLegendVisible(false);
        lineChart.setAnimated(false);
        xAxis.setAnimated(false);
        yAxis.setAnimated(false);
    }

    private void portConnected(boolean state) {
        connectButton.setDisable(state);
        disconnectButton.setDisable(!state);
        sendButton.setDisable(!state);
        clearButton.setDisable(!state);
        inputTextField.setDisable(!state);
        outputTextArea.setDisable(!state);
    }

    private SerialPort getSelectedPort() {
        String dropdownValue = portsDropdown.getValue();

        if (dropdownValue.equals("None")) return null;
        return SerialPort.getCommPort(dropdownValue);
    }

    private String[] getPorts() {
        SerialPort[] ports = SerialPort.getCommPorts();
        Stream<String> portsStream = Arrays.stream(ports).map(SerialPort::getSystemPortName);

        return portsStream.toArray(String[]::new);
    }

    @FXML
    private void refresh(MouseEvent e) {
        portsDropdown.getItems().clear();

        String[] portNames = getPorts();
        portsDropdown.getItems().addAll(portNames);

        if (portNames.length > 0) {
            portsDropdown.setValue(portNames[0]);
        } else {
            portsDropdown.getItems().add("None");
            portsDropdown.setValue("None");
        }
    }

    @FXML
    private void connect(ActionEvent e) {
        selectedPort = getSelectedPort();

        if (selectedPort == null) {
            error("No port selected");
            return;
        }

        selectedPort.setComPortParameters(9600, 8, 1, 0);
        selectedPort.setComPortTimeouts(SerialPort.TIMEOUT_WRITE_BLOCKING | SerialPort.TIMEOUT_READ_BLOCKING, 500, 500);

        selectedPort.openPort();

        if (!selectedPort.isOpen()) {
            error("Unable to connect to port '" + selectedPort.getSystemPortName() + "'");
            return;
        }

        read();
        startPlotting();

        portConnected(true);

    }

    private void read() {
        selectedPort.addDataListener(new SerialPortMessageListener() {
            @Override
            public int getListeningEvents() {
                return SerialPort.LISTENING_EVENT_DATA_RECEIVED;
            }

            @Override
            public byte[] getMessageDelimiter() {
                return new byte[]{(byte) '\n'};
            }

            @Override
            public boolean delimiterIndicatesEndOfMessage() {
                return true;
            }

            @Override
            public void serialEvent(SerialPortEvent e) {
                byte[] bytes = e.getReceivedData();
                String data = new String(bytes, StandardCharsets.UTF_8);

                print(data);
                outputTextArea.selectPositionCaret(outputTextArea.getLength() - 1);
                outputTextArea.deselect();

                String separator = " - ";
                Pattern regex = Pattern.compile(String.format("\\d+(%s\\d+\\.?\\d*){4}", separator));
                Matcher matcher = regex.matcher(data.strip());
                if (!matcher.matches()) return;

                String[] dataArray = data.split(separator);
                Map<String, Object> reading = new HashMap<>();
                reading.put("time", Integer.parseInt(dataArray[0]));
                reading.put("cpm", Integer.parseInt(dataArray[1]));
                reading.put("averageCPM", Float.parseFloat(dataArray[2]));
                reading.put("microSv", Float.parseFloat(dataArray[3]));
                reading.put("averageMicroSv", Float.parseFloat(dataArray[4]));
                readings.add(reading);
            }
        });
    }

    private void startPlotting() {
        XYChart.Series<String, Integer> series = new XYChart.Series<>();
        lineChart.getData().add(series);

        future = executor.scheduleAtFixedRate(() -> {
            Map<String, Object> reading = readings.poll();
            if (reading == null) return;

            Platform.runLater(() -> {
                series.getData().add(new XYChart.Data<>(String.valueOf(reading.get("time")), (Integer) reading.get("cpm")));
                if (series.getData().size() > maxReadingsDisplayed) series.getData().remove(0);
            });
        }, 0, 200, TimeUnit.MILLISECONDS);
    }

    @FXML
    private void disconnect(ActionEvent e) {
        if (selectedPort == null) return;

        selectedPort.closePort();

        if (selectedPort.isOpen()) {
            error("Unable to close port '" + selectedPort.getSystemPortName() + "'");
            return;
        }

        selectedPort.removeDataListener();
        lineChart.getData().clear();
        if (future != null) future.cancel(true);
        clear(null);

        portConnected(false);
    }

    @FXML
    private void send(ActionEvent e) {
        OutputStream serialOut = selectedPort.getOutputStream();
        String data = inputTextField.getText();

        try {
            serialOut.write(data.getBytes(StandardCharsets.UTF_8));
        } catch (IOException err) {
            error("Unable to send data");
            return;
        }

        inputTextField.clear();
    }

    @FXML
    private void bindEnterToSend(KeyEvent e) {
        if (e.getCode() == KeyCode.ENTER) send(null);
    }

    @FXML
    private void clear(ActionEvent e) {
        outputTextArea.clear();
    }

    private void error(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();

        stage.getIcons().clear();
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void print(String message) {
        outputTextArea.setText(outputTextArea.getText() + message);
    }

    public void setStage(Stage stage) {
        this.mainStage = stage;
    }

    @FXML
    private void setOutputDirectory(ActionEvent e) {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        File directory = directoryChooser.showDialog(mainStage);

        outputDir = Paths.get(directory.getAbsolutePath());
    }

    @FXML
    private void saveChartImage(ActionEvent e) {
        outputDir.toFile().mkdir();

        String filename = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(Calendar.getInstance().getTime());
        WritableImage image = lineChart.snapshot(new SnapshotParameters(), null);
        File file = new File(String.valueOf(Paths.get(String.valueOf(outputDir), String.format("%s.png", filename))));

        try {
            ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file);

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Chart Image Saved");
            alert.setHeaderText(null);
            alert.setContentText(String.format("Chart image successfully saved to %s", file));
            alert.showAndWait();
        } catch (IOException err) {
            error("Unable to save chart image");
        }
    }

    @FXML
    private void saveOutputToFile(ActionEvent e) {
        outputDir.toFile().mkdir();

        String filename = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(Calendar.getInstance().getTime());
        File file = new File(String.valueOf(Paths.get(String.valueOf(outputDir), String.format("%s.txt", filename))));

        try {
            Files.write(file.toPath(), List.of(outputTextArea.getText().split("\n")), StandardCharsets.UTF_8);

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Output Saved");
            alert.setHeaderText(null);
            alert.setContentText(String.format("Output successfully saved to %s", file));
            alert.showAndWait();
        } catch (IOException err) {
            error("Unable to write output to file");
        }
    }

    @FXML
    public void exit(ActionEvent e) {
        disconnect(null);
        executor.shutdownNow();
        Platform.exit();
    }

    public void setHostServices(HostServices hostServices) {
        this.hostServices = hostServices;
    }

    @FXML
    private void contribute(ActionEvent e) {
        hostServices.showDocument("https://github.com/JaimermXD/serial-geiger-counter/");
    }

    @FXML
    private void about(ActionEvent e) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        Image icon = new Image(Objects.requireNonNull(getClass().getResourceAsStream("icon.png"), "No icon resource"), 100, 100, false, false);

        alert.setGraphic(new ImageView(icon));
        alert.setTitle("About");
        alert.setHeaderText("Serial Geiger Counter v1.2.0");
        alert.setContentText("""
                Build date: June 6, 2022
                Java version: 17.0.1
                JavaFX version: 18.0.1
                
                Copyright © 2022 Jaime Redondo
                """);
        alert.showAndWait();
    }
}