package org.srm.mall.other.domain.entity;

import io.swagger.annotations.ApiModelProperty;

import java.util.List;

public class WatsonsShoppingCart extends ShoppingCart {

    private List<AllocationInfo> allocationInfoList;

    public List<AllocationInfo> getAllocationInfoList() {
        return allocationInfoList;
    }

    public void setAllocationInfoList(List<AllocationInfo> allocationInfoList) {
        this.allocationInfoList = allocationInfoList;
    }

    @ApiModelProperty(value = "CE号")
    private String ceNum;

    @ApiModelProperty(value = "联系电话")
    private String mobile;

}
