package com.example.yingzaofashiapi.service;
import com.example.yingzaofashiapi.dao.BookRepository;
import com.example.yingzaofashiapi.entity.Book;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookService {
    private final BookRepository bookRepository;

    // 查询所有书籍
    public List<Book> getAllBooks() {
        return bookRepository.findAll();
    }
}