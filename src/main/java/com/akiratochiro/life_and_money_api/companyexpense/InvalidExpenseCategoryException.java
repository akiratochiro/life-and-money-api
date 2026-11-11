package com.akiratochiro.life_and_money_api.companyexpense;

/** Uma mudança de categoria que a regra recusa: a do sistema, uma arquivada, um nome repetido. */
public class InvalidExpenseCategoryException extends RuntimeException {

    private final boolean conflict;

    private InvalidExpenseCategoryException(String message, boolean conflict) {
        super(message);
        this.conflict = conflict;
    }

    static InvalidExpenseCategoryException system() {
        return new InvalidExpenseCategoryException("A categoria Impostos é do sistema: ela recebe os impostos pagos e "
                + "não pode ser escolhida, alterada nem arquivada.", false);
    }

    static InvalidExpenseCategoryException archived() {
        return new InvalidExpenseCategoryException("Esta categoria está arquivada e não aceita novas despesas.", true);
    }

    static InvalidExpenseCategoryException duplicate() {
        return new InvalidExpenseCategoryException("Já existe uma categoria ativa com esse nome nesta empresa.", true);
    }

    /** 409 para o que conflita com o estado (arquivada, repetida); 422 para a regra da categoria do sistema. */
    public boolean isConflict() {
        return conflict;
    }
}
