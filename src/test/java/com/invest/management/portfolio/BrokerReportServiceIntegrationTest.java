package com.invest.management.portfolio;

import com.invest.management.user.AppUser;
import com.invest.management.user.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BrokerReportServiceIntegrationTest {

    @Autowired
    private BrokerReportService brokerReportService;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private PortfolioPositionRepository positionRepository;

    @Autowired
    private PortfolioTransactionRepository transactionRepository;

    @Autowired
    private PortfolioCashMovementRepository cashMovementRepository;

    @Autowired
    private BrokerReportRepository brokerReportRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private AppUser testUser;
    private Portfolio testPortfolio;

    @BeforeEach
    void setUp() {
        // Очистка данных перед каждым тестом
        positionRepository.deleteAll();
        transactionRepository.deleteAll();
        cashMovementRepository.deleteAll();
        brokerReportRepository.deleteAll();
        portfolioRepository.deleteAll();
        appUserRepository.deleteAll();

        // Создание тестового пользователя
        testUser = new AppUser();
        testUser.setEmail("test@example.com");
        testUser.setPasswordHash(passwordEncoder.encode("password"));
        testUser.setRole("USER");
        testUser = appUserRepository.save(testUser);

        // Создание тестового портфеля
        testPortfolio = new Portfolio();
        testPortfolio.setUser(testUser);
        testPortfolio.setName("Test Portfolio");
        testPortfolio = portfolioRepository.save(testPortfolio);
    }

    private MockMultipartFile loadReportFile(String fileName) throws IOException {
        String filePath = "c:/Users/user/Downloads/" + fileName;
        byte[] content = Files.readAllBytes(Paths.get(filePath));
        return new MockMultipartFile("file", fileName, "text/html", content);
    }

    @Test
    void testLoadMonthlyReportFirst_ThenDailyReport_ShouldRejectDaily() throws Exception {
        // Загрузка месячного отчета
        MockMultipartFile monthlyReport = loadReportFile("61804403ID1V42UUY_01012020_31012020.html");
        brokerReportService.processReport(testPortfolio, monthlyReport, testUser);

        // Проверка данных после месячного отчета
        assertThat(positionRepository.findByPortfolio(testPortfolio)).hasSize(4);
        assertThat(transactionRepository.findByPortfolioOrderByTradeDateAsc(testPortfolio)).hasSize(6);
        
        // Проверка движений денежных средств
        assertThat(cashMovementRepository.findByPortfolio(testPortfolio)).hasSize(8);
        assertThat(brokerReportRepository.findByPortfolioOrderByReportPeriodStartDesc(testPortfolio)).hasSize(1);

        // Попытка загрузить дневной отчет за тот же период (15.01.2020 входит в период 01.01-31.01)
        MockMultipartFile dailyReport = loadReportFile("59176076ID1V42UUY_15012020.html");
        
        assertThatThrownBy(() -> 
            brokerReportService.processReport(testPortfolio, dailyReport, testUser)
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("пересекается");

        // Проверка, что данные не изменились
        assertThat(positionRepository.findByPortfolio(testPortfolio)).hasSize(4);
        assertThat(transactionRepository.findByPortfolioOrderByTradeDateAsc(testPortfolio)).hasSize(6);
        
        // Проверка движений денежных средств
        assertThat(cashMovementRepository.findByPortfolio(testPortfolio)).hasSize(8);
        assertThat(brokerReportRepository.findByPortfolioOrderByReportPeriodStartDesc(testPortfolio)).hasSize(1);
    }

    @Test
    void testLoadDailyReportFirst_ThenMonthlyReport_ShouldAcceptBoth() throws Exception {
        // Загрузка дневного отчета
        MockMultipartFile dailyReport = loadReportFile("59176076ID1V42UUY_15012020.html");
        brokerReportService.processReport(testPortfolio, dailyReport, testUser);

        // Проверка данных после дневного отчета
        assertThat(positionRepository.findByPortfolio(testPortfolio)).hasSize(3); // Без АВТОБФ БП2
        assertThat(transactionRepository.findByPortfolioOrderByTradeDateAsc(testPortfolio)).hasSize(4);
        assertThat(cashMovementRepository.findByPortfolio(testPortfolio)).hasSize(0); // В дневном отчете нет таблицы движений денежных средств
        assertThat(brokerReportRepository.findByPortfolioOrderByReportPeriodStartDesc(testPortfolio)).hasSize(1);

        // Загрузка месячного отчета (должен быть принят, т.к. период другой)
        MockMultipartFile monthlyReport = loadReportFile("61804403ID1V42UUY_01012020_31012020.html");
        brokerReportService.processReport(testPortfolio, monthlyReport, testUser);

        // Проверка финальных данных (месячный отчет перезаписывает позиции)
        assertThat(positionRepository.findByPortfolio(testPortfolio)).hasSize(4);
        // Транзакции накапливаются, но дубликаты по trade_number пропускаются
        assertThat(transactionRepository.findByPortfolioOrderByTradeDateAsc(testPortfolio)).hasSize(6);
        
        // Проверка движений денежных средств
        assertThat(cashMovementRepository.findByPortfolio(testPortfolio)).hasSize(8);
        assertThat(brokerReportRepository.findByPortfolioOrderByReportPeriodStartDesc(testPortfolio)).hasSize(2);
    }

    @Test
    void testLoadMonthlyReport_VerifyAllDataCorrect() throws Exception {
        MockMultipartFile monthlyReport = loadReportFile("61804403ID1V42UUY_01012020_31012020.html");
        brokerReportService.processReport(testPortfolio, monthlyReport, testUser);

        // Проверка позиций
        List<PortfolioPosition> positions = positionRepository.findByPortfolio(testPortfolio);
        assertThat(positions).hasSize(4);
        
        // Проверка конкретных позиций
        PortfolioPosition bondPosition = positions.stream()
            .filter(p -> "RU000A100733".equals(p.getIsin()))
            .findFirst()
            .orElseThrow();
        assertThat(bondPosition.getSecurityType()).isEqualTo("BOND");
        assertThat(bondPosition.getCurrency()).isEqualTo("RUB");
        assertThat(bondPosition.getQuantity()).isEqualByComparingTo(new BigDecimal("30"));

        PortfolioPosition afltPosition = positions.stream()
            .filter(p -> "RU0009062285".equals(p.getIsin()))
            .findFirst()
            .orElseThrow();
        assertThat(afltPosition.getSecurityType()).isEqualTo("STOCK");
        assertThat(afltPosition.getQuantity()).isEqualByComparingTo(new BigDecimal("9"));

        // Проверка транзакций
        List<PortfolioTransaction> transactions = transactionRepository.findByPortfolioOrderByTradeDateAsc(testPortfolio);
        assertThat(transactions).hasSize(6);
        
        // Первая транзакция - продажа ММК
        PortfolioTransaction firstTransaction = transactions.get(0);
        assertThat(firstTransaction.getIsin()).isEqualTo("RU0009084396");
        assertThat(firstTransaction.getOperationType()).isEqualTo("Продажа");
        assertThat(firstTransaction.getTradeDate()).isEqualTo(LocalDate.of(2020, 1, 15));
        assertThat(firstTransaction.getSettlementDate()).isEqualTo(LocalDate.of(2020, 1, 17));
        assertThat(firstTransaction.getTradeTime()).isEqualTo(LocalTime.of(10, 43, 29));
        assertThat(firstTransaction.getQuantity()).isEqualByComparingTo(new BigDecimal("300"));
        assertThat(firstTransaction.getPrice()).isEqualByComparingTo(new BigDecimal("43.79"));
        assertThat(firstTransaction.getAmount()).isEqualByComparingTo(new BigDecimal("13137.00"));
        assertThat(firstTransaction.getBrokerCommission()).isEqualByComparingTo(new BigDecimal("13.14"));
        assertThat(firstTransaction.getExchangeCommission()).isEqualByComparingTo(new BigDecimal("1.22"));
        assertThat(firstTransaction.getTradeNumber()).isEqualTo("3071727764");

        // Последняя транзакция - покупка АВТОБФ БП2
        PortfolioTransaction lastTransaction = transactions.get(5);
        assertThat(lastTransaction.getIsin()).isEqualTo("RU000A100733");
        assertThat(lastTransaction.getOperationType()).isEqualTo("Покупка");
        assertThat(lastTransaction.getAccruedInterest()).isEqualByComparingTo(new BigDecimal("320.04"));
        assertThat(lastTransaction.getTradeNumber()).isEqualTo("3075388727");

        // Проверка движений денежных средств
        List<PortfolioCashMovement> movements = cashMovementRepository.findByPortfolio(testPortfolio);
        assertThat(movements).hasSize(8);
        
        // Первое движение - зачисление от сделки
        PortfolioCashMovement firstMovement = movements.stream()
            .filter(m -> m.getCreditAmount() != null && m.getCreditAmount().compareTo(BigDecimal.ZERO) > 0)
            .findFirst()
            .orElseThrow();
        assertThat(firstMovement.getDate()).isEqualTo(LocalDate.of(2020, 1, 17));
        assertThat(firstMovement.getTradingPlatform()).isEqualTo("Фондовый рынок");
        assertThat(firstMovement.getDescription()).contains("Сделка от 15.01.2020");
        assertThat(firstMovement.getCreditAmount()).isEqualByComparingTo(new BigDecimal("32636.60"));

        // Проверка метаданных отчетов
        List<BrokerReport> reports = brokerReportRepository.findByPortfolioOrderByReportPeriodStartDesc(testPortfolio);
        assertThat(reports).hasSize(1);
        BrokerReport report = reports.get(0);
        assertThat(report.getReportPeriodStart()).isEqualTo(LocalDate.of(2020, 1, 1));
        assertThat(report.getReportPeriodEnd()).isEqualTo(LocalDate.of(2020, 1, 31));
        assertThat(report.getInvestorName()).isEqualTo("Цыганков Сергей Владимирович");
        assertThat(report.getContractNumber()).isEqualTo("42UUY");
    }

    @Test
    void testLoadDailyReport_VerifyAllDataCorrect() throws Exception {
        MockMultipartFile dailyReport = loadReportFile("59176076ID1V42UUY_15012020.html");
        brokerReportService.processReport(testPortfolio, dailyReport, testUser);

        // Проверка позиций (3 позиции, без облигации)
        List<PortfolioPosition> positions = positionRepository.findByPortfolio(testPortfolio);
        assertThat(positions).hasSize(3);
        
        // Проверка транзакций (только продажи 15.01)
        List<PortfolioTransaction> transactions = transactionRepository.findByPortfolioOrderByTradeDateAsc(testPortfolio);
        assertThat(transactions).hasSize(4);
        assertThat(transactions).allMatch(t -> t.getOperationType().equals("Продажа"));
        assertThat(transactions).allMatch(t -> t.getTradeDate().equals(LocalDate.of(2020, 1, 15)));
        // Проверка номеров сделок
        assertThat(transactions.stream().map(PortfolioTransaction::getTradeNumber))
            .containsExactlyInAnyOrder("3071727764", "3072039532", "3072040287", "3072052736");

        // Проверка движений денежных средств (в дневном отчете нет таблицы движений денежных средств)
        List<PortfolioCashMovement> movements = cashMovementRepository.findByPortfolio(testPortfolio);
        assertThat(movements).hasSize(0);

        // Проверка метаданных отчета
        List<BrokerReport> reports = brokerReportRepository.findByPortfolioOrderByReportPeriodStartDesc(testPortfolio);
        assertThat(reports).hasSize(1);
        BrokerReport report = reports.get(0);
        assertThat(report.getReportPeriodStart()).isEqualTo(LocalDate.of(2020, 1, 15));
        assertThat(report.getReportPeriodEnd()).isEqualTo(LocalDate.of(2020, 1, 15));
    }

    @Test
    void testLoadSameReportTwice_ShouldRejectSecond() throws Exception {
        MockMultipartFile monthlyReport = loadReportFile("61804403ID1V42UUY_01012020_31012020.html");
        
        // Первая загрузка
        brokerReportService.processReport(testPortfolio, monthlyReport, testUser);
        
        // Вторая загрузка того же отчета
        assertThatThrownBy(() -> 
            brokerReportService.processReport(testPortfolio, monthlyReport, testUser)
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("Отчет за период");
    }

    @Test
    void testLoadDailyThenMonthly_VerifyNoDuplicateTransactions() throws Exception {
        // Загрузка дневного отчета
        MockMultipartFile dailyReport = loadReportFile("59176076ID1V42UUY_15012020.html");
        brokerReportService.processReport(testPortfolio, dailyReport, testUser);

        // Проверка, что загружено 4 транзакции
        List<PortfolioTransaction> transactionsAfterDaily = transactionRepository.findByPortfolioOrderByTradeDateAsc(testPortfolio);
        assertThat(transactionsAfterDaily).hasSize(4);

        // Загрузка месячного отчета (содержит те же 4 транзакции + 2 новые)
        MockMultipartFile monthlyReport = loadReportFile("61804403ID1V42UUY_01012020_31012020.html");
        brokerReportService.processReport(testPortfolio, monthlyReport, testUser);

        // Проверка, что дубликаты не созданы (4 из дневного + 2 новых = 6)
        List<PortfolioTransaction> transactionsAfterMonthly = transactionRepository.findByPortfolioOrderByTradeDateAsc(testPortfolio);
        assertThat(transactionsAfterMonthly).hasSize(6);
        
        // Проверка, что все номера сделок уникальны
        long uniqueTradeNumbers = transactionsAfterMonthly.stream()
            .map(PortfolioTransaction::getTradeNumber)
            .filter(tn -> tn != null && !tn.isBlank())
            .distinct()
            .count();
        assertThat(uniqueTradeNumbers).isEqualTo(6);
    }
}

