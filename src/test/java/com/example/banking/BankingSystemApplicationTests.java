package com.example.banking;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;

/** Needs a running Oracle. Opt in with -Doracle.integration=true. */
@SpringBootTest
@EnabledIfSystemProperty(named = "oracle.integration", matches = "true")
class BankingSystemApplicationTests {

	@Test
	void contextLoads() {
	}

}
