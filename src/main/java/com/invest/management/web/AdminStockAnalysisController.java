package com.invest.management.web;

import com.invest.management.analysis.ExpertAssessmentDto;
import com.invest.management.analysis.StockAnalysisRow;
import com.invest.management.analysis.StockAnalysisService;
import com.invest.management.moex.MoexStock;
import com.invest.management.moex.MoexStockRepository;
import com.invest.management.user.AppUser;
import com.invest.management.user.AppUserRepository;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/analysis")
@PreAuthorize("hasRole('ADMIN')")
public class AdminStockAnalysisController {

    private static final Logger log = LoggerFactory.getLogger(AdminStockAnalysisController.class);
    private static final String SESSION_LAST_ADD = "admin:analysis:lastSecid";

    private final StockAnalysisService analysisService;
    private final AppUserRepository userRepository;
    private final MoexStockRepository stockRepository;
    private final HttpSession httpSession;

    public AdminStockAnalysisController(StockAnalysisService analysisService,
                                       AppUserRepository userRepository,
                                       MoexStockRepository stockRepository,
                                       HttpSession httpSession) {
        this.analysisService = analysisService;
        this.userRepository = userRepository;
        this.stockRepository = stockRepository;
        this.httpSession = httpSession;
    }

    @GetMapping
    public String analysis(@RequestParam(value = "sortBy", required = false) String sortBy,
                          @RequestParam(value = "sortDir", required = false) String sortDir,
                          Model model) {
        AppUser user = currentUser();
        List<StockAnalysisRow> rows = analysisService.getAllRows(sortBy, sortDir);
        List<MoexStock> availableStocks = analysisService.getAvailableStocks();
        String sessionLast = (String) httpSession.getAttribute(SESSION_LAST_ADD);
        String defaultSelection = availableStocks.isEmpty() ? null : availableStocks.get(0).getSecid();
        if (sessionLast != null && availableStocks.stream().anyMatch(s -> s.getSecid().equals(sessionLast))) {
            defaultSelection = sessionLast;
        }

        Map<String, Object> attributes = model.asMap();
        Object preselectAttr = attributes.get("analysisPreselect");
        if (preselectAttr instanceof String preselect && !preselect.isBlank()) {
            defaultSelection = preselect;
        }
        boolean openAddDialog = Boolean.TRUE.equals(attributes.get("analysisOpenDialog"));

        log.debug("Admin {} stock analysis page: rows={}, availableStocks={}, defaultSelection={}, sortBy={}, sortDir={}",
            user.getEmail(), rows.size(), availableStocks.size(), defaultSelection, sortBy, sortDir);

        model.addAttribute("rows", rows);
        model.addAttribute("hasRows", !rows.isEmpty());
        model.addAttribute("availableStocks", availableStocks);
        model.addAttribute("defaultSelection", defaultSelection);
        model.addAttribute("openAddDialog", openAddDialog);
        model.addAttribute("currentSortBy", sortBy);
        model.addAttribute("currentSortDir", sortDir);
        model.addAttribute("currentTable", "stock_analysis");

        return "admin-stock-analysis";
    }

