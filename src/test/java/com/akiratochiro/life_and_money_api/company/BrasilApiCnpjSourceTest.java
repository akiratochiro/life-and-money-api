package com.akiratochiro.life_and_money_api.company;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** O cliente da BrasilAPI contra respostas simuladas: o formato real, CNPJ desconhecido e falhas. */
class BrasilApiCnpjSourceTest {

    private static final String BASE_URL = "https://brasilapi.test/api/cnpj/v1";

    private MockRestServiceServer server;
    private BrasilApiCnpjSource source;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        source = new BrasilApiCnpjSource(builder.build());
    }

    @Test
    void readsTheFieldsItUsesFromTheRealFormat() {
        // given: um trecho da resposta real (consultada em 11/11/2026), com uma sociedade limitada de dois sócios
        server.expect(requestTo(BASE_URL + "/11222333000181"))
                .andRespond(withSuccess("""
                        {"cnpj": "11222333000181", "razao_social": "ESTUDIO ANA E BRUNO LTDA ",
                         "nome_fantasia": "Estúdio AB", "data_inicio_atividade": "2013-10-03",
                         "codigo_natureza_juridica": 2062, "natureza_juridica": "Sociedade Empresária Limitada",
                         "opcao_pelo_simples": true, "opcao_pelo_mei": false,
                         "situacao_cadastral": 2, "descricao_situacao_cadastral": "ATIVA",
                         "uf": "SP", "cnae_fiscal": 7410202, "capital_social": 1000,
                         "qsa": [{"nome_socio": "ANA"}, {"nome_socio": "BRUNO"}], "ddd_fax": ""}
                        """, MediaType.APPLICATION_JSON));

        CnpjData data = source.find(Cnpj.parse("11222333000181")).orElseThrow();

        assertThat(data).isEqualTo(new CnpjData(
                "ESTUDIO ANA E BRUNO LTDA", "Estúdio AB", LocalDate.of(2013, 10, 3), LegalNature.LTDA));
        server.verify();
    }

    /** No formato real, o que falta vem como null ou texto vazio. */
    @Test
    void missingValuesBecomeNulls() {
        server.expect(requestTo(BASE_URL + "/11222333000181"))
                .andRespond(withSuccess("""
                        {"razao_social": "OPEN KNOWLEDGE BRASIL", "nome_fantasia": "", "data_inicio_atividade": null,
                         "codigo_natureza_juridica": 3999, "opcao_pelo_mei": null, "qsa": null}
                        """, MediaType.APPLICATION_JSON));

        assertThat(source.find(Cnpj.parse("11222333000181")))
                .contains(new CnpjData("OPEN KNOWLEDGE BRASIL", null, null, null));
    }

    @Test
    void anUnknownCnpjIsEmptyNotAnError() {
        server.expect(requestTo(BASE_URL + "/11222333000181")).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThat(source.find(Cnpj.parse("11222333000181"))).isEmpty();
    }

    @Test
    void aServerErrorOrAnUnreadableBodyBecomesALookupException() {
        server.expect(requestTo(BASE_URL + "/11222333000181")).andRespond(withStatus(HttpStatus.BAD_GATEWAY));
        assertThatThrownBy(() -> source.find(Cnpj.parse("11222333000181"))).isInstanceOf(CnpjLookupException.class);

        server.reset();
        server.expect(requestTo(BASE_URL + "/11222333000181"))
                .andRespond(withSuccess("{\"data_inicio_atividade\": \"03/10/2013\"}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> source.find(Cnpj.parse("11222333000181"))).isInstanceOf(CnpjLookupException.class);
    }

    @Test
    void aRateLimitFromTheBrasilApiIsALookupExceptionToo() {
        server.expect(requestTo(BASE_URL + "/11222333000181")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> source.find(Cnpj.parse("11222333000181"))).isInstanceOf(CnpjLookupException.class);
    }
}
