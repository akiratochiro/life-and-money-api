package com.akiratochiro.life_and_money_api.tax;

import com.akiratochiro.life_and_money_api.taxengine.Money;
import com.akiratochiro.life_and_money_api.taxengine.RevenueLimits;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * As frases dos limites (decisão 32), em linguagem simples e montadas com os números do ano. Ficam fora
 * do motor, que só devolve números e níveis.
 */
final class RevenueLimitMessages {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", Locale.forLanguageTag("pt-BR"));

    static final String NOTICE = "Conta só as notas lançadas no app, pela competência. Estimativa do app: confirme com o seu contador.";
    static final String CONDITIONAL = "Os limites do MEI de 2027 em diante (LC 238/2026) dependem de a renúncia de receita constar "
            + "do orçamento federal (art. 3º). Se não constar, vale o limite de R$ 81.000.";

    private RevenueLimitMessages() {
    }

    static String label(RevenueLimits.Kind kind) {
        return switch (kind) {
            case MEI_ANNUAL -> "Limite do MEI";
            case SIMPLES_EPP -> "ME → EPP";
            case SIMPLES_SUBLIMIT -> "Sublimite do ISS";
            case SIMPLES_CEILING -> "Teto do Simples";
        };
    }

    static String source(RevenueLimits.Kind kind) {
        return switch (kind) {
            case MEI_ANNUAL -> "LC 123/2006, art. 18-A, §§1º, 2º e 7º";
            case SIMPLES_EPP -> "LC 123/2006, art. 3º, I e §7º";
            case SIMPLES_SUBLIMIT -> "LC 123/2006, art. 19, §4º, e art. 20, §§1º e 1º-A";
            case SIMPLES_CEILING -> "LC 123/2006, art. 3º, II e §§9º, 9º-A, 10 e 12";
        };
    }

    static RevenueLimitsResponse.Severity severity(RevenueLimits.Limit limit) {
        return switch (limit.level()) {
            case NONE -> null;
            case WARNING, NEAR -> RevenueLimitsResponse.Severity.WARNING;
            // Virar EPP não muda imposto no Simples: é um aviso, não um problema.
            case EXCEEDED, EXCEEDED_TOLERANCE -> limit.kind() == RevenueLimits.Kind.SIMPLES_EPP
                    ? RevenueLimitsResponse.Severity.INFO : RevenueLimitsResponse.Severity.DANGER;
        };
    }

    /** O nome com artigo, para "faltam R$ X para ...". */
    private static String target(RevenueLimits.Kind kind) {
        return switch (kind) {
            case MEI_ANNUAL -> "o limite do MEI";
            case SIMPLES_EPP -> "a empresa passar de ME a EPP";
            case SIMPLES_SUBLIMIT -> "o sublimite do ISS";
            case SIMPLES_CEILING -> "o teto do Simples";
        };
    }

    private static String consequence(RevenueLimits.Kind kind) {
        return switch (kind) {
            case MEI_ANNUAL -> "Acima dele, a empresa deixa de ser MEI.";
            case SIMPLES_EPP -> "Acima dele, a empresa vira EPP no ano seguinte; no Simples, os impostos não mudam por isso.";
            case SIMPLES_SUBLIMIT -> "Acima dele, o ISS sai do DAS e passa a ser pago à prefeitura.";
            case SIMPLES_CEILING -> "Acima dele, a empresa sai do Simples Nacional.";
        };
    }

    static String message(RevenueLimits.Limit limit, int year, BigDecimal revenue, YearMonth start) {
        String amount = Money.brl(limit.amount());
        return switch (limit.level()) {
            case NONE -> null;
            case WARNING -> "Você já faturou " + percent(limit.ratio()) + " d" + (limit.kind() == RevenueLimits.Kind.SIMPLES_EPP
                    ? "o limite da microempresa" : target(limit.kind())) + " em " + year + " (" + Money.brl(revenue)
                    + " de " + amount + "). " + consequence(limit.kind());
            case NEAR -> "Faltam " + Money.brl(limit.amount().subtract(revenue).max(BigDecimal.ZERO)) + " para " + target(limit.kind())
                    + " (" + amount + "). Uma nota a mais pode passar. " + consequence(limit.kind());
            case EXCEEDED, EXCEEDED_TOLERANCE -> effect(limit, year, start);
        };
    }

