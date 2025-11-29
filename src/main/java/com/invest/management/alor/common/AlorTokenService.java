package com.invest.management.alor.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.invest.management.alor.AlorUserToken;
import com.invest.management.alor.AlorUserTokenRepository;
import com.invest.management.portfolio.PortfolioRepository;
import com.invest.management.user.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AlorTokenService {

    private static final Logger log = LoggerFactory.getLogger(AlorTokenService.class);
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AlorUserTokenRepository tokenRepository;
    private final PortfolioRepository portfolioRepository;
    private final TextEncryptor encryptor;
    
    // Кэш Access Token: ключ = userId:environment, значение = (token, expiresAt)
    private final Map<String, CachedToken> accessTokenCache = new ConcurrentHashMap<>();
    
    @Value("${alor.api.test-base-url:https://apidev.alor.ru}")
    private String testBaseUrl;
    
    @Value("${alor.api.production-base-url:https://api.alor.ru}")
    private String productionBaseUrl;
    
    @Value("${alor.api.test-oauth-url:https://oauthdev.alor.ru}")
    private String testOAuthUrl;
    
    @Value("${alor.api.production-oauth-url:https://oauth.alor.ru}")
    private String productionOAuthUrl;
    
    @Value("${alor.api.encryption.password:}")
    private String encryptionPassword;
    
    @Value("${alor.api.encryption.salt:}")
    private String encryptionSalt;

    public AlorTokenService(RestTemplate alorRestTemplate,
                            ObjectMapper objectMapper,
                            AlorUserTokenRepository tokenRepository,
                            PortfolioRepository portfolioRepository,
                            @Value("${alor.api.encryption.password:}") String encryptionPassword,
                            @Value("${alor.api.encryption.salt:}") String encryptionSalt) {
        this.restTemplate = alorRestTemplate;
        this.objectMapper = objectMapper;
        this.tokenRepository = tokenRepository;
        this.portfolioRepository = portfolioRepository;
        
        // Инициализация шифровальщика
        // Используем AES256 с ключом из конфигурации
        // ВАЖНО: password и salt должны быть заданы в application.properties или переменных окружения
        // Encryptors.standard() требует hex-формат, поэтому конвертируем строки в hex
        if (encryptionPassword == null || encryptionPassword.isEmpty()) {
            log.warn("ALOR encryption password не задан! Токены будут храниться незашифрованными.");
            this.encryptor = null;
        } else {
            // Конвертируем строки паролей в hex-формат для Encryptors.standard()
            String passwordHex = stringToHex(encryptionPassword);
            String saltHex = encryptionSalt != null && !encryptionSalt.isEmpty() 
                    ? stringToHex(encryptionSalt) 
                    : stringToHex("defaultSalt");
            this.encryptor = Encryptors.text(passwordHex, saltHex);
        }
        this.encryptionPassword = encryptionPassword;
        this.encryptionSalt = encryptionSalt;
    }

    /**
     * Сохраняет Refresh Token для пользователя с шифрованием
     */
    public void saveRefreshToken(AppUser user, String environment, String refreshToken, 
                                 String tradeServerCode, Long portfolioId) {
        String encrypted = encrypt(refreshToken);
        
        Optional<AlorUserToken> existing = tokenRepository.findByUserAndEnvironment(user, environment);
        AlorUserToken token;
        
        if (existing.isPresent()) {
            token = existing.get();
            token.setEncryptedRefreshToken(encrypted);
            if (tradeServerCode != null) {
                token.setTradeServerCode(tradeServerCode);
            }
            if (portfolioId != null) {
                portfolioRepository.findById(portfolioId).ifPresent(token::setPortfolio);
            }
        } else {
            token = new AlorUserToken();
            token.setUser(user);
            token.setEnvironment(environment);
            token.setEncryptedRefreshToken(encrypted);
            token.setTradeServerCode(tradeServerCode);
        }
        
        tokenRepository.save(token);
        log.info("Refresh Token сохранен для пользователя {} в окружении {}", user.getEmail(), environment);
    }

    /**
     * Получает и расшифровывает Refresh Token для пользователя
     */
    public Optional<String> getRefreshToken(AppUser user, String environment) {
        Optional<AlorUserToken> tokenOpt = tokenRepository.findByUserAndEnvironment(user, environment);
        if (tokenOpt.isEmpty()) {
            return Optional.empty();
        }
        
        AlorUserToken token = tokenOpt.get();
        String decrypted = decrypt(token.getEncryptedRefreshToken());
        return Optional.of(decrypted);
    }

    /**
     * Получает Access Token используя Refresh Token
     * Использует кэширование - токен запрашивается только если истек или отсутствует
     * 
     * Согласно документации ALOR API:
     * - Endpoint: /refresh на OAuth сервере
     * - Метод: POST с query параметром token
     * - Тестовый контур: https://oauthdev.alor.ru/refresh?token=...
     * - Боевой контур: https://oauth.alor.ru/refresh?token=...
     * - Access Token действителен 30 минут (1800 секунд)
     */
    public Optional<AccessTokenResponse> getAccessToken(AppUser user, String environment) {
        String cacheKey = user.getId() + ":" + environment;
        
        // Проверяем кэш
        CachedToken cached = accessTokenCache.get(cacheKey);
        if (cached != null && cached.isValid()) {
            log.debug("Использован кэшированный Access Token для пользователя {} (окружение: {}), истекает: {}", 
                    user.getEmail(), environment, cached.getExpiresAt());
            return Optional.of(new AccessTokenResponse(cached.getToken(), cached.getRemainingSeconds()));
        }
        
        // Если токен истек или отсутствует, запрашиваем новый
        if (cached != null) {
            log.info("Кэшированный Access Token истек для пользователя {} (окружение: {}), запрашиваем новый", 
                    user.getEmail(), environment);
        } else {
            log.info("Access Token не найден в кэше для пользователя {} (окружение: {}), запрашиваем новый", 
                    user.getEmail(), environment);
        }
        
        Optional<String> refreshTokenOpt = getRefreshToken(user, environment);
        if (refreshTokenOpt.isEmpty()) {
            log.warn("Refresh Token не найден для пользователя {} в окружении {}", user.getEmail(), environment);
            return Optional.empty();
        }
        
        String refreshToken = refreshTokenOpt.get();
        // Используем OAuth сервер, а не основной API сервер
        String oauthUrl = "test".equals(environment) ? testOAuthUrl : productionOAuthUrl;
        // Согласно документации: POST запрос с query параметром token
        // Важно: токен нужно URL-кодировать
        String encodedToken = URLEncoder.encode(refreshToken, StandardCharsets.UTF_8);
        String url = oauthUrl + "/refresh?token=" + encodedToken;
        
        log.info("Запрос Access Token для окружения {}: {}", environment, oauthUrl + "/refresh?token=***");
        log.debug("Полный URL (без токена): {}", oauthUrl + "/refresh");
        
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            // Для POST запроса с query параметрами и пустым телом Content-Type не устанавливаем
            // Interceptor также не установит его для запросов без тела
            
            // POST запрос с пустым телом (токен передается в query параметре)
            HttpEntity<Void> request = new HttpEntity<>(headers);
            // Согласно документации ALOR: POST запрос с query параметром token
            log.debug("Отправка POST запроса на {}", url.replace(refreshToken, "***"));
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, request, String.class);
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode json = objectMapper.readTree(response.getBody());
                String accessToken = json.has("AccessToken") ? json.get("AccessToken").asText() : null;
                
                if (accessToken != null) {
                    log.info("Access Token успешно получен для пользователя {} в окружении {}", 
                            user.getEmail(), environment);
                    // Access Token действителен 30 минут (1800 секунд) согласно документации
                    // Сохраняем в кэш с запасом в 1 минуту (1740 секунд), чтобы обновить до истечения
                    int expiresInSeconds = 1800;
                    int cacheExpiresInSeconds = 1740; // Обновляем за 1 минуту до истечения
                    OffsetDateTime expiresAt = OffsetDateTime.now().plusSeconds(cacheExpiresInSeconds);
                    accessTokenCache.put(cacheKey, new CachedToken(accessToken, expiresAt));
                    
                    return Optional.of(new AccessTokenResponse(accessToken, expiresInSeconds));
                } else {
                    log.warn("Access Token не найден в ответе: {}", response.getBody());
                }
            } else {
                log.error("Не удалось получить Access Token: статус {}, тело: {}", 
                        response.getStatusCode(), response.getBody());
            }
            
            return Optional.empty();
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            // Обработка HTTP ошибок (400, 401, 403 и т.д.)
            log.error("HTTP ошибка при получении Access Token для пользователя {}: статус {}, тело: {}", 
                    user.getEmail(), e.getStatusCode(), e.getResponseBodyAsString(), e);
            // Удаляем невалидный токен из кэша
            accessTokenCache.remove(cacheKey);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Ошибка при получении Access Token для пользователя {}: {}", 
                    user.getEmail(), e.getMessage(), e);
            // Удаляем невалидный токен из кэша
            accessTokenCache.remove(cacheKey);
            return Optional.empty();
        }
    }

    /**
     * Конвертирует строку в hex-формат для использования с Encryptors.standard()
     */
    private String stringToHex(String str) {
        if (str == null) {
            return "";
        }
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    /**
     * Шифрует токен перед сохранением в БД
     * TextEncryptor уже возвращает строку, поэтому Base64 кодирование не требуется
     */
    private String encrypt(String plainText) {
        if (encryptor == null) {
            log.warn("Шифрование отключено, токен сохраняется в открытом виде!");
            return plainText;
        }
        try {
            // TextEncryptor.encrypt() возвращает уже закодированную строку
            return encryptor.encrypt(plainText);
        } catch (Exception e) {
            log.error("Ошибка при шифровании токена: {}", e.getMessage(), e);
            throw new RuntimeException("Не удалось зашифровать токен", e);
        }
    }

    /**
     * Декодирует JWT токен и извлекает список портфелей
     * Согласно документации ALOR, список портфелей содержится в Access Token (JWT)
     */
    public List<String> getPortfoliosFromToken(AppUser user, String environment) {
        Optional<AccessTokenResponse> tokenResponse = getAccessToken(user, environment);
        if (tokenResponse.isEmpty()) {
            log.warn("Не удалось получить Access Token для извлечения портфелей");
            return Collections.emptyList();
        }
        
        String token = tokenResponse.get().getAccessToken();
        try {
            // JWT формат: header.payload.signature
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                log.warn("Неверный формат JWT токена: ожидается 3 части, получено {}", parts.length);
                return Collections.emptyList();
            }
            
            // Декодируем payload (вторая часть)
            // Base64 URL-safe декодирование
            String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JsonNode json = objectMapper.readTree(payload);
            
            log.debug("Декодированный JWT payload: {}", json);
            
            // Извлекаем список портфелей (согласно документации ALOR, поле называется "portfolios")
            if (json.has("portfolios")) {
                JsonNode portfoliosNode = json.get("portfolios");
                List<String> portfolios = new ArrayList<>();
                
                if (portfoliosNode.isArray()) {
                    // Если это массив
                    for (JsonNode portfolio : portfoliosNode) {
                        portfolios.add(portfolio.asText());
                    }
                } else if (portfoliosNode.isTextual()) {
                    // Если это строка с пробелами, разделяем
                    String portfoliosStr = portfoliosNode.asText();
                    if (!portfoliosStr.trim().isEmpty()) {
                        portfolios.addAll(Arrays.asList(portfoliosStr.trim().split("\\s+")));
                    }
                }
                
                log.info("Найдено {} портфелей в JWT токене для пользователя {}", portfolios.size(), user.getEmail());
                return portfolios;
            }
            
            log.warn("Поле 'portfolios' не найдено в JWT токене. Доступные поля: {}", json.fieldNames());
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Ошибка при декодировании JWT токена для пользователя {}: {}", 
                    user.getEmail(), e.getMessage(), e);
            return Collections.emptyList();
        }
    }
    
    /**
     * Декодирует JWT токен и возвращает весь payload для просмотра
     */
    public Optional<JsonNode> decodeTokenPayload(AppUser user, String environment) {
        Optional<AccessTokenResponse> tokenResponse = getAccessToken(user, environment);
        if (tokenResponse.isEmpty()) {
            return Optional.empty();
        }
        
        String token = tokenResponse.get().getAccessToken();
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return Optional.empty();
            }
            
            String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            return Optional.of(objectMapper.readTree(payload));
        } catch (Exception e) {
            log.error("Ошибка при декодировании JWT payload: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    /**
     * Расшифровывает токен из БД
     * TextEncryptor работает со строками напрямую
     */
    private String decrypt(String encryptedText) {
        if (encryptor == null) {
            log.warn("Шифрование отключено, возвращаем токен как есть");
            return encryptedText;
        }
        try {
            // TextEncryptor.decrypt() принимает зашифрованную строку и возвращает расшифрованную
            return encryptor.decrypt(encryptedText);
        } catch (Exception e) {
            log.error("Ошибка при расшифровке токена: {}", e.getMessage(), e);
            throw new RuntimeException("Не удалось расшифровать токен", e);
        }
    }

    /**
     * DTO для ответа с Access Token
     */
    public static class AccessTokenResponse {
        private final String accessToken;
        private final Integer expiresIn;
        private final Instant expiresAt;

        public AccessTokenResponse(String accessToken, Integer expiresIn) {
            this.accessToken = accessToken;
            this.expiresIn = expiresIn;
            this.expiresAt = expiresIn != null ? 
                    Instant.now().plusSeconds(expiresIn) : null;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public Integer getExpiresIn() {
            return expiresIn;
        }

        public Instant getExpiresAt() {
            return expiresAt;
        }

        public boolean isExpired() {
            return expiresAt != null && Instant.now().isAfter(expiresAt);
        }
    }

    /**
     * Внутренний класс для кэширования Access Token
     */
    private static class CachedToken {
        private final String token;
        private final OffsetDateTime expiresAt;

        public CachedToken(String token, OffsetDateTime expiresAt) {
            this.token = token;
            this.expiresAt = expiresAt;
        }

        public String getToken() {
            return token;
        }

        public OffsetDateTime getExpiresAt() {
            return expiresAt;
        }

        public boolean isValid() {
            return OffsetDateTime.now().isBefore(expiresAt);
        }

        public int getRemainingSeconds() {
            if (!isValid()) {
                return 0;
            }
            long seconds = java.time.Duration.between(OffsetDateTime.now(), expiresAt).getSeconds();
            return (int) Math.max(0, seconds);
        }
    }
}

