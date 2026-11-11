package com.akiratochiro.life_and_money_api.invoice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** O parser contra uma NFS-e construída a partir do XSD 1.01 e variações dela. */
class NfseXmlParserTest {

    /** A fixture como texto, para cada teste trocar só o trecho que importa. */
    static String fixture() {
        try (InputStream in = NfseXmlParserTest.class.getResourceAsStream("/nfse/nfse-v101.xml")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ParsedInvoice parse(String xml) {
        return NfseXmlParser.parse(xml.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void readsEveryFieldFromItsOfficialPath() {
        ParsedInvoice invoice = parse(fixture());

        assertThat(invoice.number()).isEqualTo("42");
        assertThat(invoice.accessKey()).isEqualTo("35503082212345678000195000000000004226111234567890");
        assertThat(invoice.competenceDate()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(invoice.serviceAmount()).isEqualByComparingTo("10000.00");
        assertThat(invoice.unconditionalDiscountAmount()).isEqualByComparingTo("0");
        assertThat(invoice.netAmount()).isEqualByComparingTo("8450.00");
        assertThat(invoice.issWithheldAmount()).isEqualByComparingTo("200.00");
        assertThat(invoice.irrfWithheldAmount()).isEqualByComparingTo("150.00");
        assertThat(invoice.csllWithheldAmount()).isEqualByComparingTo("100.00");
        assertThat(invoice.inssWithheldAmount()).isEqualByComparingTo("1100.00");
        assertThat(invoice.takerName()).isEqualTo("CLIENTE EXEMPLO S.A.");
        assertThat(invoice.takerDocument()).isEqualTo("98765432000198");
        assertThat(invoice.providerDocument()).isEqualTo("11222333000181");
        assertThat(invoice.replacedAccessKey()).isNull();
        assertThat(invoice.homologation()).isFalse();
    }

    /** dhEmi às 23h32 de Brasília: em UTC já seria dia 11, mas a nota diz dia 10. */
    @Test
    void theIssueDateIsTheDayInTheTimezoneOfTheNote() {
        assertThat(parse(fixture()).issueDate()).isEqualTo(LocalDate.of(2026, 11, 10));
    }

    @ParameterizedTest
    @CsvSource({"1, 0.00", "2, 200.00", "3, 200.00"})
    void theIssIsWithheldOnlyWhenTheTakerOrTheIntermediaryWithheldIt(String code, String expected) {
        ParsedInvoice invoice = parse(fixture().replace("<tpRetISSQN>2</tpRetISSQN>", "<tpRetISSQN>" + code + "</tpRetISSQN>"));

        assertThat(invoice.issWithheldAmount()).isEqualByComparingTo(expected);
    }

    /** A tabela atual de tpRetPisCofins; 1 e 2 também são os códigos das notas antigas. */
    @ParameterizedTest
    @CsvSource({
            "0, 0.00, 0.00", "1, 65.00, 300.00", "2, 0.00, 0.00", "3, 65.00, 300.00", "4, 65.00, 300.00",
            "5, 65.00, 0.00", "6, 0.00, 300.00", "7, 0.00, 300.00", "8, 0.00, 0.00", "9, 65.00, 0.00"
    })
    void pisAndCofinsCountAsWithheldOnlyWhenTheCodeSaysSo(String code, String pis, String cofins) {
        ParsedInvoice invoice = parse(fixture().replace("<tpRetPisCofins>2</tpRetPisCofins>",
                "<tpRetPisCofins>" + code + "</tpRetPisCofins>"));

        assertThat(invoice.pisWithheldAmount()).isEqualByComparingTo(pis);
        assertThat(invoice.cofinsWithheldAmount()).isEqualByComparingTo(cofins);
    }

    @Test
    void anUnconditionalDiscountIsRead() {
        ParsedInvoice invoice = parse(fixture().replace("<vServPrest><vServ>10000.00</vServ></vServPrest>",
                "<vServPrest><vServ>10000.00</vServ></vServPrest><vDescCondIncond><vDescIncond>500</vDescIncond></vDescCondIncond>"));

        assertThat(invoice.unconditionalDiscountAmount()).isEqualByComparingTo("500.00");
    }

    @Test
    void theTakerMayBeAPersonAForeignerOrAbsent() {
        String cpf = fixture().replace("<CNPJ>98765432000198</CNPJ>", "<CPF>12345678909</CPF>");
        String nif = fixture().replace("<CNPJ>98765432000198</CNPJ>", "<NIF>DE123456789</NIF>");
        String none = fixture().replaceAll("(?s)<toma>.*?</toma>", "");

        assertThat(parse(cpf).takerDocument()).isEqualTo("12345678909");
        assertThat(parse(nif).takerDocument()).isEqualTo("DE123456789");
        assertThat(parse(none).takerName()).isNull();
        assertThat(parse(none).takerDocument()).isNull();
    }

    @Test
    void aReplacementPointsToTheNoteItReplaces() {
        String key = "35503082212345678000195000000000004126111234567891";
        ParsedInvoice invoice = parse(fixture().replace("<tpEmit>1</tpEmit>",
                "<tpEmit>1</tpEmit><subst><chSubstda>" + key + "</chSubstda><cMotivo>01</cMotivo></subst>"));

        assertThat(invoice.replacedAccessKey()).isEqualTo(key);
    }

    @Test
    void aHomologationNoteIsFlagged() {
        assertThat(parse(fixture().replace("<tpAmb>1</tpAmb>", "<tpAmb>2</tpAmb>")).homologation()).isTrue();
    }

    @Test
    void version100IsReadTheSameWay() {
        ParsedInvoice invoice = parse(fixture().replace("versao=\"1.01\"", "versao=\"1.00\""));

        assertThat(invoice.netAmount()).isEqualByComparingTo("8450.00");
    }

    /** O namespace com prefixo é o mesmo namespace: o parser procura por namespace e nome local. */
    @Test
    void aPrefixedNamespaceIsAccepted() {
        String prefixed = fixture()
                .replace("xmlns=\"http://www.sped.fazenda.gov.br/nfse\"", "xmlns:n=\"http://www.sped.fazenda.gov.br/nfse\"")
                .replaceAll("<(/?)(?!\\?|!|Signature|SignedInfo|SignatureValue)([A-Za-z]+)", "<$1n:$2");

        assertThat(parse(prefixed).number()).isEqualTo("42");
    }

    @Test
    void anotherKindOfXmlIsRejectedWithAClearMessage() {
        String nfe = "<nfeProc xmlns=\"http://www.portalfiscal.inf.br/nfe\"><NFe/></nfeProc>";

        assertThatThrownBy(() -> parse(nfe))
                .isInstanceOf(InvoiceParseException.class)
                .hasMessage("Este XML não é uma NFS-e do padrão nacional. Confira se baixou o XML da nota de serviço.");
    }

    @Test
    void aMalformedFileIsRejected() {
        assertThatThrownBy(() -> parse("<NFSe><infNFSe>"))
                .isInstanceOf(InvoiceParseException.class)
                .hasMessage("Não foi possível ler este arquivo como XML.");
    }

    @Test
    void aMissingRequiredFieldIsNamed() {
        assertThatThrownBy(() -> parse(fixture().replace("<dCompet>2026-10-31</dCompet>", "")))
                .isInstanceOf(InvoiceParseException.class)
                .hasMessageContaining("falta o campo dCompet");
    }

    @Test
    void aMalformedAccessKeyIsRejected() {
        assertThatThrownBy(() -> parse(fixture().replace("Id=\"NFS3550", "Id=\"NFX3550")))
                .isInstanceOf(InvoiceParseException.class)
                .hasMessage("A chave de acesso da nota está ausente ou fora do formato.");
    }

    /**
     * XXE: uma entidade externa apontando para um arquivo do servidor. O DOCTYPE é recusado antes de
     * qualquer leitura, então o conteúdo do arquivo nunca aparece em lugar nenhum.
     */
    @Test
    void aDoctypeWithAnExternalEntityIsRejectedWithoutReadingTheFile(@TempDir Path dir) throws IOException {
        Path secret = Files.writeString(dir.resolve("segredo.txt"), "SEGREDO-DO-SERVIDOR");
        String attack = fixture()
                .replace("<?xml version=\"1.0\" encoding=\"UTF-8\"?>",
                        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><!DOCTYPE NFSe [<!ENTITY x SYSTEM \"" + secret.toUri() + "\">]>")
                .replace("<nNFSe>42</nNFSe>", "<nNFSe>&x;</nNFSe>");

        assertThatThrownBy(() -> parse(attack))
                .isInstanceOf(InvoiceParseException.class)
                .hasMessage("Não foi possível ler este arquivo como XML.")
                .satisfies(e -> assertThat(e.toString()).doesNotContain("SEGREDO-DO-SERVIDOR"));
    }

    /** "Billion laughs": entidades que se expandem exponencialmente. Recusado pelo mesmo motivo. */
    @Test
    void anEntityExpansionBombIsRejected() {
        String bomb = "<?xml version=\"1.0\"?><!DOCTYPE l [<!ENTITY a \"aaaaaaaaaa\"><!ENTITY b \"&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;\">]><NFSe>&b;</NFSe>";

        assertThatThrownBy(() -> parse(bomb)).isInstanceOf(InvoiceParseException.class);
    }

    @Test
    void valuesAreKeptWithTwoDecimals() {
        ParsedInvoice invoice = parse(fixture().replace("<vLiq>8450.00</vLiq>", "<vLiq>8450</vLiq>"));

        assertThat(invoice.netAmount()).isEqualTo(new BigDecimal("8450.00"));
    }
}
