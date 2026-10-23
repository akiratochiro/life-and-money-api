package com.akiratochiro.life_and_money_api.category;

public class CategoryArchivedException extends RuntimeException {
    public CategoryArchivedException() {
        super("Esta categoria está arquivada e não aceita novas transações.");
    }
}
