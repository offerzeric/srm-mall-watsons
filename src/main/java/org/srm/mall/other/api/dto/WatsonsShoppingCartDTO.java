package org.srm.mall.other.api.dto;

import io.choerodon.core.exception.CommonException;
import io.swagger.annotations.ApiModelProperty;
import org.hzero.core.base.BaseConstants;
import org.hzero.core.util.ResponseUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.srm.mall.common.feign.SmdmRemoteService;
import org.srm.mall.context.entity.ItemCategory;
import org.srm.mall.other.api.dto.ShoppingCartDTO;
import org.srm.mall.other.app.service.impl.ShoppingCartServiceImpl;
import org.srm.mall.other.domain.entity.AllocationInfo;
import org.srm.mall.other.domain.entity.WatsonsShoppingCart;
import org.srm.mall.platform.domain.entity.PurReqMergeRule;
import org.srm.mall.product.api.dto.ItemCategoryDTO;


import java.util.List;
import javax.persistence.Transient;

public class WatsonsShoppingCartDTO extends ShoppingCartDTO {

    @Autowired
    private SmdmRemoteService smdmRemoteService;

    private static final Logger logger = LoggerFactory.getLogger(ShoppingCartServiceImpl.class);

    private List<AllocationInfo> allocationInfoList;


    @ApiModelProperty(value = "CE号")
    private String ceNum;

    @ApiModelProperty(value = "联系电话")
    private String mobile;

    @ApiModelProperty(value = "采购品类名称")
    private String itemCategoryName;

    @ApiModelProperty(value = "用于合单操作的key")
    @Transient
    private String key;

    public List<AllocationInfo> getAllocationInfoList() {
        return allocationInfoList;
    }

    public void setAllocationInfoList(List<AllocationInfo> allocationInfoList) {
        this.allocationInfoList = allocationInfoList;
    }

    public String getCeNum() {
        return ceNum;
    }

    public void setCeNum(String ceNum) {
        this.ceNum = ceNum;
    }

    @Override
    public String getMobile() {
        return mobile;
    }

    @Override
    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getItemCategoryName() {
        return itemCategoryName;
    }

    public void setItemCategoryName(String itemCategoryName) {
        this.itemCategoryName = itemCategoryName;
    }


    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }


}
