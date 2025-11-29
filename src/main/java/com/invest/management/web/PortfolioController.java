package com.invest.management.web;

import com.invest.management.portfolio.*;
import com.invest.management.user.AppUser;
import com.invest.management.user.AppUserRepository;
import com.invest.management.web.AlorTokenForm;
import jakarta.validation.Valid;
import org.springframework.validation.BindingResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;

@Controller
@RequestMapping("/portfolios")
public class PortfolioController {

    private static final Logger log = LoggerFactory.getLogger(PortfolioController.class);

    private final PortfolioService portfolioService;
    private final BrokerReportService brokerReportService;
    private final PortfolioPositionService positionService;
    private final AppUserRepository userRepository;
    private final PortfolioValueService portfolioValueService;
    private final com.invest.management.alor.common.AlorTokenService alorTokenService;
    private final com.invest.management.alor.AlorUserTokenRepository alorTokenRepository;
    private final com.invest.management.alor.common.AlorApiClient alorApiClient;
    private final com.invest.management.alor.AlorPortfolioService alorPortfolioService;

    public PortfolioController(PortfolioService portfolioService,
                                BrokerReportService brokerReportService,
                                PortfolioPositionService positionService,
                                AppUserRepository userRepository,
                                PortfolioValueService portfolioValueService,
                                com.invest.management.alor.common.AlorTokenService alorTokenService,
                                com.invest.management.alor.AlorUserTokenRepository alorTokenRepository,
                                com.invest.management.alor.common.AlorApiClient alorApiClient,
                                com.invest.management.alor.AlorPortfolioService alorPortfolioService) {
        this.portfolioService = portfolioService;
        this.brokerReportService = brokerReportService;
        this.positionService = positionService;
        this.userRepository = userRepository;
        this.portfolioValueService = portfolioValueService;
        this.alorTokenService = alorTokenService;
        this.alorTokenRepository = alorTokenRepository;
        this.alorApiClient = alorApiClient;
        this.alorPortfolioService = alorPortfolioService;
    }

    @GetMapping
    public String listPortfolios(Model model, Authentication authentication) {
        AppUser user = getCurrentUser(authentication);
        List<Portfolio> portfolios = portfolioService.getUserPortfolios(user);
        model.addAttribute("portfolios", portfolios);
        return "portfolios/list";
    }

    @GetMapping("/new")
    public String showCreateForm(Model model) {
        model.addAttribute("portfolio", new PortfolioForm());
        return "portfolios/create";
    }

    @PostMapping
    public String createPortfolio(@ModelAttribute PortfolioForm form,
                                  Authentication authentication,
                                  RedirectAttributes redirectAttributes) {
        AppUser user = getCurrentUser(authentication);
        try {
            portfolioService.createPortfolio(user, form.getName(), form.getBrokerAccount());
            redirectAttributes.addFlashAttribute("success", "Портфель успешно создан");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка при создании портфеля: " + e.getMessage());
        }
        return "redirect:/portfolios";
    }

    @GetMapping("/{id}")
    public String viewPortfolio(@PathVariable Long id, Model model, Authentication authentication) {
        AppUser user = getCurrentUser(authentication);
        Optional<Portfolio> portfolio = portfolioService.getPortfolioByIdAndUser(id, user);
        if (portfolio.isEmpty()) {
            return "redirect:/portfolios";
        }

        Portfolio p = portfolio.get();
        PortfolioValueService.PortfolioValue portfolioValue = portfolioValueService.calculatePortfolioValue(p);
        
        // Проверяем, есть ли позиции, требующие ввода цены
        List<PortfolioPosition> positionsRequiringPrice = brokerReportService.getPositionsRequiringPriceInput(p);
        List<BrokerReportService.PositionWithName> positionsWithNames = brokerReportService.getPositionsWithNamesRequiringPriceInput(p);
        model.addAttribute("positionsRequiringPrice", positionsRequiringPrice);
        model.addAttribute("positionsWithNames", positionsWithNames);
        model.addAttribute("hasPositionsRequiringPrice", !positionsRequiringPrice.isEmpty());

        // Проверяем наличие токенов ALOR для пользователя
        boolean hasTestToken = alorTokenRepository.findByUserAndEnvironment(user, "test").isPresent();
        boolean hasProductionToken = alorTokenRepository.findByUserAndEnvironment(user, "production").isPresent();
        model.addAttribute("hasTestToken", hasTestToken);
        model.addAttribute("hasProductionToken", hasProductionToken);

        model.addAttribute("portfolio", p);
        model.addAttribute("portfolioValue", portfolioValue);
        
        // Инициализируем форму, если она не была передана через flash attributes
        if (!model.containsAttribute("alorTokenForm")) {
            model.addAttribute("alorTokenForm", new AlorTokenForm());
        }
        
        return "portfolios/view";
    }

