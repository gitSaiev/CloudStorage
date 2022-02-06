package com.saiev.common;

import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;

//класс для передачи файлов, при передачи файлов мы создаем новый файл по пути и передаем его
public class FileToSend implements Serializable {
    private String fileName;
    private String path;
    private byte[] data;
    private int size;


    public FileToSend(Path path) {
        this.path = path.toString();
        this.fileName = path.getFileName().toString();
        try {
            this.data = Files.readAllBytes(path);
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.size = data.length;
    }

    public String getFileName() {
        return fileName;
    }

    public byte[] getData() {
        return data;
    }

}
