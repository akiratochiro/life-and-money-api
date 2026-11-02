package com.akiratochiro.life_and_money_api.category;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table (name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private String name;
    @Enumerated(EnumType.STRING)
    private CategoryType type;

    @Enumerated(EnumType.STRING)
    private CategoryColor color;

    @Enumerated(EnumType.STRING)
    private CategoryIcon icon;

    private boolean archived;
    private Instant createdAt;


    protected Category(){}

    public Category(Long userId, String name, CategoryType type) {
        this(userId, name, type, null, null);
    }

    /** Sem cor ou sem ícone, vale o padrão do tipo (ver {@link #defaultColor} e {@link #defaultIcon}). */
    public Category(Long userId, String name, CategoryType type, CategoryColor color, CategoryIcon icon) {
        this.userId = userId;
        this.name = normalizeName(name);
        this.type = type;
        this.color = color != null ? color : defaultColor(type);
        this.icon = icon != null ? icon : defaultIcon(type);
        this.archived = false;
        this.createdAt = Instant.now();
    }

    public boolean isArchived() {
        return archived;
    }

    public Long getId(){
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getName(){
        return name;
    }

    public CategoryType getType(){
        return type;
    }

    public Instant getCreatedAt(){
        return createdAt;
    }

    public CategoryColor getColor() {
        return color;
    }

    public CategoryIcon getIcon() {
        return icon;
    }

    public void changeColor(CategoryColor newColor) {
        this.color = newColor;
    }

    public void changeIcon(CategoryIcon newIcon) {
        this.icon = newIcon;
    }

    static CategoryColor defaultColor(CategoryType type) {
        return switch (type) {
            case INCOME -> CategoryColor.MINT;
            case EXPENSE -> CategoryColor.CORAL;
            case SAVING -> CategoryColor.PINK;
        };
    }

    static CategoryIcon defaultIcon(CategoryType type) {
        return switch (type) {
            case INCOME -> CategoryIcon.COIN;
            case EXPENSE -> CategoryIcon.OTHER;
            case SAVING -> CategoryIcon.PIGGY_BANK;
        };
    }

    public void rename(String newName) {
        this.name = normalizeName(newName);
    }

    public void archive(){
        archived = true;
    }

    static String normalizeName(String name) {
        return name.strip();
    }
}
