package ru.geekbrains.june.chat.client;


import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;

import java.io.*;
import java.net.Socket;
import java.util.LinkedList;
import java.util.List;


public class Controller {
    @FXML
    TextArea chatArea;

    @FXML
    TextField messageField, loginField, newNikField;

    @FXML
    HBox authPanel, msgPanel, nikPanel;

    @FXML
    PasswordField passwordField;

    @FXML
    ListView<String> clientsListView;

    @FXML
    Label labelUser;

    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private String nik;
    private String firstNik;
    private String nextNik;
    private final int CHAT_COUNT = 100; //кол-во загруженных строк чата
    private BufferedWriter bufferedWriter;
    private FileWriter fileWriter;

    public void setAuthorized(boolean authorized) {
        //простановка видимости панелей элементов
        msgPanel.setVisible(authorized);
        msgPanel.setManaged(authorized);
        nikPanel.setVisible(authorized);
        nikPanel.setManaged(authorized);
        authPanel.setVisible(!authorized);
        authPanel.setManaged(!authorized);
        clientsListView.setVisible(authorized);
        clientsListView.setManaged(authorized);
        Platform.runLater(() -> {
            if (authPanel.isVisible()) {
                labelUser.setText("Введите имя пользователя:");
                chatArea.clear();
            } else {
                labelUser.setText("Пользователь авторизован под именем " + nik);
                messageField.requestFocus();
            }
        });
    }

    public void sendMessage() {
        try {
            //отправка сообщения на сервер из поля messageField:
            out.writeUTF(messageField.getText());
            messageField.clear(); // очистка messageField
            messageField.requestFocus(); //фокус на messageField
        } catch (IOException e) {
            showError("Невозможно отправить сообщение на сервер");
        }
    }

    public void sendNewNik() {
        try { //отправка на сервер сообщения о новом нике:
            if (newNikField.getText() != "") {
                out.writeUTF("/nik " + nik + " " + newNikField.getText());
                newNikField.clear();
            }
        } catch (IOException e) {
            showError("Невозможно отправить новый ник на сервер");
        }
    }

