package com.akiratochiro.life_and_money_api.invoice;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Lê o XML de uma NFS-e do padrão nacional (leiaute 1.00 e 1.01, conferido nos XSDs oficiais de
 * 09/02/2026). Função pura: recebe bytes, devolve {@link ParsedInvoice} ou lança
 * {@link InvoiceParseException} com a mensagem para o usuário.
 *
 * <p>Os caminhos, a partir da raiz {@code NFSe/infNFSe}:
 * <ul>
 *   <li>chave: atributo {@code Id} ("NFS" + 50 dígitos); número: {@code nNFSe};</li>
 *   <li>emissão: {@code DPS/infDPS/dhEmi}; competência: {@code DPS/infDPS/dCompet};</li>
 *   <li>serviço e desconto: {@code infDPS/valores/vServPrest/vServ} e {@code vDescCondIncond/vDescIncond};</li>
 *   <li>líquido: {@code infNFSe/valores/vLiq}, como emitido;</li>
 *   <li>ISS retido: {@code infNFSe/valores/vISSQN}, só quando {@code tribMun/tpRetISSQN} é 2 ou 3;</li>
 *   <li>retenções federais: {@code infDPS/valores/trib/tribFed}.</li>
 * </ul>
 *
 * <p>A assinatura digital não é verificada (decisão 20).
 */
public final class NfseXmlParser {

    static final String NAMESPACE = "http://www.sped.fazenda.gov.br/nfse";

    private static final Pattern ID = Pattern.compile("NFS([0-9]{50})");
    private static final Pattern KEY = Pattern.compile("[0-9]{50}");

    /** tpRetISSQN: 1 não retido, 2 retido pelo tomador, 3 retido pelo intermediário. */
    private static final Set<String> ISS_WITHHELD = Set.of("2", "3");

    /**
     * tpRetPisCofins, na tabela atual de 10 códigos. Os códigos das notas antigas (1 retido,
     * 2 não retido) coincidem com o 1 e o 2 de hoje.
     */
    private static final Set<String> PIS_WITHHELD = Set.of("1", "3", "4", "5", "9");
    private static final Set<String> COFINS_WITHHELD = Set.of("1", "3", "4", "6", "7");

    private NfseXmlParser() {
    }

    public static ParsedInvoice parse(byte[] xml) {
        Element root = read(xml).getDocumentElement();
        if (!"NFSe".equals(root.getLocalName()) || !NAMESPACE.equals(root.getNamespaceURI())) {
            throw new InvoiceParseException(
                    "Este XML não é uma NFS-e do padrão nacional. Confira se baixou o XML da nota de serviço.");
        }

        Element infNfse = required(root, "infNFSe");
        Element infDps = required(required(infNfse, "DPS"), "infDPS");
        Element dpsValues = required(infDps, "valores");
        Element nfseValues = required(infNfse, "valores");
        Element trib = child(dpsValues, "trib");
        Element tribFed = child(trib, "tribFed");
        Element pisCofins = child(tribFed, "piscofins");

        String retIss = text(child(trib, "tribMun"), "tpRetISSQN");
        String retPisCofins = text(pisCofins, "tpRetPisCofins");
        Element taker = child(infDps, "toma");

        return new ParsedInvoice(
                requiredText(infNfse, "nNFSe"),
                accessKey(infNfse),
                issueDate(requiredText(infDps, "dhEmi")),
                date(requiredText(infDps, "dCompet")),
                positive(requiredText(required(dpsValues, "vServPrest"), "vServ"), "valor do serviço"),
                amount(text(child(dpsValues, "vDescCondIncond"), "vDescIncond")),
                amount(requiredText(nfseValues, "vLiq")),
                ISS_WITHHELD.contains(retIss) ? amount(text(nfseValues, "vISSQN")) : BigDecimal.ZERO,
                amount(text(tribFed, "vRetIRRF")),
                PIS_WITHHELD.contains(retPisCofins) ? amount(text(pisCofins, "vPis")) : BigDecimal.ZERO,
                COFINS_WITHHELD.contains(retPisCofins) ? amount(text(pisCofins, "vCofins")) : BigDecimal.ZERO,
                amount(text(tribFed, "vRetCSLL")),
                amount(text(tribFed, "vRetCP")),
                text(taker, "xNome"),
                firstText(taker, "CNPJ", "CPF", "NIF"),
                firstText(child(infDps, "prest"), "CNPJ", "CPF"),
                optionalKey(text(child(infDps, "subst"), "chSubstda")),
                "2".equals(text(infDps, "tpAmb")));
    }

