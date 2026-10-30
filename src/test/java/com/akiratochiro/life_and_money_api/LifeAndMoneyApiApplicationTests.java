package com.akiratochiro.life_and_money_api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = {
        "jwt.secret=46N3Mn7ipRBtI+LDENQDLRM2mXOGfeamGMqAcgFS9Gc=",
        "app.email.provider=log",
        "app.front-url=http://localhost:5173",
        "app.client-ip.proxy-secret=segredo-dos-testes"
})
@Import(TestcontainersConfiguration.class)
class LifeAndMoneyApiApplicationTests {

	@Test
	void contextLoads() {
	}
}