    @GetMapping("/{id}/value")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getPortfolioValueJson(
            @PathVariable Long id,
            Authentication authentication) {
        AppUser user = getCurrentUser(authentication);
        Optional<Portfolio> portfolio = portfolioService.getPortfolioByIdAndUser(id, user);
        
        if (portfolio.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        PortfolioValueService.PortfolioValue portfolioValue = 
            portfolioValueService.calculatePortfolioValue(portfolio.get());
        
        Map<String, Object> result = new HashMap<>();
        result.put("totalValue", portfolioValue.getTotalValue());
        result.put("previousTotalValue", portfolioValue.getPreviousTotalValue());
        result.put("dailyChange", portfolioValue.getDailyChange());
        result.put("dailyChangePercent", portfolioValue.getDailyChangePercent());
        result.put("cashBalance", portfolioValue.getCashBalance());
        
        List<Map<String, Object>> positions = new ArrayList<>();
        for (PortfolioValueService.PositionValue posValue : portfolioValue.getPositions()) {
            Map<String, Object> pos = new HashMap<>();
            pos.put("isin", posValue.getPosition().getIsin());
            pos.put("shortName", posValue.getShortName());
            pos.put("securityType", posValue.getSecurityType());
            pos.put("currentPrice", posValue.getCurrentPrice());
            pos.put("currentValue", posValue.getCurrentValue());
            pos.put("dailyChange", posValue.getDailyChange());
            pos.put("dailyChangePercent", posValue.getDailyChangePercent());
            pos.put("totalReturn", posValue.getTotalReturn());
            pos.put("totalReturnPercent", posValue.getTotalReturnPercent());
            pos.put("decimals", posValue.getDecimals());
            positions.add(pos);
        }
        result.put("positions", positions);
        
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}/upload")
    public String showUploadForm(@PathVariable Long id, Model model, Authentication authentication) {
        AppUser user = getCurrentUser(authentication);
        Optional<Portfolio> portfolio = portfolioService.getPortfolioByIdAndUser(id, user);
        if (portfolio.isEmpty()) {
            return "redirect:/portfolios";
        }
        model.addAttribute("portfolio", portfolio.get());
        return "portfolios/upload";
    }

    @PostMapping("/{id}/upload")
    public String uploadReport(@PathVariable Long id,
                               @RequestParam("files") MultipartFile[] files,
                               Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        AppUser user = getCurrentUser(authentication);
        Optional<Portfolio> portfolio = portfolioService.getPortfolioByIdAndUser(id, user);
        if (portfolio.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Портфель не найден");
            return "redirect:/portfolios";
        }

        int successCount = 0;
        StringBuilder errors = new StringBuilder();

        Portfolio p = portfolio.get();
        // Проверяем, является ли это первым отчетом ДО загрузки
        boolean wasFirstReport = brokerReportService.isFirstReport(p);
        
        for (MultipartFile file : files) {
            if (file.isEmpty()) {
                continue;
            }
            try {
                brokerReportService.processReport(p, file, user);
                successCount++;
            } catch (Exception e) {
                if (errors.length() != 0) {
                    errors.append("; ");
                }
                errors.append(String.format("Ошибка при обработке файла %s: %s", 
                    file.getOriginalFilename(), e.getMessage()));
            }
        }

        if (successCount > 0) {
            redirectAttributes.addFlashAttribute("success", 
                String.format("Успешно загружено отчетов: %d", successCount));
            
            // Проверяем, нужно ли показать окно для ввода цен приобретения
            // (только если это был первый отчет и есть позиции без цены)
            if (wasFirstReport) {
                List<BrokerReportService.PositionWithName> positionsWithNames = brokerReportService.getPositionsWithNamesRequiringPriceInput(p);
                if (!positionsWithNames.isEmpty()) {
                    // Сохраняем информацию о позициях, требующих ввода цены, в сессии
                    redirectAttributes.addFlashAttribute("showPriceInputModal", true);
                    redirectAttributes.addFlashAttribute("positionsWithNames", positionsWithNames);
                }
            }
        }
        if (errors.length() != 0) {
            redirectAttributes.addFlashAttribute("error", errors.toString());
        }

        return "redirect:/portfolios/" + id;
    }

    @PostMapping("/{id}/save-purchase-prices")
    public String savePurchasePrices(@PathVariable Long id,
                                     @RequestParam Map<String, String> prices,
                                     Authentication authentication,
                                     RedirectAttributes redirectAttributes) {
        AppUser user = getCurrentUser(authentication);
        Optional<Portfolio> portfolio = portfolioService.getPortfolioByIdAndUser(id, user);
        if (portfolio.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Портфель не найден");
            return "redirect:/portfolios";
        }

        try {
            Portfolio p = portfolio.get();
            int updatedCount = 0;
            for (Map.Entry<String, String> entry : prices.entrySet()) {
                if (entry.getKey().startsWith("price_")) {
                    String isin = entry.getKey().substring(6); // Убираем префикс "price_"
                    String priceStr = entry.getValue();
                    if (priceStr != null && !priceStr.trim().isEmpty()) {
                        try {
                            java.math.BigDecimal price = new java.math.BigDecimal(priceStr.trim());
                            positionService.updateAveragePrice(p, isin, price);
                            updatedCount++;
                        } catch (NumberFormatException e) {
                            log.warn("Неверный формат цены для ISIN {}: {}", isin, priceStr);
                        }
                    }
                }
            }
            redirectAttributes.addFlashAttribute("success", 
                String.format("Сохранено цен приобретения: %d", updatedCount));
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка при сохранении цен: " + e.getMessage());
        }
        return "redirect:/portfolios/" + id;
    }

    @PostMapping("/{id}/delete")
    public String deletePortfolio(@PathVariable Long id,
                                  Authentication authentication,
                                  RedirectAttributes redirectAttributes) {
        AppUser user = getCurrentUser(authentication);
        try {
            portfolioService.deletePortfolio(id, user);
            redirectAttributes.addFlashAttribute("success", "Портфель удален");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка при удалении портфеля: " + e.getMessage());
        }
        return "redirect:/portfolios";
    }

    @PostMapping("/{id}/alor-token")
    public String saveAlorToken(@PathVariable Long id,
                                @Valid @ModelAttribute("alorTokenForm") AlorTokenForm form,
                                BindingResult bindingResult,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        AppUser user = getCurrentUser(authentication);
        Optional<Portfolio> portfolio = portfolioService.getPortfolioByIdAndUser(id, user);
        if (portfolio.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Портфель не найден");
            return "redirect:/portfolios";
        }

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.alorTokenForm", bindingResult);
            redirectAttributes.addFlashAttribute("alorTokenForm", form);
            redirectAttributes.addFlashAttribute("error", "Ошибка валидации формы. Проверьте введенные данные.");
            redirectAttributes.addFlashAttribute("showAlorTokenModal", true);
            return "redirect:/portfolios/" + id;
        }

        try {
            Portfolio p = portfolio.get();
            alorTokenService.saveRefreshToken(
                    user,
                    form.getEnvironment(),
                    form.getRefreshToken(),
                    form.getTradeServerCode(),
                    p.getId()
            );
            redirectAttributes.addFlashAttribute("success", 
                    String.format("Refresh Token для окружения '%s' успешно сохранен и зашифрован", form.getEnvironment()));
        } catch (Exception e) {
            log.error("Ошибка при сохранении ALOR токена для портфеля {}: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", "Ошибка при сохранении токена: " + e.getMessage());
        }

        return "redirect:/portfolios/" + id;
    }

