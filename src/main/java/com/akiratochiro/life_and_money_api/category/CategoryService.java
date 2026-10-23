package com.akiratochiro.life_and_money_api.category;

import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public Category create(Long userId, String name, CategoryType type) {
        String normalizedName = Category.normalizeName(name);

        if (categoryRepository.existsByUserIdAndTypeAndNameIgnoreCaseAndArchivedFalse(userId, type, normalizedName)) {
            throw new CategoryAlreadyExistsException(normalizedName);
        }

        return categoryRepository.save(new Category(userId, normalizedName, type));
    }

    @Transactional
    public Category rename(Long userId, Long categoryId, String newName) {
        Category category = findOwned(userId, categoryId);
        String normalizedName = Category.normalizeName(newName);

        boolean onlyCaseChange = normalizedName.equalsIgnoreCase(category.getName());

        if (!category.isArchived() && !onlyCaseChange
                && categoryRepository.existsByUserIdAndTypeAndNameIgnoreCaseAndArchivedFalse(
                userId, category.getType(), normalizedName)) {
            throw new CategoryAlreadyExistsException(normalizedName);
        }

        category.rename(normalizedName);
        return category;
    }

    @Transactional(readOnly = true)
    public List<Category> list(Long userId, boolean includeArchived) {
        if (includeArchived) {
            return categoryRepository.findAllByUserIdOrderByNameAsc(userId);
        }
        return categoryRepository.findAllByUserIdAndArchivedFalseOrderByNameAsc(userId);
    }


    @Transactional
    public Category archive(Long userId, Long categoryId) {
        Category category = findOwned(userId, categoryId);
        category.archive();
        return category;
    }

    private Category findOwned(Long userId, Long categoryId) {
        return categoryRepository.findByIdAndUserId(categoryId, userId)
                .orElseThrow(CategoryNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public Category getActiveOwned(Long userId, Long categoryId) {
        Category category = findOwned(userId, categoryId);
        if (category.isArchived()) {
            throw new CategoryArchivedException();
        }
        return category;
    }

    @Transactional(readOnly = true)
    public Category getOwned(Long userId, Long categoryId) {
        return findOwned(userId, categoryId);
    }
}
