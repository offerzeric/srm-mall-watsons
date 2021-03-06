package org.srm.mall.common.feign.dto.agreement;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.choerodon.mybatis.annotation.ModifyAudit;
import io.choerodon.mybatis.annotation.MultiLanguage;
import io.choerodon.mybatis.annotation.MultiLanguageField;
import io.choerodon.mybatis.annotation.VersionAudit;
import io.choerodon.mybatis.domain.AuditDomain;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import org.apache.commons.lang3.time.DateUtils;
import org.hzero.core.base.BaseConstants;
import org.hzero.starter.keyencrypt.core.Encrypt;
import org.springframework.format.annotation.DateTimeFormat;
import org.srm.mall.common.feign.dto.agreemnet.AgreementLine;
import org.srm.mall.common.feign.dto.postage.Postage;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * @author fu.ji@hand-china.com 2020-05-22 10:50:31
 */
@Data
@ApiModel("")
@VersionAudit
@MultiLanguage
@ModifyAudit
@Table(name = "smal_agreement")
public class Agreement extends AuditDomain {

    public static final String FIELD_AGREEMENT_ID = "agreementId";
    public static final String FIELD_TENANT_ID = "tenantId";
    public static final String FIELD_COMPANY_ID = "companyId";
    public static final String FIELD_COMPANY = "company";
    public static final String FIELD_AGREEMENT_NUMBER = "agreementNumber";
    public static final String FIELD_AGREEMENT_NAME = "agreementName";
    public static final String FIELD_SUPPLIER_TENANT_ID = "supplierTenantId";
    public static final String FIELD_SUPPLIER_COMPANY_ID = "supplierCompanyId";
    public static final String FIELD_SUPPLIER_COMPANY = "supplier";
    public static final String FIELD_RELEASE_DATE = "releaseDate";
    public static final String FIELD_SOURCE_FROM = "sourceFrom";
    public static final String FIELD_SOURCE_FROM_NUMBER = "sourceFromNumber";
    public static final String FIELD_AGREEMENT_TYPE = "agreementType";
    public static final String FIELD_MATERIAL_TYPE = "materialType";
    public static final String FIELD_PAYMENT_TYPE = "paymentType";
    public static final String FIELD_AGREEMENT_STATUS = "agreementStatus";
    public static final String FIELD_AGREEMENT_BELONG_TYPE = "agreementBelongType";
    public static final String FIELD_UUID = "uuid";
    public static final String FIELD_REMARK = "remark";
    public static final String FIELD_VERSION_NUM = "versionNum";
    public static final String FIELD_FREIGHT_TYPE = "freightType";

    //
    // ????????????(???public protected private????????????)
    // ------------------------------------------------------------------------------

    //
    // ???????????????
    // ------------------------------------------------------------------------------