    /**
     * Fábrica endurecida: qualquer DOCTYPE é recusado, o que fecha de uma vez as entidades externas
     * (XXE, que leriam arquivos do servidor) e a expansão exponencial de entidades ("billion
     * laughs"). Uma NFS-e nunca tem DOCTYPE.
     */
    private static Document read(byte[] xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            // Sem isso, o parser imprime cada erro no stderr antes de lançar a exceção.
            builder.setErrorHandler(null);
            return builder.parse(new ByteArrayInputStream(xml));
        } catch (SAXException | IOException e) {
            throw new InvoiceParseException("Não foi possível ler este arquivo como XML.", e);
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("Parser XML sem as proteções exigidas", e);
        }
    }

    private static String accessKey(Element infNfse) {
        var matcher = ID.matcher(infNfse.getAttribute("Id"));
        if (!matcher.matches()) {
            throw new InvoiceParseException("A chave de acesso da nota está ausente ou fora do formato.");
        }
        return matcher.group(1);
    }

    private static String optionalKey(String value) {
        return value != null && KEY.matcher(value).matches() ? value : null;
    }

    /** O dia da emissão no fuso em que ela foi feita, que é o que aparece na nota. */
    private static LocalDate issueDate(String value) {
        try {
            return OffsetDateTime.parse(value).toLocalDate();
        } catch (DateTimeParseException e) {
            return date(value.length() >= 10 ? value.substring(0, 10) : value);
        }
    }

    private static LocalDate date(String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new InvoiceParseException("A nota tem uma data fora do formato esperado: " + value + ".", e);
        }
    }

    private static BigDecimal amount(String value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value).setScale(2, java.math.RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw new InvoiceParseException("A nota tem um valor fora do formato esperado: " + value + ".", e);
        }
    }

    private static BigDecimal positive(String value, String what) {
        BigDecimal amount = amount(value);
        if (amount.signum() <= 0) {
            throw new InvoiceParseException("O " + what + " da nota precisa ser maior que zero.");
        }
        return amount;
    }

    /** O primeiro filho com o nome, no namespace da NFS-e; nulo se não houver (ou se o pai for nulo). */
    private static Element child(Element parent, String name) {
        if (parent == null) {
            return null;
        }
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element
                    && name.equals(element.getLocalName())
                    && NAMESPACE.equals(element.getNamespaceURI())) {
                return element;
            }
        }
        return null;
    }

    private static Element required(Element parent, String name) {
        Element element = child(parent, name);
        if (element == null) {
            throw missing(name);
        }
        return element;
    }

    private static String text(Element parent, String name) {
        Element element = child(parent, name);
        if (element == null) {
            return null;
        }
        String value = element.getTextContent().strip();
        return value.isEmpty() ? null : value;
    }

    private static String requiredText(Element parent, String name) {
        String value = text(parent, name);
        if (value == null) {
            throw missing(name);
        }
        return value;
    }

    private static String firstText(Element parent, String... names) {
        for (String name : names) {
            String value = text(parent, name);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static InvoiceParseException missing(String element) {
        return new InvoiceParseException(
                "O XML da nota está incompleto: falta o campo " + element + ". Confira se é o XML da NFS-e autorizada.");
    }
}
