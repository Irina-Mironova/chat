package ru.geekbrains.june.chat.server;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ClientHandler {
    private Server server;
    private Socket socket;
    private String username;
    private DataInputStream in;
    private DataOutputStream out;
    private static final Logger LOGGER = LogManager.getLogger(ClientHandler.class);

    public String getUsername() {
        return username;
    }

    public ClientHandler(Server server, Socket socket) {
        try {//конструктор обработки клиентов
            this.server = server;
            this.socket = socket;
            this.in = new DataInputStream(socket.getInputStream());
            this.out = new DataOutputStream(socket.getOutputStream());
            //при подключении нового клиента создается отдельный поток для работы с ним:
            server.getCachedService().execute(()->logic());
           // new Thread(() -> logic()).start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendMessage(String message) {
        try {//отправка клиенту сообщения
            out.writeUTF(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void logic() {
        try {//в бесконечных циклах обработка авторизации клиента и поступающих от клиента сообщений:
            while (!consumeAuthorizeMessage(in.readUTF())) ;
            while (consumeRegularMessage(in.readUTF())) ;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            System.out.println("Клиент " + username + " отключился");
            server.unsubscribe(this);//удаление клиента из массива клиентов
            closeConnection(); //закрытие соединения с клиентом
            LOGGER.info("Клиент " + username + " отключился");
        }
    }

    private boolean consumeRegularMessage(String inputMessage) {
        LOGGER.info("Клиент прислал сообщение: "+ inputMessage);
        //если пришло техническое сообщение с /
        if (inputMessage.startsWith("/")) {
            //если пришло сообщение об отключении клиента
            if (inputMessage.equals("/exit")) {
                //отправляем клиенту сообщение о подтверждении выхода
                sendMessage("/exit");
                return false;
            }
            //если пришло именное сообщение,
            if (inputMessage.startsWith("/w ")) {
                //разбиваем его на три части: отправитель, получатель и само сообщение:
                String[] tokens = inputMessage.split("\\s+", 3);
                //отправляем сообщение конкретному пользователю:
                server.sendPersonalMessage(this, tokens[1], tokens[2]);
                return true;
            }
            //если пришло сообщение о смене ника
            if (inputMessage.startsWith("/nik ")) {
                String[] tokens = inputMessage.split("\\s");
                if (tokens.length == 3) {
                    String nik = tokens[1];
                    String newNik = tokens[2];
                    if (server.getAuthenticationProvider().isNikUsed(newNik)) {
                        sendMessage("SERVER: Пользователь с таким ником уже существует.Выберите другой ник");
                        return true;
                    }
                    if (server.getAuthenticationProvider().isChangeNik(nik, newNik)) {
                        sendMessage("/nikok " + newNik);
                        server.unsubscribe(this);
                        username = newNik;
                        server.subscribe(this);
                    }
                } else {
                    sendMessage("SERVER: Вы неверно указали новый ник");
                }
            }
            return true;
        }
        //если пришло не техническое сообщение, рассылаем его всем клиентам
        server.broadcastMessage(username + ": " + inputMessage);
        return true;
    }

    private boolean consumeAuthorizeMessage(String message) {
        LOGGER.info("Клиент прислал сообщение: "+ message);
        //если пришло техническое сообщение об авторизации
        if (message.startsWith("/auth ")) {
            //разбиваем его на части по пробелам.
            String[] tokens = message.split("\\s+");
            //если в сообщении 1 слово
            if (tokens.length == 1) {
                sendMessage("SERVER: Вы не указали имя пользователя и пароль");
                return false;
            }
            //если в сообщении 1 слово
            if (tokens.length == 2) {
                sendMessage("SERVER: Вы не указали пароль");
                return false;
            }
            //если в сообщении более 3 слов
            if (tokens.length > 3) {
                sendMessage("SERVER: Имя пользователя или пароль не может состоять из нескольких слов");
                return false;
            }
            //проверка: не занято ли имя пользователя
            String login = tokens[1];
            String password = tokens[2];
            String selectedUsername = server.getAuthenticationProvider().getUsernameByLoginAndPassword(login, password);
            if (selectedUsername == null) {
                sendMessage("SERVER: Неверно указан логин/пароль, либо данного пользователя нет в БД");
                return false;
            }
            if (server.isUsernameUsed(selectedUsername)) {
                sendMessage("SERVER: Данное имя пользователя уже занято");
                return false;
            }
            //если имя свободно, запоминаем его
            username = selectedUsername;

            //отправляем клиенту сообщение об успешной авторизации
            sendMessage("/authok " + username);
            //добавляем клиента в массив клиентов
            server.subscribe(this);
            return true;
        } else {
            sendMessage("SERVER: Вам необходимо авторизоваться");
            return false;
        }
    }

    private void closeConnection() {
        //закрытие соединения с бд, клиентом и входящего и исходящего потоков
        try {
            if (in != null) {
                in.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            if (out != null) {
                out.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        LOGGER.info("Соединение с базой данных закрыто");
    }
}
