package com.akiratochiro.life_and_money_api.holiday;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableCaching
class HolidayConfig {

    /**
     * Timeouts curtos e explícitos: sem eles, uma BrasilAPI pendurada seguraria a tela de contas
     * pelo tempo padrão do cliente HTTP, que é bem maior.
     */
    @Bean
    HolidaySource holidaySource(ObjectProvider<RestClient.Builder> builder,
                                @Value("${app.holidays.base-url}") String baseUrl,
                                @Value("${app.holidays.connect-timeout}") Duration connectTimeout,
                                @Value("${app.holidays.read-timeout}") Duration readTimeout) {
        return brasilApi(builder.getIfAvailable(RestClient::builder), baseUrl, connectTimeout, readTimeout);
    }

    /**
     * Timeouts curtos e explícitos: sem eles, uma BrasilAPI pendurada seguraria a tela de contas
     * pelo tempo padrão do cliente HTTP, que é bem maior. Separado do {@code @Bean} para o teste
     * do timeout montar o mesmo cliente contra um servidor lento.
     */
    static BrasilApiHolidaySource brasilApi(RestClient.Builder builder, String baseUrl,
                                            Duration connectTimeout, Duration readTimeout) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(connectTimeout).build());
        requestFactory.setReadTimeout(readTimeout);

        return new BrasilApiHolidaySource(builder
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build());
    }
}
