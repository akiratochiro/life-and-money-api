package com.akiratochiro.life_and_money_api.auth;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
class PasswordPolicyConfig {

    /**
     * Timeouts curtos, como os da BrasilAPI: o cadastro espera esta resposta, e uma Pwned Passwords
     * pendurada não pode segurar a tela pelo tempo padrão do cliente HTTP.
     */
    @Bean
    BreachedPasswords breachedPasswords(ObjectProvider<RestClient.Builder> builder,
                                        @Value("${app.pwned-passwords.base-url}") String baseUrl,
                                        @Value("${app.pwned-passwords.connect-timeout}") Duration connectTimeout,
                                        @Value("${app.pwned-passwords.read-timeout}") Duration readTimeout) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(connectTimeout).build());
        requestFactory.setReadTimeout(readTimeout);

        return new PwnedPasswordsClient(builder.getIfAvailable(RestClient::builder)
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build());
    }
}
