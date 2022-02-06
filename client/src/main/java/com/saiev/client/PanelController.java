package com.saiev.client;

import com.saiev.common.FileInfo;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.stage.Stage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;
import java.util.stream.Collectors;



/* панель для файлового менеджера где отображаются файлы, путь и диски*/

public class PanelController implements Initializable {
    static Logger LOGGER = LogManager.getLogger(Main.class);
    Path currentPathServer = Path.of("server");
    Path rootPath = Path.of("server");

    @FXML
    TableView<FileInfo> filesTable;


    @FXML
    ComboBox<String> disksBox;

    @FXML
    TextField pathField;
//инициализируем таблицу и создаем колонки

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {

//создаем колонку с типом файла: файл или директория
        TableColumn<FileInfo, String> fileTypeColumn = new TableColumn<>();
        fileTypeColumn.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getType().getName()));
        fileTypeColumn.setPrefWidth(24);
//создаем колонку с названием файла
        TableColumn<FileInfo, String> fileNameColumn = new TableColumn<>("Имя");
        fileNameColumn.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getFilename()));
        fileNameColumn.setPrefWidth(200);
//создаем колонку с размером файла
        TableColumn<FileInfo, Long> fileSizeColumn = new TableColumn<>("Размер");
        fileSizeColumn.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue().getSize()));
        fileSizeColumn.setPrefWidth(120);
        fileSizeColumn.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(Long aLong, boolean b) {
                super.updateItem(aLong, b);
                if (aLong == null || b) {
                    setText(null);
                    setStyle("");
                } else {
                    String text = String.format("%,d byres", aLong);
                    if (aLong == -1L) {
                        text = "[DIR]";
                    }
                    setText(text);
                }
            }
        });
//создаем колонку с датой изменения файла
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:s");
        TableColumn<FileInfo, String> fileDateColumn = new TableColumn<>("Дата изменения");
        fileDateColumn.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().getLastModified().format(dtf)));
        fileDateColumn.setPrefWidth(120);

//добавляем в таблицу созданные колоки
        filesTable.getColumns().addAll(fileNameColumn, fileTypeColumn, fileSizeColumn, fileDateColumn);
        filesTable.getSortOrder().add(fileTypeColumn);
// добавляем диски подключенные к компьютеру
        disksBox.getItems().clear();
        for (Path p : FileSystems.getDefault().getRootDirectories()) {
            disksBox.getItems().add(p.toString());
        }
        disksBox.getItems().add("server");
        disksBox.getSelectionModel().select(0);
//добавляем слушателя на 2-е нажатие кнопки для перехода по файлу
        filesTable.setOnMouseClicked(mouseEvent -> {
            if (mouseEvent.getClickCount() == 2) {
                Path path = Path.of(pathField.getText()).resolve(filesTable.getSelectionModel().getSelectedItem().getFilename());
                if (Files.isDirectory(path)) {
                    updateList(path);
                }
                if (disksBox.getSelectionModel().getSelectedItem().equals("server")) {
                    Controller.clientConnection.sendMessage("updateFiles " + filesTable.getSelectionModel().getSelectedItem().getFilename());
                    currentPathServer = Path.of(currentPathServer.toString(), filesTable.getSelectionModel().getSelectedItem().getFilename());
                    pathField.setText(currentPathServer.normalize().toString());
                }
            }
        });
