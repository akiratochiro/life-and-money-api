package com.akiratochiro.life_and_money_api.company;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
class CnpjConfig {

    /**
     * Timeouts curtos e explícitos, como nos feriados: o formulário espera esta resposta, e uma
     * BrasilAPI pendurada não pode segurar o usuário pelo tempo padrão do cliente HTTP.
     */
    @Bean
    CnpjLookup cnpjLookup(ObjectProvider<RestClient.Builder> builder,
                          @Value("${app.cnpj.base-url}") String baseUrl,
                          @Value("${app.cnpj.connect-timeout}") Duration connectTimeout,
                          @Value("${app.cnpj.read-timeout}") Duration readTimeout) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(connectTimeout).build());
        requestFactory.setReadTimeout(readTimeout);

        return new BrasilApiCnpjSource(builder.getIfAvailable(RestClient::builder)
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build());
    }
}
