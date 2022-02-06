package com.saiev.server;

import com.saiev.common.FileToSend;
import com.saiev.server.auth.Singleton;
import com.saiev.common.FileInfo;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

//обработчик событий
public class ServerHandler extends ChannelInboundHandlerAdapter {
    static Logger LOGGER = LogManager.getLogger(Server.class);
    public static List<FileInfo> fileInfoServerList = new ArrayList<>();
    private Path pathRoot = Path.of("serverStorage");
    private Path currentPath = Path.of("serverStorage");
    PreparedStatement preparedStatement = null;

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        LOGGER.info("client connected: " + ctx.channel());
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        String message = String.valueOf(msg);
        if ("auth".equals(message.split(" ")[0])) {
            LOGGER.info("от клиента пришел запрос на авторизацию:" + message);
            try {
                preparedStatement = Singleton.getConnection().prepareStatement("SELECT * FROM users WHERE login = ?");
                preparedStatement.setString(1, message.split(" ")[1]);
                ResultSet resultSet = preparedStatement.executeQuery();
                while (resultSet.next()) {
                    String pass = resultSet.getString(3);
                    if (pass.equals(message.split(" ")[2])) {
                        LOGGER.info("Клиент успешно авторизовался");
                        ctx.writeAndFlush("authOK " + message.split(" ")[1]);
                        Path pathForUser = Path.of(pathRoot.toString(), message.split(" ")[1]);
                        currentPath = pathForUser;
                        if (!Files.exists(pathForUser)) {
                            Files.createDirectories(pathForUser);
                        }
                        updateFilesList(ctx, pathForUser);
                    } else {
                        ctx.writeAndFlush("WRONG");
                    }
                }

            } catch (SQLException | ClassNotFoundException throwables) {
                throwables.printStackTrace();
                LOGGER.log(Level.ERROR, throwables);
                LOGGER.log(Level.ERROR, throwables);
            }

        }
        //передача листа файлов при первом подключении к серверу
        if (message.contains("serverStorage")) {
            updateFilesList(ctx, pathRoot);
            pathRoot = currentPath;
        }
        //команда когда пользователь переходит во внутрь папок
        if ("updateFiles".equals(message.split(" ")[0])) {
            LOGGER.info("Пользователь перешел во внутреннюю директорию");
            currentPath = Path.of(currentPath.toString(), message.split(" ")[1]);
            updateFilesList(ctx, currentPath);
        }
        //команда для перехода в родительскую директорию
        if ("parentDirectory".equals(message)) {
            LOGGER.info("Пользователь перешел на уровень вверх");
            currentPath = currentPath.getParent();
            updateFilesList(ctx, currentPath);
        }
        //загрузка фалов на сервер с клиента
        if (msg instanceof FileToSend) {
            LOGGER.info("Пользователь  отправил файл");
            FileToSend file = (FileToSend) msg;
            Path path = Paths.get(currentPath + "/" + file.getFileName());
            try {
                if (Files.exists(path)) {
                    Files.write(path, file.getData(), StandardOpenOption.TRUNCATE_EXISTING);
                } else {
                    Files.write(path, file.getData(), StandardOpenOption.CREATE);
                }
                updateFilesList(ctx, path.getParent());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        //скачивание файла с сервера
        if ("download".equals(message.split(" ", 2)[0])) {
            LOGGER.info("Пользователь скачивает файл");
            ctx.writeAndFlush(new FileToSend(Path.of(currentPath.toString(), message.split(" ", 2)[1])));
        }
        //удаление файлов с сервера
        if ("delete".equals(message.split(" ")[0])) {
            LOGGER.info("пользователь удалил файл " + message.split(" ",2)[1]);
            Path pathForDelete = Path.of(currentPath.toString().concat("/").concat(message.split(" ", 2)[1]));
            try {
                Files.walkFileTree(pathForDelete, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                        Files.delete(dir);
                        return FileVisitResult.CONTINUE;
                    }
                });
                updateFilesList(ctx, currentPath);
            } catch (IOException ignored) {
            }
        }
        //создание новой директории
        if ("create".equals(message.split(" ")[0])) {
            LOGGER.info("Пользователь Создал новую директорию: " + message.split(" ",2)[1]);
            Path newPath = Path.of(currentPath.toString().concat("/").concat(message.split(" ", 2)[1]));
            Files.createDirectories(newPath);
            updateFilesList(ctx, currentPath);
        }
        //поиск файла
        if ("find".equals(message.split(" ")[0])) {
            LOGGER.info("Пользователь ищет файл: " + message.split(" ",2)[1]);
            Path rootPath = pathRoot;
            String fileToFind = File.separator + message.split(" ", 2)[1];
            try {
                Files.walkFileTree(rootPath, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        String fileString = file.toAbsolutePath().toString();
                        if (fileString.endsWith(fileToFind)) {
                            fileInfoServerList.clear();
                            fileInfoServerList.add(new FileInfo(file));
                            ctx.writeAndFlush(fileInfoServerList);
                            return FileVisitResult.TERMINATE;
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
        //переименование файла
        if ("rename".equals(message.split(" ")[0])) {
            LOGGER.info("Пользователь переименовал файл: " + message.split(" ")[1]);
            Path oldPath = Path.of(currentPath.toString(), message.split(" ")[1]);
            Path newPath = Path.of(currentPath.toString(), message.split(" ")[2]);
            Files.copy(oldPath, newPath);
            Files.delete(oldPath);
            updateFilesList(ctx, currentPath);
        }

    }

    //метод для передачи листа файлов клиенту
    private void updateFilesList(ChannelHandlerContext ctx, Path currentPath) throws IOException {
        fileInfoServerList.clear();
        fileInfoServerList.addAll(Files.list(currentPath)
                .map(FileInfo::new).collect(Collectors.toList()));
        ctx.writeAndFlush(fileInfoServerList);

    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        LOGGER.info("client disconnected: " + ctx.channel());
    }
}

