package com.jupudi.books.ebookstore.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.jupudi.books.ebookstore.model.Book;
import com.jupudi.books.ebookstore.repository.BookRepository;

@Service
public class BookService {

  //  @Autowired
  //  private BookRepository bookRepository; // Assuming you have a repository for database interactions

    // Save book metadata in the database
    public void saveBook(Book book) {
      //  bookRepository.save(book);
    }

    // Get all books from the database
   // public List<Book> getAllBooks() {
       // return bookRepository.findAll();
   // }
}
