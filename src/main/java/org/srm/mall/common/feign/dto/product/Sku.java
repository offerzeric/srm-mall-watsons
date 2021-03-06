package org.srm.mall.common.feign.dto.product;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.choerodon.mybatis.annotation.ModifyAudit;
import io.choerodon.mybatis.annotation.MultiLanguage;
import io.choerodon.mybatis.annotation.MultiLanguageField;
import io.choerodon.mybatis.annotation.VersionAudit;
import io.choerodon.mybatis.domain.AuditDomain;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.hzero.boot.platform.code.builder.CodeRuleBuilder;
import org.hzero.core.base.BaseConstants;
import org.hzero.starter.keyencrypt.core.Encrypt;
import org.springframework.util.ObjectUtils;

import java.math.BigDecimal;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * SKU
 *
 * @author yuhao.guo@hand-china.com 2020-12-15 11:18:00
 */
@ApiModel("SKU")
@VersionAudit
@ModifyAudit
@MultiLanguage
@JsonInclude(value = JsonInclude.Include.NON_NULL)
@Table(name = "smpc_sku")
public class Sku extends AuditDomain {

    public static final String FIELD_SKU_ID = "skuId";
    public static final String FIELD_TENANT_ID = "tenantId";
    public static final String FIELD_SPU_ID = "spuId";
    public static final String FIELD_SKU_CODE = "skuCode";
    public static final String FIELD_SKU_NAME = "skuName";
    public static final String FIELD_CATEGORY_ID = "categoryId";
    public static final String FIELD_INTRODUCTION_URL = "introductionUrl";
    public static final String FIELD_SOURCE_FROM = "sourceFrom";
    public static final String FIELD_SOURCE_FROM_TYPE = "sourceFromType";
    public static final String FIELD_THIRD_SKU_CODE = "thirdSkuCode";
    public static final String FIELD_PACKING_LIST = "packingList";
    public static final String FIELD_PRIMARY_FLAG = "primaryFlag";
    public static final String FIELD_SKU_STATUS = "skuStatus";
    public static final String FIELD_SUPPLIER_TENANT_ID = "supplierTenantId";
    public static final String FIELD_SUPPLIER_COMPANY_ID = "supplierCompanyId";
    public static final String FIELD_UNIT_PRICE = "unitPrice";
    public static final String FIELD_MARKET_PRICE = "marketPrice";
    public static final String FIELD_SALE_PRICE = "salePrice";
    public static final String FIELD_VERSION = "version";
    public static final String FIELD_SUPPLIER_ITEM_CODE = "supplierItemCode";
    public static final String FIELD_SUPPLIER_ITEM_NAME = "supplierItemName";
    public static final String FIELD_MANUFACTURER_ITEM_CODE = "manufacturerItemCode";
    public static final String FIELD_MANUFACTURER_ITEM_NAME = "manufacturerItemName";
    public static final String FIELD_MANUFACTURER_INFO = "manufacturerInfo";

    //
    // ????????????(???public protected private????????????)
    // ------------------------------------------------------------------------------

	public void init(Long spuId, Long categoryId) {
		this.spuId = spuId;
		this.categoryId = categoryId;
		this.sourceFrom = ObjectUtils.isEmpty(this.sourceFrom) ? "CATA" : this.sourceFrom;
		this.sourceFromType = ObjectUtils.isEmpty(this.sourceFromType) ? SmpcConstants.SourceFromType.MANUAL : this.sourceFromType;
		this.skuStatus = 0;
		if (this.supplierTenantId == null || this.supplierTenantId.equals(-1L)) {
			this.supplierTenantId = -1L;
			this.supplierCompanyId = -1L;
		}
		this.version = 1L;
	}