    @ApiModelProperty("???ID?????????????????????????????????")
    @Id
    @GeneratedValue
    @Encrypt
    private Long agreementId;
    @ApiModelProperty(value = "??????id", required = true)
    @NotNull
    private Long tenantId;
    @ApiModelProperty(value = "??????id", required = true)
    @NotNull
    @Encrypt
    private Long companyId;
    @ApiModelProperty(value = "????????????", required = true)
    @NotBlank
    private String agreementNumber;
    @MultiLanguageField
    @ApiModelProperty(value = "????????????", required = true)
    @NotBlank
    private String agreementName;
    @ApiModelProperty(value = "???????????????id", required = true)
    @NotNull
    private Long supplierTenantId;
    @ApiModelProperty(value = "???????????????id", required = true)
    @NotNull
    @Encrypt
    private Long supplierCompanyId;
    @ApiModelProperty(value = "???????????????", required = true)
    @NotNull
    @JsonFormat(pattern = BaseConstants.Pattern.DATE)
    @DateTimeFormat(pattern = BaseConstants.Pattern.DATE)
    private Date validDateFrom;
    @ApiModelProperty(value = "???????????????", required = true)
    @JsonFormat(pattern = BaseConstants.Pattern.DATE)
    @DateTimeFormat(pattern = BaseConstants.Pattern.DATE)
    private Date validDateTo;
    @ApiModelProperty(value = "????????????")
    @JsonFormat(pattern = BaseConstants.Pattern.DATE)
    @DateTimeFormat(pattern = BaseConstants.Pattern.DATE)
    private Date releaseDate;
    @ApiModelProperty(value = "????????????????????? SMAL.AGREEMENT_FROM", required = true)
    @NotBlank
    private String sourceFrom;
    @ApiModelProperty(value = "????????????")
    private String sourceFromNumber;
    @ApiModelProperty(value = "????????????????????? SMAL.AGREEMENT_TYPE", required = true)
    @NotBlank
    private String agreementType;
    @ApiModelProperty(value = "????????????????????? SMAL.MATERIAL_TYPE")
    private String materialType;
    @ApiModelProperty(value = "????????????????????? SMAL.PAYMENT_TYPE")
    private String paymentType;
    @ApiModelProperty(value = "????????????????????? SMAL.AGREEMENT_STATUS", required = true)
    @NotBlank
    private String agreementStatus;
    @ApiModelProperty(value = "?????????????????? -1 ?????????/1 ?????????", required = true)
    @NotNull
    private Integer agreementBelongType;
    @ApiModelProperty(value = "??????")
    private String uuid;
    @ApiModelProperty(value = "??????")
    private String remark;
    @ApiModelProperty(value = "?????????")
    private Integer versionNum;
    @ApiModelProperty(value = "?????????????????????SMAL.AGREEMENT_FREIGHT_TYPE")
    private String freightType;

    //
    // ??????????????????
    // ------------------------------------------------------------------------------
    @Transient
    private List<AgreementLine> agreementLines;
    @Transient
    private Long sourceAgreementId;
    @ApiModelProperty(value = "??????????????????")
    @Transient
    private Postage postage;
    @Transient
    private String companyName;
    @Transient
    private String supplierCompanyName;
    @Transient
    private Long purchaseTenantId;
    @Transient
    private String company;
    @Transient
    private String supplier;
    @Transient
    @ApiModelProperty(value = "???????????????????????????,?????????")
    private Boolean handleEcFlag;
    @Transient
    @ApiModelProperty(value = "??????????????????????????????,?????????")
    private Boolean handleCataFlag;
    //
    // getter/setter
    // ------------------------------------------------------------------------------


    public Agreement() {
    }

    public Agreement(Long agreementId) {
        this.agreementId = agreementId;
    }

    /**
     * @return ???????????? true ??????/ false ??????
     */
    public boolean effective() {
        Date currentDate = new Date();
        return DateUtils.truncatedCompareTo(this.validDateFrom, currentDate, Calendar.DATE) <= 0 && DateUtils.truncatedCompareTo(this.validDateTo, currentDate, Calendar.DATE) >= 0;
    }

    /**
     * @return ???????????? true ??????/ false ??????
     */
    public boolean failed() {
        Date currentDate = new Date();
        return DateUtils.truncatedCompareTo(this.validDateTo, currentDate, Calendar.DATE) < 0;
    }
    /**
     * @return ???ID?????????????????????????????????
     */
    public Long getAgreementId() {
        return agreementId;
    }

    public void setAgreementId(Long agreementId) {
        this.agreementId = agreementId;
    }

    /**
     * @return ??????id
     */
    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    /**
     * @return ??????id
     */
    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    /**
     * @return ????????????
     */
    public String getAgreementNumber() {
        return agreementNumber;
    }

    public void setAgreementNumber(String agreementNumber) {
        this.agreementNumber = agreementNumber;
    }

    /**
     * @return ????????????
     */
    public String getAgreementName() {
        return agreementName;
    }

    public void setAgreementName(String agreementName) {
        this.agreementName = agreementName;
    }

    /**
     * @return ???????????????id
     */
    public Long getSupplierTenantId() {
        return supplierTenantId;
    }

    public void setSupplierTenantId(Long supplierTenantId) {
        this.supplierTenantId = supplierTenantId;
    }

    /**
     * @return ???????????????id
     */
    public Long getSupplierCompanyId() {
        return supplierCompanyId;
    }

    public void setSupplierCompanyId(Long supplierCompanyId) {
        this.supplierCompanyId = supplierCompanyId;
    }

