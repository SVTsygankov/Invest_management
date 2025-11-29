package com.invest.management.web;

import com.invest.management.user.AppUserService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import org.springframework.security.core.Authentication;

@Controller
public class RegistrationController {

    private final AppUserService appUserService;

    public RegistrationController(AppUserService appUserService) {
        this.appUserService = appUserService;
    }

    @GetMapping("/")
    public String showRegistration(Model model, Authentication authentication) {
        if (authentication != null && authentication.isAuthenticated()) {
            return "redirect:/dashboard";
        }
        prepareForm(model);
        return "registration";
    }

    @PostMapping("/register")
    public String registerUser(@Valid @ModelAttribute("registrationForm") RegistrationForm form,
                               BindingResult bindingResult,
                               RedirectAttributes redirectAttributes) {
        if (!form.getPassword().equals(form.getConfirmPassword())) {
            bindingResult.rejectValue("confirmPassword", "password.mismatch", "Пароли должны совпадать");
        }

        if (!bindingResult.hasFieldErrors("email")
            && appUserService.emailExists(form.getEmail())) {
            bindingResult.rejectValue("email", "email.exists", "Такой e-mail уже зарегистрирован");
        }

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.registrationForm", bindingResult);
            redirectAttributes.addFlashAttribute("registrationForm", form);
            return "redirect:/";
        }

        appUserService.register(form);
        redirectAttributes.addFlashAttribute("successMessage", "Регистрация успешно выполнена. Теперь вы можете войти после настройки аутентификации.");
        return "redirect:/";
    }

    @GetMapping("/login")
    public String showLogin() {
        return "login";
    }

    private void prepareForm(Model model) {
        if (!model.containsAttribute("registrationForm")) {
            model.addAttribute("registrationForm", new RegistrationForm());
        }
    }
}