	//?????????????????????
	public Sku importInit(Long tenantId, Long spuId, Long categoryId, SkuImportDTO skuImportDTO, CodeRuleBuilder codeRuleBuilder, SkuService skuService) {
		this.skuCode = codeRuleBuilder.generateCode(SmpcConstants.CodeRule.CODE_RULE_SKU_CODE, null);
		this.tenantId = tenantId;
		this.skuName = skuImportDTO.getSkuName();
		this.spuId = spuId;
		this.categoryId = categoryId;
		this.sourceFrom = ObjectUtils.isEmpty(this.sourceFrom) ? "CATA" : this.sourceFrom;
		this.sourceFromType = ObjectUtils.isEmpty(this.sourceFromType) ? SmpcConstants.SourceFromType.MANUAL : this.sourceFromType;
		this.supplierCompanyId = ObjectUtils.isEmpty(skuImportDTO.getSupplierCompanyId()) ? -1L : skuImportDTO.getSupplierCompanyId();
		this.supplierTenantId = ObjectUtils.isEmpty(skuImportDTO.getSupplierTenantId()) ? -1L : skuImportDTO.getSupplierTenantId();
		this.version = 1L;
		this.primaryFlag = BaseConstants.Flag.YES;
		if (skuService.checkPlatformApprove()) {
			this.skuStatus = SmpcConstants.SkuStatus.WAITING;
		} else {
			this.skuStatus = SmpcConstants.SkuStatus.VALID;
		}
		if (!ObjectUtils.isEmpty(skuImportDTO.getPrice())) {
			this.unitPrice = skuImportDTO.getPrice();
		} else {
			this.unitPrice = BigDecimal.ZERO;
		}
		return this;
	}
    //
    // ???????????????
    // ------------------------------------------------------------------------------


    @ApiModelProperty("")
    @Id
    @GeneratedValue
    @Encrypt
    private Long skuId;
    @ApiModelProperty(value = "??????ID???hpfm_tenant.tenant_id", required = true)
    @NotNull
    private Long tenantId;
    @ApiModelProperty(value = "?????????ID,smpc_spu.spu_id", required = true)
    @NotNull
    @Encrypt
    private Long spuId;
    @ApiModelProperty(value = "????????????")
    private String skuCode;
    @ApiModelProperty(value = "????????????", required = true)
    @NotBlank
    @MultiLanguageField
    private String skuName;
    @ApiModelProperty(value = "????????????ID???smpc_category.category_id", required = true)
    @NotNull
    @Encrypt
    private Long categoryId;
    @ApiModelProperty(value = "????????????")
    private String introductionUrl;
    @ApiModelProperty(value = "????????????CATA????????????EC", required = true)
    @NotBlank
    private String sourceFrom;
    @ApiModelProperty(value = "??????????????????????????????MANUAL????????????AGREEMENT", required = true)
    @NotBlank
    private String sourceFromType;
    @ApiModelProperty(value = "?????????????????????")
    private String thirdSkuCode;
    @ApiModelProperty(value = "????????????")
    private String packingList;
    @ApiModelProperty(value = "???SKU??? 1:???sku,0:??????sku", required = true)
    @NotNull
    private Integer primaryFlag;
    @ApiModelProperty(value = "sku?????????0:?????? 1:???????????? 2:???????????? 3:?????? 4:??????", required = true)
    @NotNull
    private Integer skuStatus;
    @ApiModelProperty(value = "???????????????ID  hpfm_tenant.tenant_id", required = true)
    @NotNull
    private Long supplierTenantId;
    @ApiModelProperty(value = "???????????????ID???hpfm_company.company_id", required = true)
    @NotNull
    @Encrypt
    private Long supplierCompanyId;
    @ApiModelProperty(value = "??????")
    private BigDecimal unitPrice;
    @ApiModelProperty(value = "?????????")
    private BigDecimal marketPrice;
    @ApiModelProperty(value = "?????????")
    private BigDecimal salePrice;
    @ApiModelProperty(value = "?????????", required = true)
    @NotNull
    private Long version;
    @ApiModelProperty(value = "??????????????????")
    private String supplierItemCode;
    @ApiModelProperty(value = "?????????????????????")
    private String supplierItemName;
    @ApiModelProperty(value = "??????????????????")
    private String manufacturerItemCode;
    @ApiModelProperty(value = "?????????????????????")
    private String manufacturerItemName;
    @ApiModelProperty(value = "???????????????")
    private String manufacturerInfo;

