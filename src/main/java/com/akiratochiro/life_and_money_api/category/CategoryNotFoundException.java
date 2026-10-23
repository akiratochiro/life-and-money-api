package com.akiratochiro.life_and_money_api.category;

public class CategoryNotFoundException extends RuntimeException {
    public CategoryNotFoundException() {
        super("Categoria não encontrada.");
    }
}