//обновляем таблицу
        updateList(Path.of("."));
    }

    /* метод позволяет обновить таблицу  в метод передается путь, из пути берутся название, тип, размер, дата модификации,
     * создается FileInfo и загружается в таблицу*/
    public void updateList(Path path) {
        filesTable.getItems().clear();
        try {
            pathField.setText(path.normalize().toAbsolutePath().toString());
            filesTable.getItems().addAll(Files.list(path).map(FileInfo::new).collect(Collectors.toList()));
            filesTable.sort();
        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.WARNING, "Не удалось обновить список файлов", ButtonType.OK);
            alert.showAndWait();
        }
    }

    //метод для обновление файлов листа через параметр List
    public void updateList(List<FileInfo> files) {
        filesTable.getItems().clear();
        pathField.setText(currentPathServer.toString());
        filesTable.getItems().addAll(files);
        filesTable.sort();
    }

    //метод для кнопки для перехода на уровень вврех
    public void btnPathUpAction(ActionEvent actionEvent) {
        if (!pathField.getText().contains("server")) {
            Path upperPath = Path.of(pathField.getText()).getParent();
            if (upperPath != null) {
                updateList(upperPath);
                return;
            }
        }

        if (currentPathServer.toString().equals("server")) {
            Alert alert = new Alert(Alert.AlertType.ERROR, "Вы находитесь в корневой директории", ButtonType.OK);
            alert.showAndWait();
            return;
        }
        if (currentPathServer.toString().contains("server")) {
            Controller.clientConnection.sendMessage("parentDirectory");
            currentPathServer = currentPathServer.getParent();
            pathField.setText(currentPathServer.normalize().toString());
        }
    }

    //метод для смены диска
    public void selectDiskAction(ActionEvent actionEvent) {
        ComboBox<String> element = (ComboBox<String>) actionEvent.getSource();

        if (element.getSelectionModel().getSelectedItem().equals("server")) {
            LOGGER.info("Пользователь зашел на серверное хранилище");
            filesTable.getItems().clear();
            if (!ClientConnection.isAuth) {
                Stage stage = new Stage();
                Label lbl = new Label();
                TextField login = new TextField();
                TextField password = new TextField();
                login.setPrefColumnCount(20);
                login.setText("Логин");
                password.setPrefColumnCount(11);
                password.setText("Пароль");
                Button btn = new Button("Войти");
                btn.setOnAction(event -> {
                    Controller.clientConnection.sendMessage("auth " + login.getText().trim() + " " + password.getText().trim());
                    stage.close();
                });
                FlowPane root = new FlowPane(Orientation.VERTICAL, 10, 10, login, password, btn, lbl);
                Scene scene = new Scene(root, 250, 100);

                stage.setScene(scene);
                stage.setTitle("Пройдите авторизацию");
                stage.show();
            }
            if (ClientConnection.isAuth) {
                Controller.clientConnection.sendMessage("serverStorage");
            }

        } else {
            updateList(Path.of(element.getSelectionModel().getSelectedItem()));
        }

    }

    // метод для возвращения названия файла, нужен для перехода по пути
    public String getSelectedFileName() {
        if (!filesTable.isFocused()) {
            return null;
        }
        return filesTable.getSelectionModel().getSelectedItem().getFilename();
    }

    //метод для возвращения текущего пути, нужен для перехода по дереву
    public String getCurrentPath() {
        return pathField.getText();
    }

    public void btnCreateDir(ActionEvent actionEvent) {
        Stage stage = new Stage();
        Label lbl = new Label();
        TextField textField = new TextField();
        textField.setPrefColumnCount(11);
        Button btn = new Button("Создать");
        btn.setOnAction(event -> {
            Path newPath = Path.of(pathField.getText().concat("/").concat(textField.getText()));
            if (pathField.getText().contains("server")) {
                Controller.clientConnection.sendMessage("create " + textField.getText());
                stage.close();
                return;
            }
            try {
                Files.createDirectories(newPath);
                updateList(newPath.getParent());
            } catch (IOException e) {
                e.printStackTrace();
            }
            stage.close();
        });
        FlowPane root = new FlowPane(Orientation.VERTICAL, 10, 10, textField, btn, lbl);
        Scene scene = new Scene(root, 250, 100);

        stage.setScene(scene);
        stage.setTitle("Введите название папки");
        stage.show();
    }

    //метод для поиска локальных файлов
    public void btnFindFile(ActionEvent actionEvent) {
        Stage stage = new Stage();
        Label lbl = new Label();
        TextField textField = new TextField();
        textField.setPrefColumnCount(11);
        Button btn = new Button("Найти файл");
        btn.setOnAction(event -> {
            Path rootPath = Paths.get(disksBox.getSelectionModel().getSelectedItem());
            if (rootPath.toString().contains("server")) {
                Controller.clientConnection.sendMessage("find " + textField.getText());
                stage.close();
                return;
            }
            String fileToFind = File.separator + textField.getText();
            try {
                Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        String fileString = file.toAbsolutePath().toString();

                        if (fileString.endsWith(fileToFind)) {
                            filesTable.getItems().clear();
                            filesTable.getItems().add(new FileInfo(file));
                            pathField.setText(file.normalize().toString());
                            return FileVisitResult.TERMINATE;
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
            stage.close();
        });
        FlowPane root = new FlowPane(Orientation.VERTICAL, 10, 10, textField, btn, lbl);
        Scene scene = new Scene(root, 250, 100);

        stage.setScene(scene);
        stage.setTitle("Введите название файла");
        stage.show();
    }
}
