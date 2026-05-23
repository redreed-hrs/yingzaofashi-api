package com.example.yingzaofashiapi.dao;
import com.example.yingzaofashiapi.entity.Book;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookRepository extends JpaRepository<Book, Long> {
}