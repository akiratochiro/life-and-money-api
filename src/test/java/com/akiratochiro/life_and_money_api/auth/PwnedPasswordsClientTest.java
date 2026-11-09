package com.akiratochiro.life_and_money_api.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * O cliente da Pwned Passwords contra respostas simuladas. O SHA-1 de "password" é
 * 5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8: prefixo 5BAA6, final 1E4C9B93F3F0682250B6CF8331B7EE68FD8.
 */
class PwnedPasswordsClientTest {

    private static final String BASE_URL = "https://pwned.test";
    private static final String PASSWORD_SUFFIX = "1E4C9B93F3F0682250B6CF8331B7EE68FD8";

    private MockRestServiceServer server;
    private PwnedPasswordsClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new PwnedPasswordsClient(builder.build());
    }

    @Test
    void sendsOnlyTheFirstFiveCharactersOfTheHashWithPadding() {
        server.expect(requestTo(BASE_URL + "/range/5BAA6"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Add-Padding", "true"))
                .andRespond(withSuccess("0018A45C4D1DEF81644B54AB7F969B88D65:3\r\n", MediaType.TEXT_PLAIN));

        assertThat(client.isBreached("password")).isFalse();
        server.verify();
    }

    @Test
    void aSuffixInTheResponseWithACountMeansBreached() {
        server.expect(requestTo(BASE_URL + "/range/5BAA6"))
                .andRespond(withSuccess("""
                        0018A45C4D1DEF81644B54AB7F969B88D65:3\r
                        %s:52256179\r
                        """.formatted(PASSWORD_SUFFIX), MediaType.TEXT_PLAIN));

        assertThat(client.isBreached("password")).isTrue();
    }

    @Test
    void aPaddingLineWithCountZeroDoesNotCount() {
        server.expect(requestTo(BASE_URL + "/range/5BAA6"))
                .andRespond(withSuccess(PASSWORD_SUFFIX + ":0\r\n", MediaType.TEXT_PLAIN));

        assertThat(client.isBreached("password")).isFalse();
    }

    @Test
    void whenTheServiceFailsThePasswordIsAccepted() {
        server.expect(requestTo(BASE_URL + "/range/5BAA6")).andRespond(withServerError());

        assertThat(client.isBreached("password")).isFalse();
    }
}
