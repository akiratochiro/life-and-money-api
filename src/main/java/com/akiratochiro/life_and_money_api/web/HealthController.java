package com.akiratochiro.life_and_money_api.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * Endpoint que o serviço de hospedagem consulta para saber se a aplicação subiu.
 *
 * <p>Não consulta o banco de propósito. O Flyway roda na inicialização e falha se o banco não
 * estiver acessível, então uma resposta aqui já significa que a aplicação subiu com o banco no
 * ar. Um endpoint simples também não revela nada sobre a infraestrutura, diferente do que um
 * {@code /actuator/health} detalhado faria.
 */
@RestController
@SecurityRequirements
public class HealthController {

    @GetMapping("/health")
    @Operation(summary = "Diz se a API está no ar")
    public Health health() {
        return new Health("UP");
    }

    public record Health(@Schema(requiredMode = REQUIRED, example = "UP") String status) {
    }
}
