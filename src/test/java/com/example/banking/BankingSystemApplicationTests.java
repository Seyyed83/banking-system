package com.example.banking;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/** Needs a running Oracle. Opt in with -Doracle.integration=true. */
@SpringBootTest
@EnabledIfSystemProperty(named = "oracle.integration", matches = "true")
@TestPropertySource(properties = {"banking.security.username=test-admin", "banking.security.password=test-password"})
class BankingSystemApplicationTests {

	@Test
	void contextLoads() {
	}

}
