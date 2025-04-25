package com.example.bank_cards;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class BankCardsApplication {

	public static void main(String[] args) {
		SpringApplication.run(BankCardsApplication.class, args);
	}

}
