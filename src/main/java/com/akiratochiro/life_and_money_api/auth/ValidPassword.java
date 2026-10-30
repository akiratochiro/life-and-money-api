package com.akiratochiro.life_and_money_api.auth;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * As regras de senha, num lugar só: o cadastro e a redefinição usam esta anotação, então não há
 * como as duas divergirem.
 *
 * <p>O teto de 72 vem do BCrypt, que ignora o que passa de 72 bytes: uma senha maior daria a
 * falsa impressão de que o resto conta.
 */
@NotBlank(message = "Informe uma senha.")
@Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres.")
@Constraint(validatedBy = {})
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPassword {

    String message() default "";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