    @PostMapping("/add")
    public String addStock(@RequestParam("secid") String secid,
                           RedirectAttributes redirectAttributes) {
        if (!StringUtils.hasText(secid)) {
            redirectAttributes.addFlashAttribute("errorMessage", "Выберите акцию для добавления.");
            return "redirect:/admin/analysis";
        }
        AppUser user = currentUser();
        MoexStock stock = stockRepository.findBySecid(secid)
            .orElse(null);
        if (stock == null) {
            redirectAttributes.addFlashAttribute("errorMessage", "Акция не найдена: " + secid);
            redirectAttributes.addFlashAttribute("analysisOpenDialog", true);
            redirectAttributes.addFlashAttribute("analysisPreselect", secid);
            return "redirect:/admin/analysis";
        }
        try {
            analysisService.addStock(stock);
            httpSession.setAttribute(SESSION_LAST_ADD, stock.getSecid());
            log.info("Admin {} added stock {} to analysis", user.getEmail(), stock.getSecid());
            redirectAttributes.addFlashAttribute("successMessage", "Акция " + stock.getSecid() + " добавлена в анализ.");
        } catch (IllegalArgumentException ex) {
            log.warn("Admin {} attempted to add duplicate stock {}: {}", user.getEmail(), stock.getSecid(), ex.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            redirectAttributes.addFlashAttribute("analysisOpenDialog", true);
            redirectAttributes.addFlashAttribute("analysisPreselect", stock.getSecid());
        }
        return "redirect:/admin/analysis";
    }

    @PostMapping("/delete")
    public String delete(@RequestParam("id") Long analysisId,
                         RedirectAttributes redirectAttributes) {
        AppUser user = currentUser();
        try {
            analysisService.delete(analysisId);
            log.info("Admin {} deleted analysis id {}", user.getEmail(), analysisId);
            redirectAttributes.addFlashAttribute("successMessage", "Запись удалена.");
        } catch (Exception ex) {
            log.warn("Admin {} failed to delete analysis: {}", user.getEmail(), ex.getMessage());
            redirectAttributes.addFlashAttribute("errorMessage", "Не удалось удалить запись.");
        }
        return "redirect:/admin/analysis";
    }

    @GetMapping("/expert-assessment/{analysisId}/history")
    @ResponseBody
    public ResponseEntity<List<ExpertAssessmentDto>> getExpertAssessmentHistory(
            @PathVariable Long analysisId) {
        AppUser user = currentUser();
        try {
            List<ExpertAssessmentDto> history = analysisService.getExpertAssessmentHistory(analysisId);
            return ResponseEntity.ok(history);
        } catch (IllegalArgumentException ex) {
            log.warn("Admin {} failed to get expert assessment history: {}", user.getEmail(), ex.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Ошибка при получении истории экспертных оценок", e);
            return ResponseEntity.status(500).build();
        }
    }

    @PostMapping("/expert-assessment")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveExpertAssessment(
            @RequestParam("analysisId") Long analysisId,
            @RequestParam(value = "expertTarget", required = false) String expertTargetStr,
            @RequestParam(value = "expertRecommendation", required = false) String expertRecommendation,
            @RequestParam("expertTargetDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expertTargetDate) {
        AppUser user = currentUser();
        
        try {
            BigDecimal target = null;
            if (expertTargetStr != null && !expertTargetStr.isBlank()) {
                try {
                    target = new BigDecimal(expertTargetStr.trim());
                } catch (NumberFormatException e) {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("success", false);
                    errorResponse.put("message", "Некорректное значение целевой цены");
                    return ResponseEntity.badRequest().body(errorResponse);
                }
            }
            
            // Проверяем, что хотя бы одно поле заполнено
            if (target == null && (expertRecommendation == null || expertRecommendation.isBlank())) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "Необходимо заполнить хотя бы одно поле: целевая цена или рекомендация");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            analysisService.addExpertAssessment(analysisId, target, expertRecommendation, expertTargetDate);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Экспертная оценка сохранена");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            log.warn("Admin {} failed to save expert assessment: {}", user.getEmail(), ex.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", ex.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            log.error("Ошибка при сохранении экспертной оценки", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Внутренняя ошибка сервера");
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    @PutMapping("/expert-assessment/{assessmentId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> updateExpertAssessment(
            @PathVariable Long assessmentId,
            @RequestParam(value = "expertTarget", required = false) String expertTargetStr,
            @RequestParam(value = "expertRecommendation", required = false) String expertRecommendation,
            @RequestParam(value = "expertTargetDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expertTargetDate) {
        AppUser user = currentUser();
        
        try {
            BigDecimal target = null;
            if (expertTargetStr != null && !expertTargetStr.isBlank()) {
                try {
                    target = new BigDecimal(expertTargetStr.trim());
                } catch (NumberFormatException e) {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("success", false);
                    errorResponse.put("message", "Некорректное значение целевой цены");
                    return ResponseEntity.badRequest().body(errorResponse);
                }
            }
            
            analysisService.updateExpertAssessment(assessmentId, target, expertRecommendation, expertTargetDate);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Экспертная оценка обновлена");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            log.warn("Admin {} failed to update expert assessment: {}", user.getEmail(), ex.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", ex.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            log.error("Ошибка при обновлении экспертной оценки", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Внутренняя ошибка сервера");
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    @DeleteMapping("/expert-assessment/{assessmentId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deleteExpertAssessment(@PathVariable Long assessmentId) {
        AppUser user = currentUser();
        
        try {
            analysisService.deleteExpertAssessment(assessmentId);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Экспертная оценка удалена");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException ex) {
            log.warn("Admin {} failed to delete expert assessment: {}", user.getEmail(), ex.getMessage());
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", ex.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            log.error("Ошибка при удалении экспертной оценки", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Внутренняя ошибка сервера");
            return ResponseEntity.status(500).body(errorResponse);
        }
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