    @GetMapping("/{id}/test-alor-token")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testAlorToken(@PathVariable Long id,
                                                              @RequestParam(required = false, defaultValue = "test") String environment,
                                                              Authentication authentication) {
        AppUser user = getCurrentUser(authentication);
        Optional<Portfolio> portfolio = portfolioService.getPortfolioByIdAndUser(id, user);
        if (portfolio.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Портфель не найден"));
        }

        try {
            Optional<com.invest.management.alor.common.AlorTokenService.AccessTokenResponse> tokenResponse = 
                    alorTokenService.getAccessToken(user, environment);
            
            if (tokenResponse.isPresent()) {
                com.invest.management.alor.common.AlorTokenService.AccessTokenResponse response = tokenResponse.get();
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("environment", environment);
                result.put("accessToken", response.getAccessToken());
                result.put("expiresIn", response.getExpiresIn());
                result.put("expiresAt", response.getExpiresAt() != null ? response.getExpiresAt().toString() : null);
                result.put("isExpired", response.isExpired());
                return ResponseEntity.ok(result);
            } else {
                // Проверяем, есть ли Refresh Token
                boolean hasToken = alorTokenService.getRefreshToken(user, environment).isPresent();
                String errorMessage = hasToken 
                    ? "Не удалось получить Access Token. Возможно, Refresh Token недействителен или истек срок действия."
                    : "Refresh Token не найден. Сохраните Refresh Token перед тестированием.";
                return ResponseEntity.badRequest().body(Map.of("error", errorMessage));
            }
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            // Обработка HTTP ошибок от ALOR API
            String errorMessage = String.format("Ошибка ALOR API: %s %s", 
                    e.getStatusCode(), 
                    e.getResponseBodyAsString() != null ? e.getResponseBodyAsString() : e.getMessage());
            log.error("HTTP ошибка при тестировании получения Access Token: {}", errorMessage, e);
            return ResponseEntity.badRequest().body(Map.of("error", errorMessage));
        } catch (Exception e) {
            log.error("Ошибка при тестировании получения Access Token: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", 
                    "Внутренняя ошибка: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}/test-alor-positions")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testAlorPositions(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "test") String environment,
            @RequestParam String alorPortfolioId,
            @RequestParam(required = false, defaultValue = "MOEX") String exchange,
            Authentication authentication) {
        AppUser user = getCurrentUser(authentication);
        Optional<Portfolio> portfolio = portfolioService.getPortfolioByIdAndUser(id, user);
        if (portfolio.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Портфель не найден"));
        }

        try {
            String rawData = alorApiClient.getPositionsRaw(user, environment, alorPortfolioId, exchange);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("environment", environment);
            result.put("alorPortfolioId", alorPortfolioId);
            result.put("exchange", exchange);
            result.put("rawData", rawData);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Ошибка при тестировании получения позиций: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/test-alor-transactions")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testAlorTransactions(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "test") String environment,
            @RequestParam String alorPortfolioId,
            @RequestParam(required = false, defaultValue = "MOEX") String exchange,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Authentication authentication) {
        AppUser user = getCurrentUser(authentication);
        Optional<Portfolio> portfolio = portfolioService.getPortfolioByIdAndUser(id, user);
        if (portfolio.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Портфель не найден"));
        }

        // По умолчанию - последние 30 дней
        if (from == null) {
            from = LocalDate.now().minusDays(30);
        }
        if (to == null) {
            to = LocalDate.now();
        }

        try {
            String rawData = alorApiClient.getTransactionsRaw(user, environment, alorPortfolioId, from, to, exchange);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("environment", environment);
            result.put("alorPortfolioId", alorPortfolioId);
            result.put("exchange", exchange);
            result.put("from", from.toString());
            result.put("to", to.toString());
            result.put("rawData", rawData);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Ошибка при тестировании получения сделок: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/test-alor-cash-movements")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testAlorCashMovements(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "test") String environment,
            @RequestParam String alorPortfolioId,
            @RequestParam(required = false, defaultValue = "MOEX") String exchange,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Authentication authentication) {
        AppUser user = getCurrentUser(authentication);
        Optional<Portfolio> portfolio = portfolioService.getPortfolioByIdAndUser(id, user);
        if (portfolio.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Портфель не найден"));
        }

        // По умолчанию - последние 30 дней
        if (from == null) {
            from = LocalDate.now().minusDays(30);
        }
        if (to == null) {
            to = LocalDate.now();
        }

        try {
            String rawData = alorApiClient.getCashMovementsRaw(user, environment, alorPortfolioId, from, to, exchange);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("environment", environment);
            result.put("alorPortfolioId", alorPortfolioId);
            result.put("exchange", exchange);
            result.put("from", from.toString());
            result.put("to", to.toString());
            result.put("rawData", rawData);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Ошибка при тестировании получения движений денежных средств: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/test-alor-portfolios")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testAlorPortfolios(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "test") String environment,
            Authentication authentication) {
        AppUser user = getCurrentUser(authentication);
        Optional<Portfolio> portfolio = portfolioService.getPortfolioByIdAndUser(id, user);
        if (portfolio.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Портфель не найден"));
        }

        try {
            List<String> portfolios = alorTokenService.getPortfoliosFromToken(user, environment);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("environment", environment);
            result.put("portfolios", portfolios);
            result.put("portfoliosCount", portfolios.size());
            
            // Также декодируем весь токен для просмотра
            Optional<com.fasterxml.jackson.databind.JsonNode> tokenPayload = 
                    alorTokenService.decodeTokenPayload(user, environment);
            if (tokenPayload.isPresent()) {
                result.put("tokenPayload", tokenPayload.get());
            }
            
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Ошибка при получении списка портфелей: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    private AppUser getCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmailIgnoreCase(email)
            .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
    }

    private boolean hasAlorToken(AppUser user, String environment) {
        return alorTokenRepository.findByUserAndEnvironment(user, environment).isPresent();
    }

    @PostMapping("/{id}/sync-from-alor")
    public String syncFromAlor(@PathVariable Long id,
                                @RequestParam String environment,
                                @RequestParam String alorPortfolioId,
                                @RequestParam(required = false, defaultValue = "MOEX") String exchange,
                                Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        AppUser user = getCurrentUser(authentication);
        Optional<Portfolio> portfolio = portfolioService.getPortfolioByIdAndUser(id, user);
        if (portfolio.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Портфель не найден");
            return "redirect:/portfolios";
        }

        // Проверяем наличие токена для выбранного окружения
        if (!hasAlorToken(user, environment)) {
            redirectAttributes.addFlashAttribute("error", 
                    String.format("Refresh Token для окружения '%s' не найден. Сохраните токен перед синхронизацией.", environment));
            redirectAttributes.addFlashAttribute("showAlorTokenModal", true);
            return "redirect:/portfolios/" + id;
        }

        try {
            Portfolio p = portfolio.get();
            com.invest.management.alor.AlorPortfolioService.SyncResult result = 
                    alorPortfolioService.syncPositionsFromAlor(p, user, environment, alorPortfolioId, exchange);

            // Формируем сообщение об успехе
            StringBuilder message = new StringBuilder();
            message.append(String.format("Синхронизация завершена: успешно обработано %d из %d позиций", 
                    result.getSuccessfulPositions(), result.getTotalPositions()));
            
            if (result.getSkippedPositions() > 0) {
                message.append(String.format(", пропущено: %d", result.getSkippedPositions()));
            }
            
            if (!result.getProblematicPositions().isEmpty()) {
                message.append(String.format(". Проблемных позиций: %d", result.getProblematicPositions().size()));
                redirectAttributes.addFlashAttribute("problematicPositions", result.getProblematicPositions());
            }
            
            if (result.getFreeCashRUB().compareTo(java.math.BigDecimal.ZERO) > 0) {
                message.append(String.format(". Свободные средства RUB: %s", 
                        result.getFreeCashRUB().setScale(2, java.math.RoundingMode.HALF_UP)));
            }

            redirectAttributes.addFlashAttribute("success", message.toString());
            
            if (!result.getProblematicPositions().isEmpty()) {
                redirectAttributes.addFlashAttribute("warning", 
                        "Некоторые позиции не были синхронизированы из-за отсутствия ISIN. Проверьте список проблемных позиций.");
            }

        } catch (Exception e) {
            log.error("Ошибка при синхронизации позиций из ALOR для портфеля {}: {}", id, e.getMessage(), e);
            redirectAttributes.addFlashAttribute("error", 
                    "Ошибка при синхронизации: " + e.getMessage());
        }

        return "redirect:/portfolios/" + id;
    }

    // DTO для формы создания портфеля
    public static class PortfolioForm {
        private String name;
        private String brokerAccount;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getBrokerAccount() {
            return brokerAccount;
        }

        public void setBrokerAccount(String brokerAccount) {
            this.brokerAccount = brokerAccount;
        }
    }
}

