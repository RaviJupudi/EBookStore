package com.jupudi.books.ebookstore.controller;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.cloudinary.utils.ObjectUtils;
import com.jupudi.books.ebookstore.model.Book;
import com.jupudi.books.ebookstore.repository.BookRepository;
import com.jupudi.books.ebookstore.service.BookService;

@RestController
@RequestMapping("/api/books")
public class BookController {

    @Autowired
    private Cloudinary cloudinary;
    
    @Autowired
    private BookRepository bookRepository; 
    
    @Autowired
    private BookService bookService;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadBook(
        @RequestParam("file") MultipartFile file,
        @RequestParam("title") String title,
        @RequestParam("category") String category) {
        
        try {
            // 1. Upload to Cloudinary
            Map uploadResult = cloudinary.uploader().upload(
                file.getBytes(), 
                ObjectUtils.asMap("resource_type", "auto")
            );
            
            // 2. Save metadata to database
            Book book = new Book();
            book.setTitle(title);
            book.setCategory(category);
            book.setPublicId((String) uploadResult.get("public_id"));
            book.setUrl((String) uploadResult.get("secure_url"));
            book.setUploadDate(LocalDateTime.now());
            
            Book savedBook = bookRepository.save(book);
            
            // 3. Return response
            return ResponseEntity.status(HttpStatus.CREATED).body(savedBook);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Upload failed", "message", e.getMessage()));
        }
    }
    
    @GetMapping
    public ResponseEntity<List<Book>> getAllBooks() {
        return ResponseEntity.ok(bookRepository.findAll());
    }
   

    @GetMapping("/view/{publicId}")
    public ResponseEntity<?> viewBook(@PathVariable String publicId) {
        try {
            // 1. Verify book exists in database
            Book book = bookRepository.findByPublicId(publicId)
                .orElseThrow(() -> new RuntimeException("Book not found"));
            
            // 2. Generate direct Cloudinary URL
            String viewUrl = cloudinary.url()
                .secure(true)
                .resourceType("auto")  // Critical for non-image files
                .format("pdf")        // Match your file type
                .generate(publicId);
            
            // 3. Return 302 redirect
            return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(viewUrl))
                .build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of(
                    "error", "View failed",
                    "message", e.getMessage(),
                    "timestamp", LocalDateTime.now()
                ));
        }
    }

    @GetMapping("/download/{publicId}")
    public ResponseEntity<?> downloadBook(@PathVariable String publicId) {
        try {
            // 1. Verify book exists
            bookRepository.findByPublicId(publicId)
                .orElseThrow(() -> new RuntimeException("Book not found"));
            
            // 2. Generate download URL with forced attachment
            String downloadUrl = cloudinary.url()
                .secure(true)
                .resourceType("auto")
                .transformation(new Transformation()
                    .flags("attachment")  // Force download
                    .quality("auto")     // Optional quality setting
                )
                .generate(publicId);

            // 3. Return 302 redirect
            return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(downloadUrl))
                .build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "error", "Download failed",
                    "message", e.getMessage(),
                    "timestamp", LocalDateTime.now()
                ));
        }
    }

    @DeleteMapping("/delete/{publicId}")
    public ResponseEntity<?> deleteBook(@PathVariable String publicId) {
        try {
            // First delete from database
            Book book = bookRepository.findByPublicId(publicId)
                .orElseThrow(() -> new RuntimeException("Book not found"));
            
            bookRepository.delete(book);
            
            // Then delete from Cloudinary
            Map result = cloudinary.uploader()
                .destroy(publicId, ObjectUtils.emptyMap());
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Delete failed: " + e.getMessage());
        }
    }
}
