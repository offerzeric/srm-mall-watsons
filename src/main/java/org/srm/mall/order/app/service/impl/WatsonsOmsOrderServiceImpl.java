package org.srm.mall.order.app.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.core.type.TypeReference;
import io.choerodon.core.exception.CommonException;
import io.choerodon.core.oauth.DetailsHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.hzero.boot.platform.code.builder.CodeRuleBuilder;
import org.hzero.core.base.BaseConstants;
import org.hzero.core.util.ResponseUtils;
import org.hzero.mybatis.domian.Condition;
import org.hzero.mybatis.util.Sqls;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;
import org.srm.mall.common.constant.ScecConstants;
import org.srm.mall.common.feign.SmdmRemoteNewService;
import org.srm.mall.common.feign.SmodrRemoteService;
import org.srm.mall.infra.constant.WatsonsConstants;
import org.srm.mall.order.api.dto.*;
import org.srm.mall.order.app.service.OmsOrderService;
import org.srm.mall.order.domain.vo.PurchaseRequestVO;
import org.srm.mall.other.api.dto.ShoppingCartDTO;
import org.srm.mall.other.api.dto.WatsonsPreRequestOrderDTO;
import org.srm.mall.other.api.dto.WatsonsShoppingCartDTO;
import org.srm.mall.other.domain.entity.AllocationInfo;
import org.srm.mall.platform.domain.vo.TaxVO;
import org.srm.mall.product.api.dto.ItemCategoryDTO;
import org.srm.mall.product.api.dto.QueryItemCodeDTO;
import org.srm.mall.region.domain.entity.Address;
import org.srm.mall.region.domain.entity.MallRegion;
import org.srm.mall.region.domain.repository.MallRegionRepository;
import org.srm.web.annotation.Tenant;

import javax.transaction.Transactional;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service("watsonsOmsOrderService")
@Tenant(WatsonsConstants.TENANT_NUMBER)
@Slf4j
public class WatsonsOmsOrderServiceImpl extends OmsOrderServiceImpl {
    @Autowired
    private CodeRuleBuilder codeRuleBuilder;
    @Autowired
    private SmodrRemoteService smodrRemoteService;
    @Autowired
    private MallRegionRepository mallRegionRepository;
    @Autowired
    private SmdmRemoteNewService smdmRemoteNewService;

