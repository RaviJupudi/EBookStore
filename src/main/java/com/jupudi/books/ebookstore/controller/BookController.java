package com.jupudi.books.ebookstore.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.jupudi.books.ebookstore.model.Book;
import com.jupudi.books.ebookstore.repository.BookRepository;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/books")
@RequiredArgsConstructor
public class BookController {

	@Autowired
    private BookRepository bookRepository;

    private final Path storagePath = Paths.get("uploads");

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(storagePath);
    }

    @PostMapping
    public ResponseEntity<String> uploadBook(@RequestParam MultipartFile file,
                                             @RequestParam String title,
                                             @RequestParam String category) {
        try {
            // Use the /tmp directory for Render, it's the only writable folder in free tier
            Path storagePath = Paths.get(System.getProperty("java.io.tmpdir"), "uploads");

            // Ensure the uploads folder exists
            Files.createDirectories(storagePath);

            // Generate a safe and unique filename
            String filename = System.currentTimeMillis() + "_" + StringUtils.cleanPath(file.getOriginalFilename());
            Path filePath = storagePath.resolve(filename);

            // Save the uploaded file
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            // Create and save book metadata
            Book book = new Book();
            book.setTitle(title);
            book.setCategory(category);
            book.setFilename(filename);
            book.setUploadDate(LocalDateTime.now());
            bookRepository.save(book);

            return ResponseEntity.ok("Uploaded successfully");
        } catch (IOException e) {
            e.printStackTrace(); // This will show up in Render logs
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                 .body("Upload failed: " + e.getMessage());
        }
    }


    @GetMapping
    public List<Book> listBooks(@RequestParam(required = false) String category) {
        if (category != null && !category.isEmpty()) {
            return bookRepository.findByCategoryOrderByUploadDateDesc(category);
        }
        return bookRepository.findAllByOrderByUploadDateDesc();
    }

    @GetMapping("/{id}/stream")
    public ResponseEntity<Resource> streamBook(@PathVariable Long id, HttpServletRequest request) {
        System.out.println("📚 [StreamBook] Requested ID: " + id);

        Book book = bookRepository.findById(id)
                .orElseThrow(() -> {
                    System.out.println("❌ Book not found for ID: " + id);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found");
                });

        Path file = storagePath.resolve(book.getFilename()).normalize();
        System.out.println("📁 Resolved file path: " + file.toAbsolutePath());

        if (!Files.exists(file)) {
            System.out.println("❌ File not found on server: " + file.toAbsolutePath());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File not found on server");
        }

        try {
            InputStreamResource resource = new InputStreamResource(Files.newInputStream(file));
            System.out.println("✅ File found, streaming PDF...");

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + book.getTitle() + ".pdf\"")
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .header(HttpHeaders.EXPIRES, "0")
                    .body(resource);

        } catch (IOException e) {
            System.out.println("💥 Error reading file: " + e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error reading file", e);
        }
    }

    
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteBook(@PathVariable Long id) {
        Book book = bookRepository.findById(id).orElseThrow(() -> new RuntimeException("Book not found"));

        // Delete the physical file
        Path file = storagePath.resolve(book.getFilename());
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to delete file: " + e.getMessage());
        }

        // Delete the DB record
        bookRepository.deleteById(id);
        return ResponseEntity.ok("Book deleted successfully");
    }


}
