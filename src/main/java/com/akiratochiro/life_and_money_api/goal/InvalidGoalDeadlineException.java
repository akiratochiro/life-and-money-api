package com.akiratochiro.life_and_money_api.goal;

public class InvalidGoalDeadlineException extends RuntimeException {
    public InvalidGoalDeadlineException() {
        super("O prazo da meta precisa ser um mês posterior ao mês atual.");
    }
}