    @Override
    @Transactional
    public PurchaseRequestVO createOrder(Long tenantId, String customizeUnitCode, List<PreRequestOrderDTO> preRequestOrderDTOs) {
        //?????????
        Map<Optional<Long>,String> batchNumMap = new HashMap<>();
        List<OmsOrderDto> omsOrderDtos = new ArrayList<>();
        for (PreRequestOrderDTO preRequestOrderDTOTemp : preRequestOrderDTOs) {
            if(!(preRequestOrderDTOTemp instanceof WatsonsPreRequestOrderDTO)){
                throw new CommonException(BaseConstants.ErrorCode.DATA_INVALID);
            }
            WatsonsPreRequestOrderDTO preRequestOrderDTO = (WatsonsPreRequestOrderDTO)preRequestOrderDTOTemp;
            String batchNum;
            //???????????????????????????
            if(batchNumMap.containsKey(Optional.ofNullable(preRequestOrderDTO.getShoppingCartDTOList().get(0).getItemCategoryId()))){
                batchNum = batchNumMap.get(Optional.ofNullable(preRequestOrderDTO.getShoppingCartDTOList().get(0).getItemCategoryId()));
            }else {
                batchNum = codeRuleBuilder.generateCode(ScecConstants.RuleCode.S2FUL_ORDER_BATCH_CODE, null);
                batchNumMap.put(Optional.ofNullable(preRequestOrderDTO.getShoppingCartDTOList().get(0).getItemCategoryId()),batchNum);
            }
            //??????????????????
            Map<Long,WatsonsShoppingCartDTO> shoppingCartDTOMap = preRequestOrderDTO.getWatsonsShoppingCartDTOList().stream().collect(Collectors.toMap(ShoppingCartDTO::getCartId,Function.identity()));
            for(int i = 0;i < preRequestOrderDTO.getShoppingCartDTOList().size();i++){
                WatsonsShoppingCartDTO watsonsShoppingCartDTO = shoppingCartDTOMap.get(preRequestOrderDTO.getShoppingCartDTOList().get(i).getCartId());
                ShoppingCartDTO shoppingCartDTO = new ShoppingCartDTO();
                BeanUtils.copyProperties(watsonsShoppingCartDTO,shoppingCartDTO);
                preRequestOrderDTO.getShoppingCartDTOList().set(i,shoppingCartDTO);
            }
            OmsOrderDto omsOrderDto = self().omsOrderDtoBuilder(tenantId, preRequestOrderDTO, batchNum);
            //????????????id
            omsOrderDto.getOrder().setAttributeBigint10(preRequestOrderDTO.getShoppingCartDTOList().get(0).getItemCategoryId());
            //feign??????mdm????????????????????????
            ResponseEntity<String> responseEntity = smdmRemoteNewService.queryItemById(tenantId,preRequestOrderDTO.getShoppingCartDTOList().get(0).getItemCategoryId());
            if(!ObjectUtils.isEmpty(responseEntity)){
                QueryItemCodeDTO queryItemCodeDTO = ResponseUtils.getResponse(responseEntity, new com.fasterxml.jackson.core.type.TypeReference<QueryItemCodeDTO>() {
                });
                //????????????code
                omsOrderDto.getOrder().setAttributeVarchar10(queryItemCodeDTO.getCategoryCode());
                //??????????????????
                omsOrderDto.getOrder().setAttributeVarchar11(queryItemCodeDTO.getCategoryName());
            }
            //?????????????????????
            List<WatsonsShoppingCartDTO> watsonsShoppingCartDTOList = preRequestOrderDTO.getWatsonsShoppingCartDTOList();
            if(Objects.nonNull(watsonsShoppingCartDTOList)){
                Map<Long, List<WatsonsShoppingCartDTO>> groupBy = watsonsShoppingCartDTOList.stream()
                        .collect(Collectors.groupingBy(WatsonsShoppingCartDTO::getProductId));
                omsOrderDto.getOrderEntryList().forEach(omsOrderEntry -> {
                    if(Objects.nonNull(omsOrderEntry.getSkuId())){
                        List<WatsonsShoppingCartDTO> watsonsShoppingCartDTOS = groupBy.get(omsOrderEntry.getSkuId());
                        if(CollectionUtils.isNotEmpty(watsonsShoppingCartDTOS)){
                            WatsonsShoppingCartDTO watsonsShoppingCartDTO = watsonsShoppingCartDTOS.get(0);
                            //????????????
                            omsOrderEntry.setInvOrganizationId(watsonsShoppingCartDTO.getAllocationInfoList().get(0).getCostShopId());
                            omsOrderEntry.setInvOrganizationCode(watsonsShoppingCartDTO.getAllocationInfoList().get(0).getCostShopCode());
                            omsOrderEntry.setInvOrganizationName(watsonsShoppingCartDTO.getAllocationInfoList().get(0).getCostShopName());
                            omsOrderEntry.setNeededDate(omsOrderDto.getOrder().getNeededDate());
                            //??????????????????
                            omsOrderEntry.setAttributeBigint7(watsonsShoppingCartDTO.getAllocationInfoList().get(0).getCostDepartmentId());
                            //??????????????????
                            omsOrderEntry.setAttributeVarchar8(watsonsShoppingCartDTO.getAllocationInfoList().get(0).getReceiveWarehouseCode());
                            //????????????
                            omsOrderEntry.setAttributeVarchar5(watsonsShoppingCartDTO.getAllocationInfoList().get(0).getProjectCostCode());
                            //?????????????????????
                            omsOrderEntry.setAttributeVarchar6(watsonsShoppingCartDTO.getAllocationInfoList().get(0).getProjectCostSubcategoryCode());
                            //?????????
                            omsOrderEntry.setAttributeVarchar7(watsonsShoppingCartDTO.getCmsNumber());
                            //????????????
                            omsOrderEntry.setAttributeLongtext1(JSONObject.toJSONString(watsonsShoppingCartDTO.getAllocationInfoList()));
                            log.debug("watsons allocationInfo:" + JSONObject.toJSONString(omsOrderEntry.getAttributeLongtext1()));
                            //?????????id
                            omsOrderEntry.setAttributeBigint9(preRequestOrderDTO.getReceiverContactId());
                            //????????????
                            if(Objects.nonNull(omsOrderEntry.getTaxId())){
                                ResponseEntity<String> taxResponseEntity = smdmRemoteNewService.selectTaxById(tenantId, omsOrderEntry.getTaxId());
                                if(!ObjectUtils.isEmpty(taxResponseEntity)){
                                    TaxVO tax = ResponseUtils.getResponse(taxResponseEntity, new TypeReference<TaxVO>() {
                                    });
                                    log.debug("?????????{}"+JSONObject.toJSONString(tax));
                                    omsOrderEntry.setAttributeVarchar3(tax.getDescription());
                                }
                            }
                        }
                    }
                });
            }
            //??????ce???
            omsOrderDto.getOrder().setAttributeVarchar2(preRequestOrderDTO.getCeNumber());
            //??????????????????
            omsOrderDto.getOrder().setAttributeVarchar3(preRequestOrderDTO.getWatsonsShoppingCartDTOList().get(0).getAllocationInfoList().get(0).getDeliveryType());
            omsOrderDtos.add(omsOrderDto);
        }
        log.info("?????????oms??????????????????:" + JSONObject.toJSONString(omsOrderDtos));
        ResponseEntity<String> result = smodrRemoteService.create(tenantId, customizeUnitCode, omsOrderDtos);
        log.info("?????????oms??????????????????:" + JSONObject.toJSONString(result));
        if (ResponseUtils.isFailed(result)) {
            //?????????????????????????????????????????????????????????
            String message = null;
            try {
                Exception exception = JSONObject.parseObject(result.getBody(), Exception.class);
                message = exception.getMessage();
            } catch (Exception e) {
                message = result.getBody();
            }
            throw new CommonException(message);
        }
        OmsResultDTO omsResultDTO = ResponseUtils.getResponse(result, OmsResultDTO.class);
        return self().returnVOBuilder(omsResultDTO,batchNumMap.entrySet().iterator().next().getValue());
    }

