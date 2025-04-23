package com.jupudi.books.ebookstore.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jupudi.books.ebookstore.model.Book;

public interface BookRepository extends JpaRepository<Book, Long> {
    List<Book> findAllByOrderByUploadDateDesc();
    List<Book> findByCategoryOrderByUploadDateDesc(String category);
    Optional<Book> findByPublicId(String publicId);
}