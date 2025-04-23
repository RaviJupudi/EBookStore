package com.jupudi.books.ebookstore.repository;

import com.jupudi.books.ebookstore.model.Book;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BookRepository extends JpaRepository<Book, Long> {
    List<Book> findAllByOrderByUploadDateDesc();
    List<Book> findByCategoryOrderByUploadDateDesc(String category);
}