    private static String effect(RevenueLimits.Limit limit, int year, YearMonth start) {
        String month = month(limit.exceededIn());
        String beyondMonth = limit.toleranceExceededIn() == null ? null : month(limit.toleranceExceededIn());
        String tolerance = limit.toleranceAmount() == null ? null : Money.brl(limit.toleranceAmount());
        String from = limit.effectiveFrom() == null ? null : month(limit.effectiveFrom());
        int next = year + 1;
        return switch (limit.effect()) {
            case MEI_LEAVES_NEXT_YEAR -> "A receita de " + year + " passou do limite do MEI em " + month + ", mas dentro da tolerância "
                    + "de 20%. Em 1º de janeiro de " + next + ", a empresa deixa de ser MEI e passa ao Simples como microempresa, e há "
                    + "um DAS complementar sobre o que passou do limite. Combine a mudança com o seu contador.";
            case MEI_RETROACTIVE -> "A receita de " + year + " passou mais de 20% do limite do MEI (" + tolerance + ") em " + beyondMonth
                    + ". O desenquadramento vale desde " + (limit.effectiveFrom().equals(start) && start.getYear() == year
                    ? "a abertura, em " + from : "janeiro de " + year) + ", e os impostos desse período passam a ser os do Simples, "
                    + "com juros. Procure o seu contador agora.";
            case BECOMES_EPP_NEXT_YEAR -> "A receita de " + year + " passou de " + Money.brl(limit.amount()) + " em " + month
                    + ": a partir de janeiro de " + next + ", a empresa é EPP. No Simples, os impostos não mudam por isso; muda o "
                    + "porte no cadastro.";
            case ISS_OUT_NEXT_YEAR -> "A receita de " + year + " passou do sublimite de " + Money.brl(limit.amount()) + " em " + month
                    + ", em até 20%. A partir de janeiro de " + next + ", o ISS (e o ICMS, se houver) sai do DAS e passa a ser pago "
                    + "à prefeitura (e ao estado), pelas regras normais.";
            case ISS_OUT_NEXT_MONTH -> "A receita de " + year + " passou mais de 20% do sublimite (" + tolerance + ") em " + beyondMonth
                    + ". A partir de " + from + ", o ISS (e o ICMS, se houver) sai do DAS e passa a ser pago à prefeitura (e ao "
                    + "estado). Procure o seu contador agora.";
            case ISS_OUT_RETROACTIVE -> "No ano de abertura, a receita passou mais de 20% do sublimite proporcional (" + tolerance
                    + ") em " + beyondMonth + ". O ISS (e o ICMS, se houver) sai do DAS desde a abertura, em " + from
                    + ". Procure o seu contador agora.";
            case EXCLUDED_NEXT_YEAR -> "A receita de " + year + " passou do teto do Simples (" + Money.brl(limit.amount()) + ") em "
                    + month + ", em até 20%. A empresa sai do Simples Nacional em 1º de janeiro de " + next + ", e os impostos "
                    + "passam a ser os do Lucro Presumido ou Real. Planeje a mudança com o seu contador.";
            case EXCLUDED_NEXT_MONTH -> "A receita de " + year + " passou mais de 20% do teto do Simples (" + tolerance + ") em "
                    + beyondMonth + ". A empresa sai do Simples Nacional a partir de " + from + ". Procure o seu contador agora.";
            case EXCLUDED_RETROACTIVE -> "No ano de abertura, a receita passou mais de 20% do teto proporcional (" + tolerance + ") em "
                    + beyondMonth + ". A exclusão do Simples vale desde a abertura, em " + from + ". Procure o seu contador agora.";
        };
    }

    static String projection(RevenueLimits.Limit limit) {
        if (limit.projectedIn() == null) {
            return null;
        }
        return "No ritmo atual, a receita passa d" + (limit.kind() == RevenueLimits.Kind.SIMPLES_EPP
                ? "o limite da microempresa" : target(limit.kind())) + " em " + month(limit.projectedIn()) + ".";
    }

    private static String percent(BigDecimal ratio) {
        return ratio.multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.DOWN).toPlainString() + "%";
    }

    private static String month(YearMonth month) {
        return MONTH.format(month);
    }
}
