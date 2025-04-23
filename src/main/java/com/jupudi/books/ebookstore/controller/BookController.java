package com.jupudi.books.ebookstore.controller;

import com.jupudi.books.ebookstore.model.Book;
import com.jupudi.books.ebookstore.repository.BookRepository;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/books")
@RequiredArgsConstructor
public class BookController {

    private final BookRepository bookRepository = null;

    private final Path storagePath = Paths.get("uploads");

    @PostConstruct
    public void init() throws IOException {
        Files.createDirectories(storagePath);
    }

    @PostMapping
    public ResponseEntity<String> uploadBook(@RequestParam MultipartFile file,
                                             @RequestParam String title,
                                             @RequestParam String category) throws IOException {
        String filename = System.currentTimeMillis() + "_" + StringUtils.cleanPath(file.getOriginalFilename());
        Path filePath = storagePath.resolve(filename);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        Book book = new Book();
        book.setTitle(title);
        book.setCategory(category);
        book.setFilename(filename);
        book.setUploadDate(LocalDateTime.now());
        bookRepository.save(book);

        return ResponseEntity.ok("Uploaded successfully");
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