    public void sendCloseRequest() {
        try { //если поток с сервером существует, отправка на сервер сообщения /exit о выходе пользователя из чата
            if (out != null) {
                out.writeUTF("/exit");
            }
        } catch (IOException e) {
           e.printStackTrace();
        }finally {
            try {
                if (bufferedWriter != null) {
                    bufferedWriter.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                if (fileWriter != null) {
                    fileWriter.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            // переименовываем файл с историей, если менялся ник
            if (nextNik != "" && nextNik != null) {
                File file = new File(firstNik + ".txt");
                file.renameTo(new File(nextNik + ".txt"));
                nextNik = "";
            }
        }

    }

    public void tryToAuth() {
        if (!connect()) {//подключение к серверу
            return;
        }
        try { //отправка на сервер сообщения об авторизации пользователя с именем и паролем:
            out.writeUTF("/auth " + loginField.getText() + " " + passwordField.getText());
            loginField.clear(); // очистка поля usernameField
            passwordField.clear();
        } catch (IOException e) {
            showError("Невозможно отправить запрос авторизации на сервер");
        }
    }


    public boolean connect() {
        //если соединение с сервером уже есть, то выходим из метода
        if (socket != null && !socket.isClosed()) {
            return true;
        } // в противном случае,
        try { //подключаемся к серверу localhost
            socket = new Socket("localhost", 8189);
            //создаем входящий и исходящий поток
            in = new DataInputStream(socket.getInputStream());
            out = new DataOutputStream(socket.getOutputStream());
            //запускаем отдельный поток для общения с сервером
            new Thread(() -> logic()).start();
            return true;
        } catch (IOException e) {
            showError("Невозможно подключиться к серверу");
            return false;
        }
    }

    private void logic() {
        try { //анализ входящих сообщений от сервера
            while (true) {
                String inputMessage = in.readUTF();
                //если от сервера пришло техническое сообщение /exit, то закрываем соединение с сервером
                if (inputMessage.equals("/exit")) {
                    closeConnection();
                }
                //если от сервера пришло техническое сообщение /authok, то авторизация прошла успешно, меняем
                //"видимость" панелей элементов на форме
                if (inputMessage.startsWith("/authok ")) {
                    nik = inputMessage.split("\\s+")[1];
                    firstNik = nik;
                    setAuthorized(true);
                    loadHistory(nik + ".txt");
                    openFileForWrite(nik + ".txt");
                    break;
                }
                //если сообщение от сервера не техническое, то выводим его в окно чата:
                chatArea.appendText(inputMessage + "\n");

            }
            while (true) {
                String inputMessage = in.readUTF();
                //если сообщение от сервера начинается с /
                if (inputMessage.startsWith("/")) {
                    //если сообщение от сервера начинается /exit, то выходим из цикла
                    if (inputMessage.equals("/exit")) {
                        break;
                    }
                    //если сообщение от сервера начинается /nikok
                    if (inputMessage.startsWith("/nikok ")) {
                        String[] tokens = inputMessage.split("\\s");
                        nik = tokens[1];
                        nextNik = nik;
                        Platform.runLater(() -> {
                            labelUser.setText("Пользователь авторизован под именем " + nik);
                            messageField.requestFocus();
                        });
                        continue;
                    }
                    // обновление листа клиентов, если от сервера пришло сообщение /clients_list
                    if (inputMessage.startsWith("/clients_list ")) {
                        Platform.runLater(() -> {
                            //каждое слово из входящего сообщения помещаем в массив:
                            String[] tokens = inputMessage.split("\\s+");
                            clientsListView.getItems().clear(); // очищаем предыдущий список
                            //добавляем клиентов в clientsListView:
                            for (int i = 1; i < tokens.length; i++) {
                                clientsListView.getItems().add(tokens[i]);
                            }
                        });
                    }
                    continue; //игнорируем код ниже
                }
                //если сообщение от сервера не техническое, то выводим его в окно чата:
                chatArea.appendText(inputMessage + "\n");
                bufferedWriter.write(inputMessage + "\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            closeConnection(); //закрытие соединения
        }
    }

    private void closeConnection() {
        //простановка видимости панелей элементов
        setAuthorized(false);
        try {
            if (bufferedWriter != null) {
                bufferedWriter.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            if (fileWriter != null) {
                fileWriter.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        // переименовываем файл с историей, если менялся ник
        if (nextNik != "" && nextNik != null) {
            File file = new File(firstNik + ".txt");
            file.renameTo(new File(nextNik + ".txt"));
            nextNik = "";
        }

        try {
            if (in != null) {
                in.close();
            }
        } catch (
                IOException e) {
            e.printStackTrace();
        }
        try {
            if (out != null) {
                out.close();
            }
        } catch (
                IOException e) {
            e.printStackTrace();
        }
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (
                IOException e) {
            e.printStackTrace();
        }

    }

    public void showError(String message) {
        //вывод сообщения об ошибке
        new Alert(Alert.AlertType.ERROR, message, ButtonType.OK).showAndWait();
    }

    public void clientsListDoubleClick(MouseEvent mouseEvent) {
        //если произошел двойной клик мышкой на  clientsListView
        if (mouseEvent.getClickCount() == 2) {
            //запоминаем выделенный элемент
            String selectedUser = clientsListView.getSelectionModel().getSelectedItem();
            //формируем сообщение для выбранного пользователя
            messageField.setText("/w " + selectedUser + " ");
            //перемещаем фокус на messageField на конец строки
            messageField.requestFocus();
            messageField.selectEnd();
        }
    }

    public void loadHistory(String path) {
        File file = new File(path);
        if (file.exists()) {
            try (BufferedReader bufferedReader = new BufferedReader(new FileReader(file))) {
                List<String> linkedList = new LinkedList<>();
                String line;
                while ((line = bufferedReader.readLine()) != null){
                    linkedList.add(line);
                    if (linkedList.size() > CHAT_COUNT){
                        linkedList.remove(0);
                    }
                }
                chatArea.clear();
                for (String a:linkedList) {
                    chatArea.appendText(a + "\n");
                }

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            try {
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void openFileForWrite(String path) {
        File file = new File(path);
        try {
            fileWriter = new FileWriter(file, true);
            bufferedWriter = new BufferedWriter(fileWriter);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
