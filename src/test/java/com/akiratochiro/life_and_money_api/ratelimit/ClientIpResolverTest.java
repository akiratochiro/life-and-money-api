package com.akiratochiro.life_and_money_api.ratelimit;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClientIpResolverTest {

    private static final String SECRET = "segredo-do-worker";
    private final ClientIpResolver resolver = new ClientIpResolver(SECRET);

    private static MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.7");
        return request;
    }

    @Test
    void trustsTheIpForwardedByTheWorkerWhenTheSecretMatches() {
        MockHttpServletRequest request = request();
        request.addHeader("X-Proxy-Auth", SECRET);
        request.addHeader("X-Client-IP", "198.51.100.23");
        request.addHeader("CF-Connecting-IP", "2a06:98c0:3600::103");

        assertThat(resolver.resolve(request)).isEqualTo("198.51.100.23");
    }

    @Test
    void ignoresAForwardedIpWithoutTheSecret() {
        MockHttpServletRequest request = request();
        request.addHeader("X-Client-IP", "198.51.100.23");
        request.addHeader("CF-Connecting-IP", "203.0.113.9");

        assertThat(resolver.resolve(request)).isEqualTo("203.0.113.9");
    }

    @Test
    void ignoresAForwardedIpWithAWrongSecret() {
        MockHttpServletRequest request = request();
        request.addHeader("X-Proxy-Auth", "chute");
        request.addHeader("X-Client-IP", "198.51.100.23");

        assertThat(resolver.resolve(request)).isEqualTo("10.0.0.7");
    }

    @Test
    void neverTrustsXForwardedFor() {
        MockHttpServletRequest request = request();
        request.addHeader("X-Forwarded-For", "198.51.100.23");

        assertThat(resolver.resolve(request)).isEqualTo("10.0.0.7");
    }

    @Test
    void aValueThatIsNotAnIpIsDiscardedWithoutADnsLookup() {
        MockHttpServletRequest request = request();
        request.addHeader("X-Proxy-Auth", SECRET);
        request.addHeader("X-Client-IP", "atacante.example.com");

        assertThat(resolver.resolve(request)).isEqualTo("10.0.0.7");
    }

    @Test
    void groupsAnIpv6AddressByItsSlash64Prefix() {
        MockHttpServletRequest first = request();
        first.addHeader("CF-Connecting-IP", "2001:db8:abcd:12:1111:2222:3333:4444");
        MockHttpServletRequest second = request();
        second.addHeader("CF-Connecting-IP", "2001:db8:abcd:12:ffff::1");

        assertThat(resolver.resolve(first))
                .isEqualTo(resolver.resolve(second))
                .isEqualTo("2001:db8:abcd:12::/64");
    }

    @Test
    void refusesToStartWithoutTheProxySecret() {
        assertThatThrownBy(() -> new ClientIpResolver(" "))
                .isInstanceOf(IllegalStateException.class);
    }
}
