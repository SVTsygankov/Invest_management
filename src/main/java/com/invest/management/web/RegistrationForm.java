package com.invest.management.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegistrationForm {

    @NotBlank(message = "Укажите e-mail")
    @Email(message = "Введите корректный e-mail")
    private String email;

    @NotBlank(message = "Введите пароль")
    @Size(min = 8, message = "Пароль должен быть не короче 8 символов")
    private String password;

    @NotBlank(message = "Повторите пароль")
    private String confirmPassword;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
}

