package com.akiratochiro.life_and_money_api.category;

public class CategoryAlreadyExistsException extends RuntimeException {
    public CategoryAlreadyExistsException(String name) {
        super("Você já tem uma categoria chamada \"" + name + "\".");
    }
}
