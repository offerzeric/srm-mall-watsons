package org.srm.mall.other.infra.mapper;

import org.apache.ibatis.annotations.Param;
import org.springframework.security.core.parameters.P;
import org.srm.mall.other.api.dto.OrganizationInfoDTO;
import org.srm.mall.other.api.dto.WatsonsRegionDTO;
import org.srm.mall.other.api.dto.WatsonsShoppingCartDTO;
import org.srm.mall.other.api.dto.WhLovResultDTO;
import org.srm.mall.other.domain.entity.AllocationInfo;
import io.choerodon.mybatis.common.BaseMapper;
import org.srm.mall.region.api.dto.AddressDTO;
import org.srm.mall.region.domain.entity.Address;

import java.util.List;

/**
 * 屈臣氏费用分配表Mapper
 *
 * @author yuewen.wei@hand-china.com 2020-12-21 15:35:27
 */
public interface AllocationInfoMapper extends BaseMapper<AllocationInfo> {

    List<OrganizationInfoDTO> selectAllocationShopOrganization(OrganizationInfoDTO organizationInfoDTO);

    Integer selectHasProjectSubcategoryId(@Param("projectCostId") Long projectCostId, @Param("tenantId")Long tenantId);

    AddressDTO selectIdByCode(Long organizationId, String watsonsOrganizationCode);

    WatsonsRegionDTO selectRegionInfoByRegionId(Long regionId);

    WatsonsRegionDTO selectRegionInfoByRegionCode(String regionCode);

    WhLovResultDTO selectInvNameByInvCode(String inventoryCode, Long organizationId);

    Integer checkAddressByInvOrganization(OrganizationInfoDTO infoDTO);

    WhLovResultDTO selectInvInfoByInvId(Long watsonsOrganizationId, Long organizationId);

    String checkDeliveryType(String itemCode, String sourceCode, Long tenantId);

    String checkItemCodeByItemId(Long itemId, Long tenantId, String sourceCode);

}
