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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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
    public ResponseEntity<Resource> streamBook(@PathVariable Long id, HttpServletRequest request) throws IOException {
        Book book = bookRepository.findById(id).orElseThrow();
        Path file = storagePath.resolve(book.getFilename());
        InputStreamResource resource = new InputStreamResource(Files.newInputStream(file));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + book.getTitle() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(resource);
    }
}
