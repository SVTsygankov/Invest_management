package com.invest.management.web;

import com.invest.management.analysis.StockAnalysis;
import com.invest.management.analysis.UserAnalysisRow;
import com.invest.management.analysis.UserAnalysisService;
import com.invest.management.user.AppUser;
import com.invest.management.user.AppUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/user-analysis")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserAnalysisController {

    private static final Logger log = LoggerFactory.getLogger(AdminUserAnalysisController.class);

    private final UserAnalysisService userAnalysisService;
    private final AppUserRepository userRepository;

    public AdminUserAnalysisController(UserAnalysisService userAnalysisService,
                                       AppUserRepository userRepository) {
        this.userAnalysisService = userAnalysisService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String analysis(@RequestParam(value = "sortBy", required = false) String sortBy,
                          @RequestParam(value = "sortDir", required = false) String sortDir,
                          Model model) {
        AppUser user = currentUser();
        // Для админа показываем все user_analysis всех пользователей
        // Пока используем текущего пользователя, но можно расширить для просмотра всех
        List<UserAnalysisRow> rows = userAnalysisService.getRowsForUser(user, sortBy, sortDir);
        List<StockAnalysis> availableStockAnalyses = userAnalysisService.getAvailableStockAnalyses(user);
        
        // Вычисляем expertForecastPercent для каждого StockAnalysis и округляем до 2 знаков
        java.util.Map<Long, java.math.BigDecimal> forecastPercentMap = new java.util.HashMap<>();
        for (StockAnalysis analysis : availableStockAnalyses) {
            java.math.BigDecimal forecast = calculateExpertForecastPercent(
                analysis.getExpertTarget(),
                analysis.getStock() != null ? analysis.getStock().getMarketprice() : null
            );
            if (forecast != null) {
                forecast = forecast.setScale(2, java.math.RoundingMode.HALF_UP);
            }
            forecastPercentMap.put(analysis.getId(), forecast);
        }

        log.debug("Admin {} user analysis page: rows={}, availableStockAnalyses={}, sortBy={}, sortDir={}",
            user.getEmail(), rows.size(), availableStockAnalyses.size(), sortBy, sortDir);

        model.addAttribute("rows", rows);
        model.addAttribute("hasRows", !rows.isEmpty());
        model.addAttribute("availableStockAnalyses", availableStockAnalyses);
        model.addAttribute("forecastPercentMap", forecastPercentMap);
        model.addAttribute("currentSortBy", sortBy);
        model.addAttribute("currentSortDir", sortDir);
        model.addAttribute("currentTable", "user_analysis");

        return "admin-user-analysis";
    }
    
    private java.math.BigDecimal calculateExpertForecastPercent(java.math.BigDecimal target, java.math.BigDecimal marketPrice) {
        if (target == null || marketPrice == null || marketPrice.compareTo(java.math.BigDecimal.ZERO) == 0) {
            return null;
        }
        return target.subtract(marketPrice)
            .divide(marketPrice, 6, java.math.RoundingMode.HALF_UP)
            .multiply(java.math.BigDecimal.valueOf(100));
    }

    @PostMapping("/add")
    public String addStockAnalyses(@RequestParam(value = "stockAnalysisIds", required = false) List<Long> stockAnalysisIds,
                                   RedirectAttributes redirectAttributes) {
        if (stockAnalysisIds == null || stockAnalysisIds.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Выберите акции для добавления.");
            return "redirect:/admin/user-analysis";
        }
        AppUser user = currentUser();
        try {
            userAnalysisService.addStockAnalyses(user, stockAnalysisIds);
            log.info("Admin {} added {} stock analyses to user_analysis", user.getEmail(), stockAnalysisIds.size());
            redirectAttributes.addFlashAttribute("successMessage", 
                "Добавлено акций: " + stockAnalysisIds.size());
        } catch (IllegalArgumentException ex) {
            log.warn("Admin {} failed to add stock analyses: {}", user.getEmail(), ex.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/user-analysis";
    }

    @PostMapping("/update")
    public String updateLevels(@RequestParam Map<String, String> formParams,
                               RedirectAttributes redirectAttributes) {
        AppUser user = currentUser();
        Map<Long, String> supportMap = extractValues(formParams, "support");
        Map<Long, String> resistanceMap = extractValues(formParams, "resistance");
        try {
            userAnalysisService.updateLevels(user, supportMap, resistanceMap);
            log.info("Admin {} updated user_analysis levels: support={}, resistance={}",
                user.getEmail(), supportMap.keySet(), resistanceMap.keySet());
            redirectAttributes.addFlashAttribute("successMessage", "Изменения сохранены.");
        } catch (IllegalArgumentException ex) {
            log.warn("Admin {} failed to update user_analysis levels: {}", user.getEmail(), ex.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/admin/user-analysis";
    }

    @PostMapping("/delete")
    public String delete(@RequestParam("id") Long userAnalysisId,
                         RedirectAttributes redirectAttributes) {
        AppUser user = currentUser();
        try {
            userAnalysisService.delete(user, userAnalysisId);
            log.info("Admin {} deleted user analysis id {}", user.getEmail(), userAnalysisId);
            redirectAttributes.addFlashAttribute("successMessage", "Запись удалена.");
        } catch (Exception ex) {
            log.warn("Admin {} failed to delete user analysis: {}", user.getEmail(), ex.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Не удалось удалить запись.");
        }
        return "redirect:/admin/user-analysis";
    }

    private java.util.Map<Long, String> extractValues(Map<String, String> source, String prefix) {
        if (source == null || source.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        String keyPrefix = prefix + "[";
        return source.entrySet().stream()
            .filter(entry -> entry.getKey().startsWith(keyPrefix) && entry.getKey().endsWith("]"))
            .collect(java.util.stream.Collectors.toMap(
                entry -> parseId(entry.getKey(), keyPrefix.length()),
                Map.Entry::getValue,
                (existing, replacement) -> replacement
            ));
    }

    private Long parseId(String key, int prefixLength) {
        String raw = key.substring(prefixLength, key.length() - 1);
        return Long.parseLong(raw);
    }

    private AppUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !StringUtils.hasText(authentication.getName())) {
            throw new IllegalStateException("Не удалось определить текущего пользователя");
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName())
            .orElseThrow(() -> new IllegalStateException("Пользователь не найден: " + authentication.getName()));
    }
}

