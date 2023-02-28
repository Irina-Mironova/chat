package ru.geekbrains.june.chat.server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Server {
    private AuthenticationProvider authenticationProvider;
    private List<ClientHandler> clients; //список клиентов
    private ExecutorService cachedService;
    private static final Logger LOGGER = LogManager.getLogger(Server.class);

    public AuthenticationProvider getAuthenticationProvider() {
        return authenticationProvider;
    }

    public ExecutorService getCachedService() {
        return cachedService;
    }

    public Server() {
        try {
            this.authenticationProvider = new BDAuthenticationProvider();
            //создание нового массива клиентов
            this.clients = new ArrayList<>();
            //подключение к серверу
            ServerSocket serverSocket = new ServerSocket(8189);
            //подключение к БД
            authenticationProvider.connect();
            System.out.println("Сервер запущен. Ожидаем подключение клиентов..");
            LOGGER.info("Сервер запущен. Ожидаем подключение клиентов.");
            cachedService = Executors.newCachedThreadPool();
            while (true) { //ожидание подключения нового клиента
                Socket socket = serverSocket.accept();
                System.out.println("Подключился новый клиент");
                LOGGER.info("Подключился новый клиент");
                new ClientHandler(this, socket); //передача нового клиента обработчику клиентов
            }
        } catch (IOException e) {
            e.printStackTrace();
            LOGGER.error("Ошибка подключения");

        }
        finally {
            cachedService.shutdown();
            if (authenticationProvider != null){
                authenticationProvider.disconnect();
            }
            LOGGER.info("Сервер отключен");
        }
    }

    public synchronized void subscribe(ClientHandler c) {
        //рассылка широковещательного сообщения о новом пользователе:
        broadcastMessage("В чат зашел пользователь " + c.getUsername());
        //добавление нового клиента в массив клиентов:
        clients.add(c);
        //рассылка списка клиентов всем клиентам
        broadcastClientList();
        LOGGER.info("В чат зашел пользователь " + c.getUsername());
    }

    public synchronized void unsubscribe(ClientHandler c) {
        //удаление клиента из массива клиентов
        clients.remove(c);
        //рассылка широковещательного сообщения об удалившемся пользователе:
        broadcastMessage("Из чата вышел пользователь " + c.getUsername());
        //рассылка списка клиентов всем клиентам
        broadcastClientList();
        LOGGER.info("Из чата вышел пользователь " + c.getUsername());
    }

    public synchronized void broadcastMessage(String message) {
        //рассылка широковещательного сообщения
        for (ClientHandler c : clients) {
            c.sendMessage(message);
        }
    }

    public synchronized void broadcastClientList() {
        //рассылка списка клиентов всем клиентам
        StringBuilder builder = new StringBuilder(clients.size() * 10);
        builder.append("/clients_list ");
        for (ClientHandler c : clients) {
            builder.append(c.getUsername()).append(" ");
        }
        String clientsListStr = builder.toString();
        broadcastMessage(clientsListStr);
    }

    public synchronized boolean isUsernameUsed(String username) {
        //проверка: есть ли в массиве клиентов пользователь с указанным именем
        for (ClientHandler c : clients) {
            if (c.getUsername().equalsIgnoreCase(username)) {
                return true;
            }
        }
        return false;
    }

    public synchronized void sendPersonalMessage(ClientHandler sender, String receiverUsername, String message) {
        //если имя текущего клиента совпадает с указанным receiverUsername,
        if (sender.getUsername().equalsIgnoreCase(receiverUsername)) {
            //то шлем сообщение текущему клиенту:
            sender.sendMessage("Нельзя отправлять личные сообщения самому себе");
            return;
        }
        //в противном случае, проверяем есть ли в массиве клиентов пользователь с указанным именем receiverUsername
        for (ClientHandler c : clients) {
            if (c.getUsername().equalsIgnoreCase(receiverUsername)) {
                //и шлем ему сообщение  с указанием отправителя:
                c.sendMessage("от " + sender.getUsername() + ": " + message);
                //шлем сообщение текущему клиенту:
                sender.sendMessage("пользователю " + receiverUsername + ": " + message);
                return;
            }
        }
        //если пользователя нет в массиве клиентов, то шлем сообщение об этом текущему клиенту:
        sender.sendMessage("Пользователь " + receiverUsername + " не в сети");
    }

}
