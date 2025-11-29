package com.invest.management.portfolio;

import com.invest.management.moex.MoexStockRepository;
import com.invest.management.moex.bond.BondRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class BrokerReportParser {

    private static final Logger log = LoggerFactory.getLogger(BrokerReportParser.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final BondRepository bondRepository;
    private final MoexStockRepository stockRepository;
    private final com.invest.management.moex.bond.BondDataLoader bondDataLoader;

    public BrokerReportParser(BondRepository bondRepository, 
                             MoexStockRepository stockRepository,
                             com.invest.management.moex.bond.BondDataLoader bondDataLoader) {
        this.bondRepository = bondRepository;
        this.stockRepository = stockRepository;
        this.bondDataLoader = bondDataLoader;
    }

    public static class ParsedReport {
        private LocalDate reportPeriodStart;
        private LocalDate reportPeriodEnd;
        private LocalDate reportCreatedDate;
        private String investorName;
        private String contractNumber;
        private List<PortfolioTransaction> transactions = new ArrayList<>();
        private List<PortfolioCashMovement> cashMovements = new ArrayList<>();
        private List<EndPeriodPosition> endPeriodPositions = new ArrayList<>(); // Позиции на конец периода
        private Map<String, String> securityTypes = new HashMap<>(); // ISIN -> STOCK or BOND

        // Getters and setters
        public LocalDate getReportPeriodStart() { return reportPeriodStart; }
        public void setReportPeriodStart(LocalDate reportPeriodStart) { this.reportPeriodStart = reportPeriodStart; }
        public LocalDate getReportPeriodEnd() { return reportPeriodEnd; }
        public void setReportPeriodEnd(LocalDate reportPeriodEnd) { this.reportPeriodEnd = reportPeriodEnd; }
        public LocalDate getReportCreatedDate() { return reportCreatedDate; }
        public void setReportCreatedDate(LocalDate reportCreatedDate) { this.reportCreatedDate = reportCreatedDate; }
        public String getInvestorName() { return investorName; }
        public void setInvestorName(String investorName) { this.investorName = investorName; }
        public String getContractNumber() { return contractNumber; }
        public void setContractNumber(String contractNumber) { this.contractNumber = contractNumber; }
        public List<PortfolioTransaction> getTransactions() { return transactions; }
        public List<PortfolioCashMovement> getCashMovements() { return cashMovements; }
        public List<EndPeriodPosition> getEndPeriodPositions() { return endPeriodPositions; }
        public Map<String, String> getSecurityTypes() { return securityTypes; }
    }

    public static class EndPeriodPosition {
        private String isin;
        private String securityType;
        private String currency;
        private BigDecimal quantity;
        private String securityName;
        private BigDecimal lastKnownPrice;

        public String getIsin() { return isin; }
        public void setIsin(String isin) { this.isin = isin; }
        public String getSecurityType() { return securityType; }
        public void setSecurityType(String securityType) { this.securityType = securityType; }
        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }
        public BigDecimal getQuantity() { return quantity; }
        public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }
        public String getSecurityName() { return securityName; }
        public void setSecurityName(String securityName) { this.securityName = securityName; }
        public BigDecimal getLastKnownPrice() { return lastKnownPrice; }
        public void setLastKnownPrice(BigDecimal lastKnownPrice) { this.lastKnownPrice = lastKnownPrice; }
    }

    public ParsedReport parse(InputStream htmlStream, String fileName) {
        try {
            Document doc = Jsoup.parse(htmlStream, "UTF-8", "");
            ParsedReport report = new ParsedReport();

            // Парсинг метаданных
            parseMetadata(doc, report);

            // Парсинг справочника ценных бумаг (для определения типа)
            parseSecuritiesReference(doc, report);

            // Парсинг конечных позиций портфеля
            parseEndPeriodPositions(doc, report);

            // Парсинг сделок
            parseTransactions(doc, report);

            // Парсинг движения денежных средств
            parseCashMovements(doc, report);

            return report;
        } catch (Exception e) {
            log.error("Ошибка при парсинге HTML отчета {}: {}", fileName, e.getMessage(), e);
            throw new RuntimeException("Не удалось распарсить отчет: " + e.getMessage(), e);
        }
    }

    private void parseMetadata(Document doc, ParsedReport report) {
        // Парсинг периода отчета из заголовка
        Elements h3Elements = doc.select("h3");
        for (Element h3 : h3Elements) {
            String text = h3.text();
            if (text.contains("за период с")) {
                // Формат: "за период с 01.01.2020 по 31.01.2020, дата создания 05.02.2020"
                String[] parts = text.split("за период с");
                if (parts.length > 1) {
                    String periodPart = parts[1].split(",")[0].trim();
                    String[] dates = periodPart.split(" по ");
                    if (dates.length == 2) {
                        report.setReportPeriodStart(parseDate(dates[0].trim()));
                        report.setReportPeriodEnd(parseDate(dates[1].trim()));
                    }
                }
                if (text.contains("дата создания")) {
                    String[] createdParts = text.split("дата создания");
                    if (createdParts.length > 1) {
                        String createdDateStr = createdParts[1].trim();
                        report.setReportCreatedDate(parseDate(createdDateStr));
                    }
                }
            }
        }

        // Парсинг инвестора и договора
        Elements pElements = doc.select("p");
        for (Element p : pElements) {
            String text = p.text();
            if (text.contains("Инвестор:")) {
                String[] parts = text.split("Инвестор:");
                if (parts.length > 1) {
                    String investorPart = parts[1].split("Договор")[0].trim();
                    report.setInvestorName(investorPart);
                }
            }
            if (text.contains("Договор")) {
                // Извлекаем номер договора из строки типа:
                // "Договор на ведение индивидуального инвестиционного счета S2UUY от 30.12.2019"
                // Нужно получить только "S2UUY"
                String contractNumber = extractContractNumber(text);
                if (contractNumber != null && !contractNumber.isEmpty()) {
                    report.setContractNumber(contractNumber);
                }
            }
        }
    }

    private void parseSecuritiesReference(Document doc, ParsedReport report) {
        log.info("=== Начало парсинга справочника ценных бумаг ===");
        // Ищем таблицу "Справочник Ценных Бумаг" - там есть ISIN и тип инструмента
        Elements tables = doc.select("table");
        log.info("Проверка {} таблиц для поиска справочника", tables.size());
        
        for (int tableIndex = 0; tableIndex < tables.size(); tableIndex++) {
            Element table = tables.get(tableIndex);
            Elements allRows = table.select("tr");
            boolean isReferenceTable = false;
            
            // Проверяем заголовки таблицы (первая строка обычно)
            if (allRows.size() > 0) {
                Elements headerCells = allRows.get(0).select("td, th");
                log.debug("Таблица #{}: проверка {} заголовков", tableIndex + 1, headerCells.size());
                
                for (Element header : headerCells) {
                    String headerText = header.text();
                    // Таблица справочника имеет уникальные колонки: "Вид, Категория, Тип" и "Эмитент"
                    if (headerText.contains("Вид, Категория, Тип") || 
                        (headerText.contains("Эмитент") && headerText.contains("ISIN"))) {
                        isReferenceTable = true;
                        log.info("Найдена таблица справочника ценных бумаг (таблица #{}, заголовок: {})", 
                            tableIndex + 1, headerText);
                        break;
                    }
                }
            }

            if (isReferenceTable) {
                Elements rows = table.select("tr");
                log.info("Найдено строк в таблице справочника: {}", rows.size());
                
                // Пропускаем заголовок (первая строка)
                int processedCount = 0;
                for (int i = 1; i < rows.size(); i++) {
                    Element row = rows.get(i);
                    Elements cells = row.select("td");
                    
                    // Структура: название (0), код (1), ISIN (2), эмитент (3), тип (4), выпуск (5)
                    if (cells.size() >= 5) {
                        String isin = cells.get(2).text().trim();
                        String securityTypeStr = cells.get(4).text().trim();
                        
                        log.debug("Справочник строка #{}: ISIN={}, тип='{}'", i, isin, securityTypeStr);
                        
                        // ИЗМЕНЕНИЕ: убираем ограничение на "RU", обрабатываем любые валидные ISIN (длина 12)
                        if (!isin.isEmpty() && isin.length() == 12) {
                            // Определяем тип из отчета
                            String securityType = "STOCK"; // по умолчанию
                            if (securityTypeStr.contains("Облигация") || securityTypeStr.contains("облигация")) {
                                securityType = "BOND";
                            } else if (securityTypeStr.contains("Акция") || securityTypeStr.contains("акция")) {
                                securityType = "STOCK";
                            } else if (securityTypeStr.contains("пай") || securityTypeStr.contains("ETF") || 
                                       securityTypeStr.contains("расписка") || securityTypeStr.contains("депозитарная")) {
                                // Для ETF, паев, депозитарных расписок используем тип "STOCK"
                                securityType = "STOCK";
                            }
                            
                            log.info("Из справочника отчета: ISIN={}, тип из отчета='{}', определен как {}", 
                                isin, securityTypeStr, securityType);
                            report.getSecurityTypes().put(isin, securityType);
                            processedCount++;
                        }
                    }
                }
                log.info("Обработано записей из справочника: {}", processedCount);
                break; // Нашли нужную таблицу, выходим
            }
        }
        log.info("=== Завершение парсинга справочника. Всего типов определено: {} ===", 
            report.getSecurityTypes().size());
    }


    /**
     * Определяет тип ценной бумаги по ISIN, проверяя справочники MOEX и загружая из API при необходимости
     * @param isin ISIN ценной бумаги
     * @param securityName название ценной бумаги (для сообщения об ошибке)
     * @return "BOND" для облигаций, "STOCK" для акций
     * @throws IllegalArgumentException если инструмент не найден ни в справочниках, ни в MOEX API, ни в отчете
     */
    private String determineSecurityType(String isin, String securityName) {
        return determineSecurityType(isin, securityName, null);
    }
    
    /**
     * Определяет тип ценной бумаги по ISIN, проверяя справочники MOEX и загружая из API при необходимости
     * @param isin ISIN ценной бумаги
     * @param securityName название ценной бумаги (для сообщения об ошибке)
     * @param report отчет, из которого можно взять информацию о типе (может быть null)
     * @return "BOND" для облигаций, "STOCK" для акций
     * @throws IllegalArgumentException если инструмент не найден ни в справочниках, ни в MOEX API, ни в отчете
     */
    private String determineSecurityType(String isin, String securityName, ParsedReport report) {
        log.info("=== Определение типа для ISIN: {}, название: {} ===", isin, securityName);
        
        // Сначала проверяем облигации
        boolean foundInBonds = bondRepository.findByIsin(isin).isPresent();
        log.info("Проверка в moex_bonds для ISIN {}: {}", isin, foundInBonds ? "НАЙДЕНО" : "не найдено");
        if (foundInBonds) {
            log.info("ISIN {} определен как BOND из справочника moex_bonds", isin);
            return "BOND";
        }
        
        // Затем проверяем акции
        boolean foundInStocks = stockRepository.findByIsin(isin).isPresent();
        log.info("Проверка в moex_stocks для ISIN {}: {}", isin, foundInStocks ? "НАЙДЕНО" : "не найдено");
        if (foundInStocks) {
            log.info("ISIN {} определен как STOCK из справочника moex_stocks", isin);
            return "STOCK";
        }
        
        // Если не найдено в справочниках, пытаемся загрузить из MOEX API (только для RU ISIN)
        if (isin != null && isin.startsWith("RU")) {
            log.info("ISIN {} не найден в справочниках, пытаемся загрузить из MOEX API", isin);
            try {
                boolean loaded = bondDataLoader.loadBondByIsin(isin);
                log.info("Результат загрузки из MOEX API для ISIN {}: {}", isin, loaded ? "успешно" : "не удалось");
                
                if (loaded) {
                    // Проверяем снова после загрузки
                    boolean foundAfterLoad = bondRepository.findByIsin(isin).isPresent();
                    log.info("Проверка в moex_bonds после загрузки для ISIN {}: {}", isin, foundAfterLoad ? "НАЙДЕНО" : "не найдено");
                    if (foundAfterLoad) {
                        log.info("ISIN {} успешно загружен из MOEX API и определен как BOND", isin);
                        return "BOND";
                    } else {
                        log.warn("ISIN {} загружен из API, но не найден в репозитории после загрузки", isin);
                    }
                }
            } catch (Exception e) {
                log.error("Ошибка при загрузке ISIN {} из MOEX API: {}", isin, e.getMessage(), e);
            }
        } else if (isin != null && !isin.startsWith("RU")) {
            log.info("ISIN {} не начинается с RU, пропускаем загрузку из MOEX API", isin);
        }
        
        // Если не удалось найти в справочниках MOEX, используем информацию из отчета
        if (report != null && report.getSecurityTypes().containsKey(isin)) {
            String typeFromReport = report.getSecurityTypes().get(isin);
            log.info("ISIN {} не найден в справочниках MOEX, используем тип из отчета: {}", isin, typeFromReport);
            return typeFromReport;
        }
        
        // Для не-RU ISIN (ETF, депозитарные расписки и т.д.) используем "STOCK" по умолчанию
        if (isin != null && !isin.startsWith("RU") && isin.length() == 12) {
            log.info("ISIN {} не начинается с RU, используем тип STOCK по умолчанию", isin);
            return "STOCK";
        }
        
        // Если не удалось найти или загрузить - выбрасываем исключение
        String errorMessage = String.format("Неизвестный инструмент isin = %s", isin);
        if (securityName != null && !securityName.trim().isEmpty()) {
            errorMessage += String.format(" (название: %s)", securityName);
        }
        log.error("=== ОШИБКА: {} ===", errorMessage);
        throw new IllegalArgumentException(errorMessage);
    }

    /**
     * Парсит конечные позиции портфеля из таблицы "Портфель Ценных Бумаг"
     * Колонка 8 (индекс 8) содержит количество на конец периода
     */
    private void parseEndPeriodPositions(Document doc, ParsedReport report) {
        log.info("=== Начало парсинга конечных позиций портфеля ===");
        Elements tables = doc.select("table");
        log.info("Найдено таблиц в документе: {}", tables.size());
        
        int tableIndex = 0;
        for (Element table : tables) {
            tableIndex++;
            log.info("Обработка таблицы #{}", tableIndex);
            
            // Ищем таблицу "Портфель Ценных Бумаг" по уникальным признакам
            // Отличается от "Справочник Ценных Бумаг" наличием колонок "Количество, шт" и "НКД****"
            Elements allRows = table.select("tr");
            boolean isPortfolioTable = false;
            
            // Проверяем заголовки таблицы (первые 2 строки обычно)
            for (int headerRowIndex = 0; headerRowIndex < Math.min(2, allRows.size()); headerRowIndex++) {
                Elements headerCells = allRows.get(headerRowIndex).select("td, th");
                for (Element header : headerCells) {
                    String headerText = header.text();
                    // Таблица портфеля имеет уникальные колонки: "Количество, шт" и "НКД****"
                    if (headerText.contains("Количество, шт") || 
                        (headerText.contains("НКД") && headerText.contains("****")) ||
                        (headerText.contains("Рыночная стоимость") && headerText.contains("НКД"))) {
                        isPortfolioTable = true;
                        log.info("Найдена таблица портфеля ценных бумаг по уникальным колонкам (заголовок: {})", headerText);
                        break;
                    }
                }
                if (isPortfolioTable) break;
            }

            if (isPortfolioTable) {
                // Парсим таблицу: получаем все строки и обрабатываем каждую
                Elements rows = table.select("tr");
                log.info("Найдено строк в таблице портфеля: {}", rows.size());
                
                int processedRows = 0;
                // Пропускаем заголовки (первые 2 строки: 0 и 1)
                for (int i = 2; i < rows.size(); i++) {
                    Element row = rows.get(i);
                    Elements cells = row.select("td");
                    
                    log.info("Обработка строки #{}: {} ячеек", i, cells.size());
                    
                    // Проверяем первую ячейку на colspan или текст заголовка
                    if (cells.size() > 0) {
                        Element firstCell = cells.get(0);
                        String firstCellText = firstCell.text().trim();
                        String colspan = firstCell.attr("colspan");
                        
                        log.info("Строка #{}: первая ячейка='{}', colspan='{}'", i, firstCellText, colspan);
                        
                        // Пропускаем строки-заголовки площадок (colspan) и итоговые строки
                        if (!colspan.isEmpty() || firstCellText.contains("Площадка") || firstCellText.contains("Итого")) {
                            log.info("Пропуск строки #{} (colspan или заголовок площадки/итоговая)", i);
                            continue;
                        }
                    }
                    
                    // В таблице портфеля должно быть минимум 9 ячеек: название, ISIN, валюта, и данные
                    if (cells.size() < 9) {
                        log.warn("Пропуск строки #{}: недостаточно ячеек ({} < 9)", i, cells.size());
                        continue;
                    }

                    // Парсим данные из ячеек таблицы портфеля
                    // Структура: название (0), ISIN (1), валюта (2), 
                    // начало периода: количество (3), номинал (4), цена (5), стоимость (6), НКД (7),
                    // конец периода: количество (8), номинал (9), цена (10), стоимость (11), НКД (12), ...
                    String securityName = cells.get(0).text().trim();
                    String isin = cells.get(1).text().trim();
                    String currency = cells.get(2).text().trim();
                    
                    // Количество на конец периода - это 9-я ячейка (индекс 8)
                    String quantityStr = cells.get(8).text().trim();
                    
                    // Номинал на конец периода - это 10-я ячейка (индекс 9)
                    String nominalStr = cells.size() > 9 ? cells.get(9).text().trim() : "";
                    
                    // Цена на конец периода - это 11-я ячейка (индекс 10)
                    String priceStr = cells.size() > 10 ? cells.get(10).text().trim() : "";
                    
                    log.info("Строка #{}: название='{}', ISIN='{}', валюта='{}', количество='{}', цена='{}'", 
                        i, securityName, isin, currency, quantityStr, priceStr);
                    
                    // ИЗМЕНЕНИЕ: убираем ограничение на "RU", проверяем только валидность ISIN (длина 12)
                    if (!isin.isEmpty() && isin.length() == 12 && !quantityStr.isEmpty()) {
                        BigDecimal quantity = parseDecimal(quantityStr);
                        log.info("Распарсено количество для ISIN {}: {}", isin, quantity);
                        
                        // Пропускаем позиции с нулевым количеством
                        if (quantity.compareTo(BigDecimal.ZERO) > 0) {
                            log.info("Создание позиции для ISIN {}: количество={}", isin, quantity);
                            EndPeriodPosition position = new EndPeriodPosition();
                            position.setIsin(isin);
                            // currency и securityName сохраняем в EndPeriodPosition для передачи данных,
                            // но не устанавливаем напрямую в PortfolioPosition (используются связи с MOEX)
                            position.setCurrency(currency);
                            position.setSecurityName(securityName);
                            
                            // Определяем тип по справочникам MOEX (используем информацию из отчета как fallback)
                            // Делаем это ДО парсинга цены, чтобы знать, нужно ли конвертировать процент в абсолютную цену
                            log.info("Определение типа для позиции ISIN={}, название={}", isin, securityName);
                            String securityType = determineSecurityType(isin, securityName, report);
                            position.setSecurityType(securityType);
                            position.setQuantity(quantity);
                            
                            // Парсим цену на конец периода (если есть)
                            if (!priceStr.isEmpty() && !priceStr.equals("-")) {
                                BigDecimal price = parseDecimal(priceStr);
                                
                                // Для облигаций цена в отчете указана в процентах от номинала
                                // Конвертируем в абсолютную цену: цена_процент * номинал / 100
                                if ("BOND".equals(securityType) && !nominalStr.isEmpty() && !nominalStr.equals("-")) {
                                    BigDecimal nominal = parseDecimal(nominalStr);
                                    if (nominal.compareTo(BigDecimal.ZERO) > 0) {
                                        // Конвертируем процент в абсолютную цену
                                        BigDecimal absolutePrice = price.multiply(nominal)
                                            .divide(new BigDecimal("100"), 6, java.math.RoundingMode.HALF_UP);
                                        position.setLastKnownPrice(absolutePrice);
                                        log.info("Распарсена цена для облигации ISIN {}: {}% от номинала {} = {}", 
                                            isin, price, nominal, absolutePrice);
                                    } else {
                                        position.setLastKnownPrice(price);
                                        log.info("Распарсена цена для ISIN {}: {} (номинал не найден, используем как есть)", isin, price);
                                    }
                                } else {
                                    // Для акций или если номинал не найден, используем цену как есть
                                    position.setLastKnownPrice(price);
                                    log.info("Распарсена цена для ISIN {}: {}", isin, price);
                                }
                            }
                            
                            report.getEndPeriodPositions().add(position);
                            processedRows++;
                            log.info("Добавлена конечная позиция: ISIN={}, тип={}, количество={}, название={}, цена={}", 
                                isin, securityType, quantity, securityName, position.getLastKnownPrice());
                        } else {
                            log.info("Пропуск позиции с нулевым количеством для ISIN {}", isin);
                        }
                    } else {
                        log.warn("Пропуск строки #{}: невалидный ISIN='{}' или пустое количество='{}'", 
                            i, isin, quantityStr);
                    }
                }
                log.info("Обработано позиций из таблицы портфеля: {}", processedRows);
            } else {
                log.debug("Таблица #{} не является таблицей портфеля", tableIndex + 1);
            }
        }
        log.info("=== Завершение парсинга конечных позиций. Всего найдено: {} ===", report.getEndPeriodPositions().size());
    }

    private void parseTransactions(Document doc, ParsedReport report) {
        // Ищем таблицу "Сделки купли/продажи ценных бумаг"
        // Уникальные признаки: "Номер сделки", "Вид" (Покупка/Продажа), "Время заключения"
        Elements tables = doc.select("table");
        for (Element table : tables) {
            Elements headers = table.select("tr:first-child td, tr:first-child th");
            boolean isTransactionTable = false;
            for (Element header : headers) {
                String headerText = header.text();
                // Таблица транзакций имеет уникальные колонки: "Номер сделки", "Вид", "Время заключения"
                if (headerText.contains("Номер сделки") || 
                    (headerText.contains("Вид") && headerText.contains("Время заключения")) ||
                    (headerText.contains("Дата заключения") && headerText.contains("Номер сделки"))) {
                    isTransactionTable = true;
                    log.info("Найдена таблица транзакций по уникальным колонкам: {}", headerText);
                    break;
                }
            }

            if (isTransactionTable) {
                Elements rows = table.select("tr");
                // Пропускаем заголовки (первые 2 строки обычно)
                for (int i = 2; i < rows.size(); i++) {
                    Element row = rows.get(i);
                    Elements cells = row.select("td");
                    
                    // Пропускаем строки-заголовки площадок и итоговые строки
                    if (cells.size() < 10 || cells.get(0).text().contains("Площадка") || 
                        cells.get(0).text().contains("Итого")) {
                        continue;
                    }

                    if (cells.size() >= 10) {
                        PortfolioTransaction transaction = new PortfolioTransaction();
                        
                        transaction.setTradeDate(parseDate(cells.get(0).text().trim()));
                        transaction.setSettlementDate(parseDate(cells.get(1).text().trim()));
                        transaction.setTradeTime(parseTime(cells.get(2).text().trim()));
                        
                        String securityName = cells.get(3).text().trim();
                        String securityCode = cells.get(4).text().trim();
                        transaction.setCurrency(cells.get(5).text().trim());
                        transaction.setOperationType(cells.get(6).text().trim());
                        transaction.setQuantity(parseDecimal(cells.get(7).text().trim()));
                        transaction.setPrice(parseDecimal(cells.get(8).text().trim()));
                        transaction.setAmount(parseDecimal(cells.get(9).text().trim()));
                        
                        if (cells.size() > 10) {
                            transaction.setAccruedInterest(parseDecimal(cells.get(10).text().trim()));
                        }
                        if (cells.size() > 11) {
                            transaction.setBrokerCommission(parseDecimal(cells.get(11).text().trim()));
                        }
                        if (cells.size() > 12) {
                            transaction.setExchangeCommission(parseDecimal(cells.get(12).text().trim()));
                        }
                        if (cells.size() > 13) {
                            String tradeNumber = cells.get(13).text().trim();
                            transaction.setTradeNumber(tradeNumber);
                            log.debug("Номер сделки для транзакции: {}", tradeNumber);
                        }

                        // Определяем ISIN: если код является валидным ISIN (длина 12), используем его
                        // Иначе ищем в таблице портфеля или справочнике
                        String isin;
                        // Проверяем, является ли код валидным ISIN (любой страны, длина 12)
                        if (securityCode.length() == 12 && 
                            securityCode.matches("^[A-Z]{2}[A-Z0-9]{9}[0-9]$")) {
                            // Валидный ISIN (любой страны)
                            isin = securityCode;
                            log.info("Используем код как ISIN: {}", isin);
                        } else if (securityCode.startsWith("RU") && securityCode.length() == 12) {
                            // Для обратной совместимости с RU ISIN без полной валидации
                            isin = securityCode;
                            log.info("Используем код как ISIN: {}", isin);
                        } else {
                            log.info("Поиск ISIN для бумаги: название={}, код={}", securityName, securityCode);
                            isin = findIsinBySecurityName(securityName, securityCode, doc);
                            log.info("Найден ISIN для бумаги {} / {}: {}", securityName, securityCode, isin);
                        }
                        
                        // ИЗМЕНЕНИЕ: проверяем валидность ISIN (длина 12), а не только RU
                        if (isin == null || isin.isBlank() || isin.length() != 12) {
                            String errorMsg = String.format("Не удалось определить ISIN для бумаги: название=%s, код=%s", 
                                securityName, securityCode);
                            log.error(errorMsg);
                            throw new IllegalArgumentException(errorMsg);
                        }
                        
                        transaction.setIsin(isin);
                        // Определяем тип по справочникам MOEX (если еще не определен в parseSecuritiesReference)
                        String securityType = report.getSecurityTypes().get(isin);
                        if (securityType == null) {
                            log.info("Определение типа для транзакции: ISIN={}, название={}", isin, securityName);
                            securityType = determineSecurityType(isin, securityName, report);
                            report.getSecurityTypes().put(isin, securityType);
                        }
                        transaction.setSecurityType(securityType);

                        report.getTransactions().add(transaction);
                    }
                }
            }
        }
    }

    private void parseCashMovements(Document doc, ParsedReport report) {
        // Ищем таблицу "Движение денежных средств за период"
        // Уникальные признаки: "Сумма зачисления", "Сумма списания"
        Elements tables = doc.select("table");
        for (Element table : tables) {
            Elements headerRows = table.select("tr:first-child");
            boolean isCashMovementTable = false;
            
            if (!headerRows.isEmpty()) {
                Element headerRow = headerRows.get(0);
                Elements headers = headerRow.select("td, th");
                // Собираем весь текст заголовков в одну строку
                StringBuilder headerTextBuilder = new StringBuilder();
                for (Element header : headers) {
                    headerTextBuilder.append(header.text()).append(" ");
                }
                String allHeadersText = headerTextBuilder.toString();
                
                // Таблица движений денежных средств имеет уникальные колонки: "Сумма зачисления", "Сумма списания"
                if (allHeadersText.contains("Сумма зачисления") && allHeadersText.contains("Сумма списания")) {
                    isCashMovementTable = true;
                    log.info("Найдена таблица движений денежных средств по уникальным колонкам: {}", allHeadersText.trim());
                }
            }

            if (isCashMovementTable) {
                Elements rows = table.select("tr");
                for (int i = 1; i < rows.size(); i++) {
                    Element row = rows.get(i);
                    Elements cells = row.select("td");
                    
                    if (cells.size() >= 6 && !cells.get(0).text().contains("Итого")) {
                        PortfolioCashMovement movement = new PortfolioCashMovement();
                        
                        movement.setDate(parseDate(cells.get(0).text().trim()));
                        movement.setTradingPlatform(cells.get(1).text().trim());
                        movement.setDescription(cells.get(2).text().trim());
                        movement.setCurrency(cells.get(3).text().trim());
                        movement.setCreditAmount(parseDecimal(cells.get(4).text().trim()));
                        movement.setDebitAmount(parseDecimal(cells.get(5).text().trim()));

                        report.getCashMovements().add(movement);
                    }
                }
                break; // Нашли нужную таблицу, выходим из цикла
            }
        }
    }

    private String findIsinBySecurityName(String securityName, String securityCode, Document doc) {
        log.info("=== Поиск ISIN для бумаги: название='{}', код='{}' ===", securityName, securityCode);
        
        // Ищем в таблице портфеля ценных бумаг (ISIN во второй колонке)
        Elements tables = doc.select("table");
        log.info("Проверка {} таблиц для поиска ISIN", tables.size());
        
        for (Element table : tables) {
            Elements headers = table.select("tr:first-child td, tr:first-child th");
            boolean isPortfolioTable = false;
            for (Element header : headers) {
                if (header.text().contains("ISIN ценной бумаги")) {
                    isPortfolioTable = true;
                    log.info("Найдена таблица портфеля для поиска ISIN");
                    break;
                }
            }

            if (isPortfolioTable) {
                Elements rows = table.select("tr");
                log.info("Проверка {} строк в таблице портфеля для поиска ISIN", rows.size());
                
                for (int i = 2; i < rows.size(); i++) {
                    Element row = rows.get(i);
                    Elements cells = row.select("td");
                    
                    log.debug("Проверка строки #{} для поиска ISIN: {} ячеек", i, cells.size());
                    
                    if (cells.size() >= 2) {
                        String firstCellText = cells.get(0).text().trim();
                        
                        // Пропускаем строки-заголовки площадок и итоговые строки
                        if (firstCellText.contains("Площадка") || firstCellText.contains("Итого")) {
                            log.debug("Пропуск строки #{} при поиске ISIN (заголовок площадки или итоговая)", i);
                            continue;
                        }
                        
                        String name = firstCellText;
                        String isin = cells.get(1).text().trim();
                        
                        log.info("Проверка строки #{}: название='{}', ISIN='{}' (ищем: '{}')", i, name, isin, securityName);
                        
                        // Сравниваем по названию (точное совпадение или частичное)
                        boolean nameMatches = name.equalsIgnoreCase(securityName) || 
                                            name.contains(securityName) || 
                                            securityName.contains(name) ||
                                            // Также пробуем сравнить без пробелов и в верхнем регистре
                                            name.replaceAll("\\s+", "").equalsIgnoreCase(securityName.replaceAll("\\s+", ""));
                        
                        log.info("Сравнение названий: '{}' vs '{}' -> совпадение: {}", name, securityName, nameMatches);
                        
                        // ИЗМЕНЕНИЕ: убираем ограничение на "RU", ищем любые валидные ISIN (длина 12)
                        if (nameMatches && !isin.isEmpty() && isin.length() == 12) {
                            log.info("Найден ISIN для бумаги '{}' / '{}': {}", securityName, securityCode, isin);
                            return isin;
                        } else if (nameMatches) {
                            log.warn("Название совпадает, но ISIN невалиден: '{}' (длина: {})", isin, isin.length());
                        }
                    } else {
                        log.debug("Строка #{} имеет недостаточно ячеек для поиска ISIN: {}", i, cells.size());
                    }
                }
            }
            
            // Также проверяем таблицу "Справочник Ценных Бумаг" для поиска ISIN
            // В ней структура: название (0), код (1), ISIN (2), ...
            Elements allRows = table.select("tr");
            boolean isReferenceTable = false;
            
            // Проверяем заголовки таблицы (первая строка обычно)
            if (allRows.size() > 0) {
                Elements headerCells = allRows.get(0).select("td, th");
                for (Element header : headerCells) {
                    String text = header.text();
                    // Таблица справочника имеет уникальные колонки: "Вид, Категория, Тип" и "Эмитент"
                    if (text.contains("Вид, Категория, Тип") || 
                        (text.contains("Эмитент") && text.contains("ISIN"))) {
                        isReferenceTable = true;
                        log.info("Найдена таблица справочника для поиска ISIN");
                        break;
                    }
                }
            }
            
            if (isReferenceTable) {
                Elements rows = table.select("tr");
                log.info("Проверка {} строк в таблице справочника для поиска ISIN", rows.size());
                
                for (int i = 1; i < rows.size(); i++) {
                    Element row = rows.get(i);
                    Elements cells = row.select("td");
                    
                    if (cells.size() >= 3) {
                        String name = cells.get(0).text().trim();
                        String code = cells.get(1).text().trim();
                        String isin = cells.get(2).text().trim();
                        
                        log.info("Проверка справочника строка #{}: название='{}', код='{}', ISIN='{}' (ищем: '{}' / '{}')", 
                            i, name, code, isin, securityName, securityCode);
                        
                        // Сравниваем по названию или коду
                        boolean matches = (name.equalsIgnoreCase(securityName) || 
                                         name.contains(securityName) || 
                                         securityName.contains(name)) ||
                                        (code.equalsIgnoreCase(securityCode));
                        
                        log.info("Сравнение для справочника: название совпадает={}, код совпадает={}", 
                            name.equalsIgnoreCase(securityName) || name.contains(securityName) || securityName.contains(name),
                            code.equalsIgnoreCase(securityCode));
                        
                        // ИЗМЕНЕНИЕ: убираем ограничение на "RU", ищем любые валидные ISIN (длина 12)
                        if (matches && !isin.isEmpty() && isin.length() == 12) {
                            log.info("Найден ISIN в справочнике для бумаги '{}' / '{}': {}", securityName, securityCode, isin);
                            return isin;
                        }
                    }
                }
            }
        }
        
        // Если не нашли, выбрасываем исключение
        String errorMsg = String.format("Не найден ISIN для бумаги: название='%s', код='%s'", securityName, securityCode);
        log.error("=== ОШИБКА: {} ===", errorMsg);
        throw new IllegalArgumentException(errorMsg);
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr.trim(), DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            log.warn("Не удалось распарсить дату: {}", dateStr);
            return null;
        }
    }

    private LocalTime parseTime(String timeStr) {
        if (timeStr == null || timeStr.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(timeStr.trim(), TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            log.warn("Не удалось распарсить время: {}", timeStr);
            return null;
        }
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null || value.isBlank() || value.equals("-")) {
            return BigDecimal.ZERO;
        }
        try {
            // Убираем пробелы (разделители тысяч) и заменяем запятую на точку
            String cleaned = value.replaceAll("\\s+", "").replace(",", ".");
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            log.warn("Не удалось распарсить число: {}", value);
            return BigDecimal.ZERO;
        }
    }

    /**
     * Извлекает номер договора из строки типа:
     * "Договор на ведение индивидуального инвестиционного счета S2UUY от 30.12.2019"
     * Возвращает только номер договора (например, "S2UUY")
     */
    private String extractContractNumber(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        
        // Ищем паттерн: "счета " + номер договора + " от "
        String[] parts = text.split("счета ");
        if (parts.length > 1) {
            String afterSchet = parts[1].trim();
            // Берем текст до " от " или до конца строки
            String[] contractParts = afterSchet.split(" от ");
            if (contractParts.length > 0) {
                String contractNumber = contractParts[0].trim();
                // Убираем возможные лишние пробелы
                contractNumber = contractNumber.replaceAll("\\s+", " ").trim();
                return contractNumber;
            }
        }
        
        // Fallback: если паттерн не найден, пытаемся найти после "Договор"
        if (text.contains("Договор")) {
            String[] parts2 = text.split("Договор");
            if (parts2.length > 1) {
                String afterDogovor = parts2[1].trim();
                String[] contractParts = afterDogovor.split(" от ");
                if (contractParts.length > 0) {
                    // Берем последнее слово перед " от " (это должен быть номер договора)
                    String lastPart = contractParts[0].trim();
                    String[] words = lastPart.split("\\s+");
                    if (words.length > 0) {
                        return words[words.length - 1]; // Последнее слово
                    }
                }
            }
        }
        
        return null;
    }
}


