package com.jupudi.books.ebookstore.controller;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.jupudi.books.ebookstore.model.Book;
import com.jupudi.books.ebookstore.repository.BookRepository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/books")
@RequiredArgsConstructor
public class BookController {

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private Cloudinary cloudinary;

    @PostConstruct
    public void init() {
        // Initialize Cloudinary configuration if needed
    }

    @PostMapping
    public ResponseEntity<String> uploadBook(@RequestParam MultipartFile file,
                                             @RequestParam String title,
                                             @RequestParam String category) {
        try {
            // Generate a safe and unique filename
            String filename = System.currentTimeMillis() + "_" + StringUtils.cleanPath(file.getOriginalFilename());

            // Upload file to Cloudinary
            var uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                "public_id", filename,
                "resource_type", "auto"
            ));

            // Get the URL of the uploaded file
            String fileUrl = (String) uploadResult.get("secure_url");

            // Create and save book metadata
            Book book = new Book();
            book.setTitle(title);
            book.setCategory(category);
            book.setFilename(filename);
            book.setFileUrl(fileUrl); // Store the Cloudinary URL
            book.setUploadDate(LocalDateTime.now());
            bookRepository.save(book);

            return ResponseEntity.ok("Uploaded successfully");

        } catch (IOException e) {
            e.printStackTrace();
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
    public ResponseEntity<byte[]> streamBook(@PathVariable Long id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found"));

        String fileUrl = book.getFileUrl();

        if (fileUrl == null || fileUrl.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File URL not found");
        }

        try {
            // Download the file from Cloudinary
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<byte[]> response = restTemplate.getForEntity(fileUrl, byte[].class);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + book.getTitle() + ".pdf\"")
                    .body(response.getBody());

        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to stream file", e);
        }
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteBook(@PathVariable Long id) {
        Book book = bookRepository.findById(id).orElseThrow(() -> new RuntimeException("Book not found"));

        // Delete the book file from Cloudinary
        try {
            cloudinary.uploader().destroy(book.getFilename(), ObjectUtils.emptyMap());
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to delete file: " + e.getMessage());
        }

        // Delete the DB record
        bookRepository.deleteById(id);
        return ResponseEntity.ok("Book deleted successfully");
    }
}
