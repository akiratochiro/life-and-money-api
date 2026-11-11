package com.akiratochiro.life_and_money_api.companyexpense;

import com.akiratochiro.life_and_money_api.category.CategoryColor;
import com.akiratochiro.life_and_money_api.category.CategoryIcon;
import com.akiratochiro.life_and_money_api.company.CompanyService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Collator;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** As categorias de despesa de cada empresa. Escrever exige a empresa ativa (não arquivada). */
@Service
public class CompanyExpenseCategoryService {

    private final CompanyExpenseCategoryRepository repository;
    private final CompanyService companyService;
    private final ApplicationEventPublisher eventPublisher;

    public CompanyExpenseCategoryService(CompanyExpenseCategoryRepository repository, CompanyService companyService,
                                         ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.companyService = companyService;
        this.eventPublisher = eventPublisher;
    }

    /** Em ordem alfabética do português; a do sistema vem junto, marcada, para a tela mostrá-la com cadeado. */
    @Transactional(readOnly = true)
    public List<CompanyExpenseCategory> list(Long userId, Long companyId, boolean includeArchived) {
        companyService.getOwned(userId, companyId);
        List<CompanyExpenseCategory> categories = includeArchived
                ? repository.findAllByCompanyId(companyId)
                : repository.findAllByCompanyIdAndArchivedFalse(companyId);
        Collator collator = Collator.getInstance(Locale.of("pt", "BR"));
        collator.setStrength(Collator.SECONDARY);
        return categories.stream().sorted(Comparator.comparing(CompanyExpenseCategory::getName, collator::compare)).toList();
    }

    @Transactional
    public CompanyExpenseCategory create(Long userId, Long companyId, String name, CategoryColor color,
                                         CategoryIcon icon, boolean countsAsPayroll) {
        companyService.getActiveOwned(userId, companyId);
        String normalized = name.strip();
        if (repository.existsByCompanyIdAndNameIgnoreCaseAndArchivedFalse(companyId, normalized)) {
            throw InvalidExpenseCategoryException.duplicate();
        }
        return repository.save(new CompanyExpenseCategory(userId, companyId, normalized,
                color != null ? color : CategoryColor.CORAL, icon != null ? icon : CategoryIcon.OTHER,
                countsAsPayroll, null));
    }

    /** Edição parcial: o que vier nulo fica como está. */
    @Transactional
    public CompanyExpenseCategory update(Long userId, Long companyId, Long categoryId, String name,
                                         CategoryColor color, CategoryIcon icon, Boolean countsAsPayroll) {
        companyService.getActiveOwned(userId, companyId);
        CompanyExpenseCategory category = editable(companyId, categoryId);
        if (name != null) {
            String normalized = name.strip();
            if (!normalized.equalsIgnoreCase(category.getName())
                    && repository.existsByCompanyIdAndNameIgnoreCaseAndArchivedFalse(companyId, normalized)) {
                throw InvalidExpenseCategoryException.duplicate();
            }
            category.rename(normalized);
        }
        if (color != null) {
            category.changeColor(color);
        }
        if (icon != null) {
            category.changeIcon(icon);
        }
        if (countsAsPayroll != null) {
            category.changeCountsAsPayroll(countsAsPayroll);
        }
        return category;
    }

    /** Arquivar avisa quem depende (as despesas fixas da categoria são encerradas). Repetir não faz nada. */
    @Transactional
    public CompanyExpenseCategory archive(Long userId, Long companyId, Long categoryId) {
        companyService.getActiveOwned(userId, companyId);
        CompanyExpenseCategory category = editable(companyId, categoryId);
        if (!category.isArchived()) {
            category.archive();
            eventPublisher.publishEvent(new ExpenseCategoryArchivedEvent(userId, companyId, categoryId));
        }
        return category;
    }

    /** Para lançar uma despesa: da empresa, ativa e escolhível (a do sistema não é). */
    @Transactional(readOnly = true)
    public CompanyExpenseCategory getSelectable(Long companyId, Long categoryId) {
        CompanyExpenseCategory category = find(companyId, categoryId);
        if (category.isSystem()) {
            throw InvalidExpenseCategoryException.system();
        }
        if (category.isArchived()) {
            throw InvalidExpenseCategoryException.archived();
        }
        return category;
    }

    /** A categoria de impostos da empresa, para a Fase 4 lançar o imposto pago. */
    @Transactional(readOnly = true)
    public CompanyExpenseCategory taxes(Long companyId) {
        return repository.findByCompanyIdAndSystemKey(companyId, ExpenseCategorySystemKey.TAXES).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<CompanyExpenseCategory> listAll(Long userId) {
        return repository.findAllByUserIdOrderByCompanyIdAscIdAsc(userId);
    }

    @Transactional
    public void deleteAllOf(Long userId) {
        repository.deleteAllByUserId(userId);
    }

    private CompanyExpenseCategory editable(Long companyId, Long categoryId) {
        CompanyExpenseCategory category = find(companyId, categoryId);
        if (category.isSystem()) {
            throw InvalidExpenseCategoryException.system();
        }
        return category;
    }

    private CompanyExpenseCategory find(Long companyId, Long categoryId) {
        return repository.findByIdAndCompanyId(categoryId, companyId).orElseThrow(ExpenseCategoryNotFoundException::new);
    }
}
