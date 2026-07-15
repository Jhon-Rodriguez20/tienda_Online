package com.fesc.tiendaOnline.service;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.fesc.tiendaOnline.exception.BusinessRuleException;

import jakarta.annotation.PostConstruct;

@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
        ".jpg", ".jpeg", ".png", ".webp", ".gif"
    );

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB

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
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleException("El archivo es obligatorio");
        }

        // Validar tamaño
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessRuleException("El archivo excede el tamaño máximo permitido (10MB)");
        }

        // Validar content type
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new BusinessRuleException("Tipo de archivo no permitido. Solo se aceptan imágenes (JPG, PNG, WebP, GIF)");
        }

        // Validar extensión
        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || !originalFileName.contains(".")) {
            throw new BusinessRuleException("El archivo debe tener una extensión válida");
        }

        String fileExtension = originalFileName.substring(originalFileName.lastIndexOf(".")).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(fileExtension)) {
            throw new BusinessRuleException("Extensión de archivo no permitida. Solo se aceptan: JPG, JPEG, PNG, WebP, GIF");
        }

        String fileName = UUID.randomUUID().toString() + fileExtension;

        try {
            Path targetLocation = this.fileStorageLocation.resolve(fileName).normalize();
            
            // Prevenir path traversal
            if (!targetLocation.startsWith(this.fileStorageLocation)) {
                throw new BusinessRuleException("Ruta de archivo inválida");
            }
            
            java.nio.file.Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
            return "/uploads/" + fileName;
        
        } catch (IOException ex) {
            throw new RuntimeException("No se pudo almacenar el archivo", ex);
        }
    }

    public void deleteFile(String fileUrl) {
        try {
            if (fileUrl == null || fileUrl.isBlank()) return;
            String fileName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
            Path filePath = this.fileStorageLocation.resolve(fileName).normalize();
            
            // Prevenir path traversal en eliminación
            if (!filePath.startsWith(this.fileStorageLocation)) return;
            
            java.nio.file.Files.deleteIfExists(filePath);

        } catch (IOException ex) {
            // Log pero no lanzar excepción — eliminación es best-effort
        }
    }
}
