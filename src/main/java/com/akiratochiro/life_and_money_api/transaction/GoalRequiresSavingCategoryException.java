package com.akiratochiro.life_and_money_api.transaction;

public class GoalRequiresSavingCategoryException extends RuntimeException {
    public GoalRequiresSavingCategoryException() {
        super("Só é possível ligar uma transação a uma meta em categorias de poupança.");
    }
}
