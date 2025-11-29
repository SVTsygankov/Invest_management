package com.invest.management.portfolio;

import com.invest.management.user.AppUser;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "broker_reports",
       uniqueConstraints = @UniqueConstraint(columnNames = {"portfolio_id", "report_period_start", "report_period_end"}))
public class BrokerReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "report_period_start", nullable = false)
    private LocalDate reportPeriodStart;

    @Column(name = "report_period_end", nullable = false)
    private LocalDate reportPeriodEnd;

    @Column(name = "report_created_date")
    private LocalDate reportCreatedDate;

    @Column(name = "investor_name", length = 255)
    private String investorName;

    @Column(name = "contract_number", length = 50)
    private String contractNumber;

    @Column(name = "uploaded_at")
    private OffsetDateTime uploadedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private AppUser uploadedBy;

    @PrePersist
    void prePersist() {
        uploadedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Portfolio getPortfolio() {
        return portfolio;
    }

    public void setPortfolio(Portfolio portfolio) {
        this.portfolio = portfolio;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public LocalDate getReportPeriodStart() {
        return reportPeriodStart;
    }

    public void setReportPeriodStart(LocalDate reportPeriodStart) {
        this.reportPeriodStart = reportPeriodStart;
    }

    public LocalDate getReportPeriodEnd() {
        return reportPeriodEnd;
    }

    public void setReportPeriodEnd(LocalDate reportPeriodEnd) {
        this.reportPeriodEnd = reportPeriodEnd;
    }

    public LocalDate getReportCreatedDate() {
        return reportCreatedDate;
    }

    public void setReportCreatedDate(LocalDate reportCreatedDate) {
        this.reportCreatedDate = reportCreatedDate;
    }

    public String getInvestorName() {
        return investorName;
    }

    public void setInvestorName(String investorName) {
        this.investorName = investorName;
    }

    public String getContractNumber() {
        return contractNumber;
    }

    public void setContractNumber(String contractNumber) {
        this.contractNumber = contractNumber;
    }

    public OffsetDateTime getUploadedAt() {
        return uploadedAt;
    }

    public AppUser getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(AppUser uploadedBy) {
        this.uploadedBy = uploadedBy;
    }
}