    //
    // ??????????????????
    // ------------------------------------------------------------------------------
    @Transient
    @ApiModelProperty(value = "???????????????")
    private Integer supFlag;
    @Transient
    @ApiModelProperty(value = "????????????")
    private String mediaPath;
    @Transient
    @ApiModelProperty(value = "spu??????")
    private String spuCode;
    @Transient
    @ApiModelProperty(value = "spu??????")
    private String spuName;
    @Transient
    @ApiModelProperty(value = "????????????")
    private Long skuStock;
    @Transient
    @ApiModelProperty(value = "???????????????????????????????????????")
    private String supplierTenantName;
    @Transient
    @ApiModelProperty(value = "???????????????????????????????????????")
    private String supplierCompanyName;
    @Transient
    @ApiModelProperty(value = "??????????????????")
    private String categoryNamePath;
    //
    // getter/setter
    // ------------------------------------------------------------------------------

    /**
     * @return
     */
    public Long getSkuId() {
        return skuId;
    }

    public void setSkuId(Long skuId) {
        this.skuId = skuId;
    }

    /**
     * @return ??????ID???hpfm_tenant.tenant_id
     */
    public Long getTenantId() {
        return tenantId;
    }

    public Sku setTenantId(Long tenantId) {
        this.tenantId = tenantId;
        return this;
    }

    /**
     * @return ?????????ID, smpc_spu.spu_id
     */
    public Long getSpuId() {
        return spuId;
    }

    public void setSpuId(Long spuId) {
        this.spuId = spuId;
    }

    /**
     * @return ????????????
     */
    public String getSkuCode() {
        return skuCode;
    }

    public Sku setSkuCode(String skuCode) {
        this.skuCode = skuCode;
        return this;
    }

    /**
     * @return ????????????
     */
    public String getSkuName() {
        return skuName;
    }

    public void setSkuName(String skuName) {
        this.skuName = skuName;
    }

    /**
     * @return ????????????ID???smpc_category.category_id
     */
    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    /**
     * @return ????????????
     */
    public String getIntroductionUrl() {
        return introductionUrl;
    }

    public void setIntroductionUrl(String introductionUrl) {
        this.introductionUrl = introductionUrl;
    }

    /**
     * @return ????????????CATA????????????EC
     */
    public String getSourceFrom() {
        return sourceFrom;
    }

    public void setSourceFrom(String sourceFrom) {
        this.sourceFrom = sourceFrom;
    }

    /**
     * @return ??????????????????????????????MANUAL????????????AGREEMENT
     */
    public String getSourceFromType() {
        return sourceFromType;
    }

    public void setSourceFromType(String sourceFromType) {
        this.sourceFromType = sourceFromType;
    }

    /**
     * @return ?????????????????????
     */
    public String getThirdSkuCode() {
        return thirdSkuCode;
    }

    public void setThirdSkuCode(String thirdSkuCode) {
        this.thirdSkuCode = thirdSkuCode;
    }

    /**
     * @return ????????????
     */
    public String getPackingList() {
        return packingList;
    }

    public void setPackingList(String packingList) {
        this.packingList = packingList;
    }

    /**
     * @return ???SKU??? 1:???sku,0:??????sku
     */
    public Integer getPrimaryFlag() {
        return primaryFlag;
    }

    public void setPrimaryFlag(Integer primaryFlag) {
        this.primaryFlag = primaryFlag;
    }

    /**
     * @return sku?????????0:?????? 1:???????????? 2:???????????? 3:?????? 4:??????
     */
    public Integer getSkuStatus() {
        return skuStatus;
    }

