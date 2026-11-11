package com.akiratochiro.life_and_money_api.company;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;

/**
 * Dados públicos do CNPJ na BrasilAPI ({@code GET /api/cnpj/v1/{cnpj}}), que repassa a base aberta
 * da Receita. Gratuita e sem chave; não publica limite de uso.
 *
 * <p>Só um resultado encontrado entra no cache: um 404 pode virar resultado depois (empresa recém
 * aberta), e uma falha não pode ficar presa até o cache expirar, como nos feriados.
 */
class BrasilApiCnpjSource implements CnpjLookup {

    private final RestClient restClient;

    BrasilApiCnpjSource(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    @Cacheable(cacheNames = "cnpj", key = "#cnpj.value()", unless = "#result == null")
    public Optional<CnpjData> find(Cnpj cnpj) {
        try {
            BrasilApiCnpj body = restClient.get()
                    .uri("/{cnpj}", cnpj.value())
                    .retrieve()
                    .body(BrasilApiCnpj.class);
            if (body == null) {
                throw new CnpjLookupException("A BrasilAPI respondeu sem corpo para o CNPJ", null);
            }
            return Optional.of(body.toData());
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            throw new CnpjLookupException("A BrasilAPI recusou a consulta de CNPJ", e);
        } catch (RestClientException | DateTimeParseException e) {
            throw new CnpjLookupException("Não foi possível consultar o CNPJ na BrasilAPI", e);
        }
    }

    /** Só os campos usados; os outros (endereço, CNAEs, capital) são ignorados. */
    record BrasilApiCnpj(
            @JsonProperty("razao_social") String legalName,
            @JsonProperty("nome_fantasia") String tradeName,
            @JsonProperty("data_inicio_atividade") String openingDate,
            @JsonProperty("codigo_natureza_juridica") Integer legalNatureCode,
            @JsonProperty("opcao_pelo_mei") Boolean mei,
            @JsonProperty("qsa") List<Object> partners
    ) {
        CnpjData toData() {
            LocalDate opening = blank(openingDate) ? null : LocalDate.parse(openingDate);
            return new CnpjData(
                    blank(legalName) ? null : legalName.strip(),
                    blank(tradeName) ? null : tradeName.strip(),
                    opening,
                    ReceitaLegalNature.from(legalNatureCode, mei, partners == null ? null : partners.size()));
        }

        private static boolean blank(String value) {
            return value == null || value.isBlank();
        }
    }
}
