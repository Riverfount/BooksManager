package com.riverfount.booksmanager;

import org.springframework.boot.SpringApplication;

public class TestBooksManagerApplication {

	public static void main(String[] args) {
		SpringApplication.from(BooksManagerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