    /**
     * @return ???????????????
     */
    public Date getValidDateFrom() {
        return validDateFrom;
    }

    public void setValidDateFrom(Date validDateFrom) {
        this.validDateFrom = validDateFrom;
    }

    /**
     * @return ???????????????
     */
    public Date getValidDateTo() {
        return validDateTo;
    }

    public void setValidDateTo(Date validDateTo) {
        this.validDateTo = validDateTo;
    }

    /**
     * @return ????????????
     */
    public Date getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(Date releaseDate) {
        this.releaseDate = releaseDate;
    }

    /**
     * @return ????????????????????? SMAL.AGREEMENT_FROM
     */
    public String getSourceFrom() {
        return sourceFrom;
    }

    public void setSourceFrom(String sourceFrom) {
        this.sourceFrom = sourceFrom;
    }

    /**
     * @return ????????????
     */
    public String getSourceFromNumber() {
        return sourceFromNumber;
    }

    public void setSourceFromNumber(String sourceFromNumber) {
        this.sourceFromNumber = sourceFromNumber;
    }

    /**
     * @return ????????????????????? SMAL.AGREEMENT_TYPE
     */
    public String getAgreementType() {
        return agreementType;
    }

    public void setAgreementType(String agreementType) {
        this.agreementType = agreementType;
    }

    /**
     * @return ????????????????????? SMAL.MATERIAL_TYPE
     */
    public String getMaterialType() {
        return materialType;
    }

    public void setMaterialType(String materialType) {
        this.materialType = materialType;
    }

    /**
     * @return ????????????????????? SMAL.PAYMENT_TYPE
     */
    public String getPaymentType() {
        return paymentType;
    }

    public void setPaymentType(String paymentType) {
        this.paymentType = paymentType;
    }

    /**
     * @return ????????????????????? SMAL.AGREEMENT_STATUS
     */
    public String getAgreementStatus() {
        return agreementStatus;
    }

    public void setAgreementStatus(String agreementStatus) {
        this.agreementStatus = agreementStatus;
    }

    /**
     * @return ?????????????????? -1 ?????????/1 ?????????
     */
    public Integer getAgreementBelongType() {
        return agreementBelongType;
    }

    public void setAgreementBelongType(Integer agreementBelongType) {
        this.agreementBelongType = agreementBelongType;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public List<AgreementLine> getAgreementLines() {
        return agreementLines;
    }

    public void setAgreementLines(List<AgreementLine> agreementLines) {
        this.agreementLines = agreementLines;
    }

    public Integer getVersionNum() {
        return versionNum;
    }

    public void setVersionNum(Integer versionNum) {
        this.versionNum = versionNum;
    }

    public Long getSourceAgreementId() {
        return sourceAgreementId;
    }

    public void setSourceAgreementId(Long sourceAgreementId) {
        this.sourceAgreementId = sourceAgreementId;
    }

    public String getFreightType() {
        return freightType;
    }

    public void setFreightType(String freightType) {
        this.freightType = freightType;
    }

    public Postage getPostage() {
        return postage;
    }

    public void setPostage(Postage postage) {
        this.postage = postage;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getSupplierCompanyName() {
        return supplierCompanyName;
    }

    public void setSupplierCompanyName(String supplierCompanyName) {
        this.supplierCompanyName = supplierCompanyName;
    }

    public Long getPurchaseTenantId() {
        return purchaseTenantId;
    }

    public void setPurchaseTenantId(Long purchaseTenantId) {
        this.purchaseTenantId = purchaseTenantId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Agreement agreement = (Agreement) o;
        return Objects.equals(agreementId, agreement.agreementId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(agreementId);
    }

    public Boolean getHandleEcFlag() {
        return handleEcFlag;
    }

    public Agreement setHandleEcFlag(Boolean handleEcFlag) {
        this.handleEcFlag = handleEcFlag;
        return this;
    }

    public Boolean getHandleCataFlag() {
        return handleCataFlag;
    }

    public Agreement setHandleCataFlag(Boolean handleCataFlag) {
        this.handleCataFlag = handleCataFlag;
        return this;
    }
}
