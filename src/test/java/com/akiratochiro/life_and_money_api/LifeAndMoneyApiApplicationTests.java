package com.akiratochiro.life_and_money_api;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = {
        "jwt.secret=46N3Mn7ipRBtI+LDENQDLRM2mXOGfeamGMqAcgFS9Gc=",
        "app.email.provider=log",
        "app.front-url=http://localhost:5173",
        "app.client-ip.proxy-secret=segredo-dos-testes",
        // Os outros testes fazem dezenas de logins e cadastros do mesmo IP; os limites têm
        // os testes deles, num contexto à parte (RateLimitIntegrationTest).
        "app.rate-limit.enabled=false"
})
@Import(TestcontainersConfiguration.class)
class LifeAndMoneyApiApplicationTests {

	@Test
	void contextLoads() {
	}
}
