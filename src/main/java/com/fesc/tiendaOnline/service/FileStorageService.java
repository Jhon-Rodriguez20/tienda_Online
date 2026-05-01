package com.fesc.tiendaOnline.service;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;

@Service
public class FileStorageService {

    @Value("${file.upload-dir}")
    private String uploadDir;
    
    private Path fileStorageLocation;

    @PostConstruct
    public void init() {
        this.fileStorageLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        
        try {
            java.nio.file.Files.createDirectories(this.fileStorageLocation);
        
        } catch (Exception ex) {
            throw new RuntimeException("No se pudo crear el directorio de uploads", ex);
        }
    }

    public String storageFile(MultipartFile file) {
        String originalFileName = file.getOriginalFilename();
        String FileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
        String fileName = UUID.randomUUID().toString() + FileExtension;

        try {
            Path targetLocation = this.fileStorageLocation.resolve(fileName);
            java.nio.file.Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            return "/uploads/" + fileName;
        
        } catch (IOException ex) {
            throw new RuntimeException("No se pudo almacenar el archivo ", ex);
        }
    }

    public void deleteFile(String fileUrl) {
        try {
            String fileName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
            Path filePath = this.fileStorageLocation.resolve(fileName);
            java.nio.file.Files.deleteIfExists(filePath);

        } catch (IOException ex) {
            System.err.println("No se pudo eliminar el archivo " + ex.getMessage());
        }
    }
}
