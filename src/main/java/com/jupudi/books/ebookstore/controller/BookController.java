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
   

    // Endpoint to view a book (read-only, redirect to Cloudinary URL)
    @GetMapping("/view/{publicId}")
    public ResponseEntity<?> viewBook(@PathVariable String publicId) {
        String url = cloudinary.url().secure(true).format("pdf").generate(publicId);
        return ResponseEntity.status(HttpStatus.FOUND)
                             .location(URI.create(url))
                             .build();
    }

    // Endpoint to download a book (forces download via Cloudinary URL)
    @GetMapping("/download/{publicId}")
    public ResponseEntity<?> downloadBook(@PathVariable String publicId) {
        String downloadUrl = cloudinary.url()
                                       .secure(true)
                                       .format("pdf")
                                       .transformation(new Transformation().flags("attachment"))
                                       .generate(publicId);

        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(downloadUrl)).build();
    }

    // Endpoint to delete a book from Cloudinary
    @DeleteMapping("/delete/{publicId}")
    public ResponseEntity<?> deleteBook(@PathVariable String publicId) {
        try {
            Map result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Delete failed: " + e.getMessage());
        }
    }
}