    public void setSkuStatus(Integer skuStatus) {
        this.skuStatus = skuStatus;
    }

    /**
     * @return ???????????????ID  hpfm_tenant.tenant_id
     */
    public Long getSupplierTenantId() {
        return supplierTenantId;
    }

    public void setSupplierTenantId(Long supplierTenantId) {
        this.supplierTenantId = supplierTenantId;
    }

    /**
     * @return ???????????????ID???hpfm_company.company_id
     */
    public Long getSupplierCompanyId() {
        return supplierCompanyId;
    }

    public void setSupplierCompanyId(Long supplierCompanyId) {
        this.supplierCompanyId = supplierCompanyId;
    }

    /**
     * @return ??????
     */
    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(BigDecimal unitPrice) {
        this.unitPrice = unitPrice;
    }

    /**
     * @return ?????????
     */
    public BigDecimal getMarketPrice() {
        return marketPrice;
    }

    public void setMarketPrice(BigDecimal marketPrice) {
        this.marketPrice = marketPrice;
    }

    /**
     * @return ?????????
     */
    public BigDecimal getSalePrice() {
        return salePrice;
    }

    public void setSalePrice(BigDecimal salePrice) {
        this.salePrice = salePrice;
    }

    /**
     * @return ?????????
     */
    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    /**
     * @return ??????????????????
     */
    public String getSupplierItemCode() {
        return supplierItemCode;
    }

    public void setSupplierItemCode(String supplierItemCode) {
        this.supplierItemCode = supplierItemCode;
    }

    /**
     * @return ?????????????????????
     */
    public String getSupplierItemName() {
        return supplierItemName;
    }

    public void setSupplierItemName(String supplierItemName) {
        this.supplierItemName = supplierItemName;
    }

    /**
     * @return ??????????????????
     */
    public String getManufacturerItemCode() {
        return manufacturerItemCode;
    }

    public void setManufacturerItemCode(String manufacturerItemCode) {
        this.manufacturerItemCode = manufacturerItemCode;
    }

    /**
     * @return ?????????????????????
     */
    public String getManufacturerItemName() {
        return manufacturerItemName;
    }

    public void setManufacturerItemName(String manufacturerItemName) {
        this.manufacturerItemName = manufacturerItemName;
    }

    /**
     * @return ???????????????
     */
    public String getManufacturerInfo() {
        return manufacturerInfo;
    }

    public void setManufacturerInfo(String manufacturerInfo) {
        this.manufacturerInfo = manufacturerInfo;
    }

    public Integer getSupFlag() {
        return supFlag;
    }

    public void setSupFlag(Integer supFlag) {
        this.supFlag = supFlag;
    }

    public String getMediaPath() {
        return mediaPath;
    }

    public void setMediaPath(String mediaPath) {
        this.mediaPath = mediaPath;
    }

    public String getSpuCode() {
        return spuCode;
    }

    public void setSpuCode(String spuCode) {
        this.spuCode = spuCode;
    }

    public String getSpuName() {
        return spuName;
    }

    public void setSpuName(String spuName) {
        this.spuName = spuName;
    }

    public Long getSkuStock() {
        return skuStock;
    }

    public void setSkuStock(Long skuStock) {
        this.skuStock = skuStock;
    }

    public String getSupplierTenantName() {
        return supplierTenantName;
    }

    public void setSupplierTenantName(String supplierTenantName) {
        this.supplierTenantName = supplierTenantName;
    }

    public String getSupplierCompanyName() {
        return supplierCompanyName;
    }

    public void setSupplierCompanyName(String supplierCompanyName) {
        this.supplierCompanyName = supplierCompanyName;
    }

    public String getCategoryNamePath() {
        return categoryNamePath;
    }

    public void setCategoryNamePath(String categoryNamePath) {
        this.categoryNamePath = categoryNamePath;
    }
}
