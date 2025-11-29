package com.invest.management.moex.bond;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "moex_bonds")
public class Bond {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Базовые поля (как в moex_stocks)
    @Column(nullable = false, unique = true, length = 20)
    private String secid;

    @Column(length = 10)
    private String boardid;

    @Column(length = 150)
    private String shortname;

    @Column(length = 255)
    private String secname;

    @Column(length = 12)
    private String isin;

    @Column(length = 40)
    private String regnumber;

    @Column(length = 20)
    private String status;

    private Integer lotsize;

    @Column(precision = 18, scale = 4)
    private BigDecimal facevalue;

    private Integer decimals;

    @Column(precision = 18, scale = 6)
    private BigDecimal prevprice;

    @Column(precision = 20, scale = 2)
    private BigDecimal marketcap;

    @Column(length = 20)
    private String marketcode;

    @Column(length = 30)
    private String instrid;

    @Column(length = 30)
    private String sectorid;

    @Column(name = "group_name", length = 50)
    private String group;

    @Column(length = 10)
    private String currencyid;

    @Column(precision = 20, scale = 2)
    private BigDecimal issuesize;

    @Column(precision = 18, scale = 6)
    private BigDecimal marketprice;

    @Column(length = 50)
    private String tradingstatus;

    @Column(length = 255)
    private String issuer;

    @Column(name = "issuer_country", length = 100)
    private String issuerCountry;

    @Column(name = "issuer_industry", length = 100)
    private String issuerIndustry;

    @Column(name = "issuer_sector", length = 100)
    private String issuerSector;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    // Специфичные поля для облигаций
    @Column(name = "maturitydate")
    private LocalDate maturitydate;

    @Column(name = "couponpercent", precision = 10, scale = 4)
    private BigDecimal couponpercent;

    @Column(name = "nextcoupon")
    private LocalDate nextcoupon;

    @Column(name = "bondtype", length = 50)
    private String bondtype;

    @Column(name = "offerdate")
    private LocalDate offerdate;

    public Long getId() {
        return id;
    }

    public String getSecid() {
        return secid;
    }

    public void setSecid(String secid) {
        this.secid = secid;
    }

    public String getBoardid() {
        return boardid;
    }

    public void setBoardid(String boardid) {
        this.boardid = boardid;
    }

    public String getShortname() {
        return shortname;
    }

    public void setShortname(String shortname) {
        this.shortname = shortname;
    }

    public String getSecname() {
        return secname;
    }

    public void setSecname(String secname) {
        this.secname = secname;
    }

    public String getIsin() {
        return isin;
    }

    public void setIsin(String isin) {
        this.isin = isin;
    }

    public String getRegnumber() {
        return regnumber;
    }

    public void setRegnumber(String regnumber) {
        this.regnumber = regnumber;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getLotsize() {
        return lotsize;
    }

    public void setLotsize(Integer lotsize) {
        this.lotsize = lotsize;
    }

    public BigDecimal getFacevalue() {
        return facevalue;
    }

    public void setFacevalue(BigDecimal facevalue) {
        this.facevalue = facevalue;
    }

    public Integer getDecimals() {
        return decimals;
    }

    public void setDecimals(Integer decimals) {
        this.decimals = decimals;
    }

    public BigDecimal getPrevprice() {
        return prevprice;
    }

    public void setPrevprice(BigDecimal prevprice) {
        this.prevprice = prevprice;
    }

    public BigDecimal getMarketcap() {
        return marketcap;
    }

    public void setMarketcap(BigDecimal marketcap) {
        this.marketcap = marketcap;
    }

    public String getMarketcode() {
        return marketcode;
    }

    public void setMarketcode(String marketcode) {
        this.marketcode = marketcode;
    }

    public String getInstrid() {
        return instrid;
    }

    public void setInstrid(String instrid) {
        this.instrid = instrid;
    }

    public String getSectorid() {
        return sectorid;
    }

    public void setSectorid(String sectorid) {
        this.sectorid = sectorid;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getCurrencyid() {
        return currencyid;
    }

    public void setCurrencyid(String currencyid) {
        this.currencyid = currencyid;
    }

    public BigDecimal getIssuesize() {
        return issuesize;
    }

    public void setIssuesize(BigDecimal issuesize) {
        this.issuesize = issuesize;
    }

    public BigDecimal getMarketprice() {
        return marketprice;
    }

    public void setMarketprice(BigDecimal marketprice) {
        this.marketprice = marketprice;
    }

    public String getTradingstatus() {
        return tradingstatus;
    }

    public void setTradingstatus(String tradingstatus) {
        this.tradingstatus = tradingstatus;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getIssuerCountry() {
        return issuerCountry;
    }

    public void setIssuerCountry(String issuerCountry) {
        this.issuerCountry = issuerCountry;
    }

    public String getIssuerIndustry() {
        return issuerIndustry;
    }

    public void setIssuerIndustry(String issuerIndustry) {
        this.issuerIndustry = issuerIndustry;
    }

    public String getIssuerSector() {
        return issuerSector;
    }

    public void setIssuerSector(String issuerSector) {
        this.issuerSector = issuerSector;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDate getMaturitydate() {
        return maturitydate;
    }

    public void setMaturitydate(LocalDate maturitydate) {
        this.maturitydate = maturitydate;
    }

    public BigDecimal getCouponpercent() {
        return couponpercent;
    }

    public void setCouponpercent(BigDecimal couponpercent) {
        this.couponpercent = couponpercent;
    }

    public LocalDate getNextcoupon() {
        return nextcoupon;
    }

    public void setNextcoupon(LocalDate nextcoupon) {
        this.nextcoupon = nextcoupon;
    }

    public String getBondtype() {
        return bondtype;
    }

    public void setBondtype(String bondtype) {
        this.bondtype = bondtype;
    }

    public LocalDate getOfferdate() {
        return offerdate;
    }

    public void setOfferdate(LocalDate offerdate) {
        this.offerdate = offerdate;
    }
}

