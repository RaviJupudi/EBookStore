package com.jupudi.books.ebookstore.controller;

import java.io.IOException;
import java.net.URI;
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

@RestController
@RequestMapping("/api/books")
public class BookController {

    @Autowired
    private Cloudinary cloudinary;

    // Endpoint to upload book
    @PostMapping("/upload")
    public ResponseEntity<?> uploadBook(@RequestParam("file") MultipartFile file) {
        try {
            Map uploadResult = cloudinary.uploader().upload(file.getInputStream(), ObjectUtils.asMap("resource_type", "auto"));
            String publicId = (String) uploadResult.get("public_id");
            String secureUrl = (String) uploadResult.get("secure_url");

            // Store the publicId and secureUrl in your database if needed for later retrieval (optional)

            return ResponseEntity.status(HttpStatus.CREATED).body("Uploaded Successfully. URL: " + secureUrl);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Upload failed: " + e.getMessage());
        }
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
