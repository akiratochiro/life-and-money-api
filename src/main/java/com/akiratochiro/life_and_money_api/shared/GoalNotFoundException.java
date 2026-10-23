package com.akiratochiro.life_and_money_api.shared;

public class GoalNotFoundException extends RuntimeException {
    public GoalNotFoundException() {
        super("Meta não encontrada.");
    }
}