    @Override
    @Transactional
    public OmsOrderAddress getOmsOrderAddress(PreRequestOrderDTO preRequestOrderDTO, OmsOrder omsOrder){
        if(!(preRequestOrderDTO instanceof WatsonsPreRequestOrderDTO)){
            throw new CommonException(BaseConstants.ErrorCode.DATA_INVALID);
        }
        WatsonsPreRequestOrderDTO watsonsPreRequestOrderDTO = (WatsonsPreRequestOrderDTO)preRequestOrderDTO;
        OmsOrderAddress omsOrderAddress = new OmsOrderAddress();
        AllocationInfo allocationInfo = watsonsPreRequestOrderDTO.getWatsonsShoppingCartDTOList().get(0).getAllocationInfoList().get(0);
        if(Objects.isNull(allocationInfo.getLastRegionId())){
            throw new CommonException(BaseConstants.ErrorCode.NOT_NULL);
        }
        MallRegion mallRegion = mallRegionRepository.selectByPrimaryKey(allocationInfo.getLastRegionId());
        List<String> regionCode = Arrays.asList(mallRegion.getLevelPath().split("\\."));
        List<MallRegion> mallRegionList = mallRegionRepository.selectByCondition(Condition.builder(MallRegion.class).andWhere(Sqls.custom()
                .andIn(MallRegion.FIELD_REGION_CODE, regionCode)).build());
        Map<String, MallRegion> mallRegionMap = mallRegionList.stream().collect(Collectors.toMap(MallRegion::getRegionCode, Function.identity()));
        omsOrderAddress.setTenantId(omsOrder.getTenantId());
        omsOrderAddress.setRegionId(mallRegionMap.get(regionCode.get(0)).getRegionId());
        omsOrderAddress.setRegionCode(mallRegionMap.get(regionCode.get(0)).getRegionCode());
        omsOrderAddress.setRegionName(mallRegionMap.get(regionCode.get(0)).getRegionName());
        omsOrderAddress.setCityId(mallRegionMap.get(regionCode.get(1)).getRegionId());
        omsOrderAddress.setCityCode(mallRegionMap.get(regionCode.get(1)).getRegionCode());
        omsOrderAddress.setCityName(mallRegionMap.get(regionCode.get(1)).getRegionName());
        omsOrderAddress.setDistrictId(mallRegionMap.get(regionCode.get(2)).getRegionId());
        omsOrderAddress.setDistrictCode(mallRegionMap.get(regionCode.get(2)).getRegionCode());
        omsOrderAddress.setDistrictName(mallRegionMap.get(regionCode.get(2)).getRegionName());
        omsOrderAddress.setStreetId((regionCode.size() == 4) ? mallRegionMap.get(regionCode.get(3)).getRegionId() : null);
        omsOrderAddress.setStreetCode((regionCode.size() == 4) ? mallRegionMap.get(regionCode.get(3)).getRegionCode() : null);
        omsOrderAddress.setStreetName((regionCode.size() == 4) ? mallRegionMap.get(regionCode.get(3)).getRegionName() : null);
        omsOrderAddress.setAddress(allocationInfo.getFullAddress());
        omsOrderAddress.setFullAddress(allocationInfo.getAddressRegion()+allocationInfo.getFullAddress());
        omsOrderAddress.setContactName(StringUtils.defaultIfBlank(watsonsPreRequestOrderDTO.getReceiverContactName(),DetailsHelper.getUserDetails().getRealName()));
        omsOrderAddress.setMobilePhone(watsonsPreRequestOrderDTO.getMobile());
        omsOrderAddress.setCompanyId(omsOrder.getPurchaseCompanyId());
        omsOrderAddress.setCompanyName(omsOrder.getPurchaseCompanyName());
        omsOrderAddress.setAddressTypeCode("COMPANY");
        omsOrder.setReceiverAddressId(allocationInfo.getAddressId());
        return omsOrderAddress;
    }
}
