package com.akiratochiro.life_and_money_api.companyexpense;

import com.akiratochiro.life_and_money_api.category.CategoryColor;
import com.akiratochiro.life_and_money_api.category.CategoryIcon;
import jakarta.persistence.*;

import java.time.Instant;

/**
 * Categoria de despesa de uma empresa, separada das categorias pessoais. Usa a mesma paleta e os
 * mesmos ícones das pessoais (decisão 12), para o front desenhar as duas do mesmo jeito.
 */
@Entity
@Table(name = "company_expense_categories")
public class CompanyExpenseCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long companyId;
    private String name;

    @Enumerated(EnumType.STRING)
    private CategoryColor color;

    @Enumerated(EnumType.STRING)
    private CategoryIcon icon;

    private boolean countsAsPayroll;

    @Enumerated(EnumType.STRING)
    private ExpenseCategorySystemKey systemKey;

    private boolean archived;
    private Instant createdAt;

    protected CompanyExpenseCategory() {
    }

    CompanyExpenseCategory(Long userId, Long companyId, String name, CategoryColor color, CategoryIcon icon,
                           boolean countsAsPayroll, ExpenseCategorySystemKey systemKey) {
        this.userId = userId;
        this.companyId = companyId;
        this.name = name;
        this.color = color;
        this.icon = icon;
        this.countsAsPayroll = countsAsPayroll;
        this.systemKey = systemKey;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getCompanyId() { return companyId; }
    public String getName() { return name; }
    public CategoryColor getColor() { return color; }
    public CategoryIcon getIcon() { return icon; }
    public boolean countsAsPayroll() { return countsAsPayroll; }
    public ExpenseCategorySystemKey getSystemKey() { return systemKey; }
    public boolean isSystem() { return systemKey != null; }
    public boolean isArchived() { return archived; }

    void rename(String newName) { this.name = newName; }
    void changeColor(CategoryColor newColor) { this.color = newColor; }
    void changeIcon(CategoryIcon newIcon) { this.icon = newIcon; }
    void changeCountsAsPayroll(boolean value) { this.countsAsPayroll = value; }
    void archive() { this.archived = true; }
}
