package org.srm.mall.other.app.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.ctrip.framework.apollo.util.ExceptionUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import io.choerodon.core.exception.CommonException;
import io.choerodon.core.oauth.CustomUserDetails;
import io.choerodon.core.oauth.DetailsHelper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.servicecomb.pack.omega.context.annotations.SagaStart;
import org.hzero.core.base.BaseConstants;
import org.hzero.core.util.ResponseUtils;
import org.hzero.mybatis.domian.Condition;
import org.hzero.mybatis.util.Sqls;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.srm.boot.common.CustomizeSettingCode;
import org.srm.boot.common.cache.impl.AbstractKeyGenerator;
import org.srm.boot.platform.customizesetting.CustomizeSettingHelper;
import org.srm.boot.saga.utils.SagaClient;
import org.srm.common.convert.bean.BeanConvertor;
import org.srm.mall.common.app.service.CommonService;
import org.srm.mall.common.constant.ScecConstants;
import org.srm.mall.common.feign.*;
import org.srm.mall.common.feign.dto.agreemnet.AgreementLine;
import org.srm.mall.common.feign.dto.agreemnet.PostageCalculateDTO;
import org.srm.mall.common.feign.dto.agreemnet.PostageCalculateLineDTO;
import org.srm.mall.common.feign.dto.product.*;
import org.srm.mall.common.feign.dto.wflCheck.WatsonsWflCheckDTO;
import org.srm.mall.common.feign.dto.wflCheck.WatsonsWflCheckResultVO;
import org.srm.mall.common.task.MallOrderAsyncTask;
import org.srm.mall.common.utils.TransactionalComponent;
import org.srm.mall.common.utils.snapshot.SnapshotUtil;
import org.srm.mall.context.dto.ProductDTO;
import org.srm.mall.context.entity.ECResult;
import org.srm.mall.context.entity.Item;
import org.srm.mall.context.entity.ItemCategory;
import org.srm.mall.context.entity.ECResult;
import org.srm.mall.infra.constant.WatsonsConstants;
import org.srm.mall.order.api.dto.PreRequestOrderResponseDTO;
import org.srm.mall.order.app.service.MallOrderCenterService;
import org.srm.mall.order.app.service.MallOrderService;
import org.srm.mall.order.app.service.OmsOrderService;
import org.srm.mall.order.domain.vo.PurchaseRequestVO;
import org.srm.mall.other.api.dto.*;
import org.srm.mall.other.app.service.*;
import org.srm.mall.other.domain.entity.*;
import org.srm.mall.other.domain.repository.*;
import org.srm.mall.platform.api.dto.PrHeaderCreateDTO;
import org.srm.mall.platform.domain.entity.*;
import org.srm.mall.platform.domain.repository.EcClientRepository;
import org.srm.mall.platform.domain.repository.EcCompanyAssignRepository;
import org.srm.mall.platform.domain.repository.EcPlatformRepository;
import org.srm.mall.product.api.dto.ItemCategoryDTO;
import org.srm.mall.product.api.dto.LadderPriceResultDTO;
import org.srm.mall.product.api.dto.PriceResultDTO;
import org.srm.mall.product.api.dto.SkuBaseInfoDTO;
import org.srm.mall.product.app.service.ProductStockService;
import org.srm.mall.product.domain.repository.ProductWorkbenchRepository;
import org.srm.mall.product.domain.repository.ProductWorkbenchRepository;
import org.srm.mall.product.domain.entity.ScecProductCategory;
import org.srm.mall.product.domain.repository.ProductWorkbenchRepository;
import org.srm.mall.region.api.dto.AddressDTO;
import org.srm.mall.region.domain.entity.Address;
import org.srm.mall.region.domain.entity.MallRegion;
import org.srm.mall.region.domain.repository.AddressRepository;
import org.srm.mall.region.domain.repository.MallRegionRepository;
import org.srm.mq.service.producer.MessageProducer;
import org.srm.web.annotation.Tenant;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service("watsonsShoppingCartService")
@Tenant(WatsonsConstants.TENANT_NUMBER)
public class WatsonsShoppingCartServiceImpl extends ShoppingCartServiceImpl implements WatsonsShoppingCartService {

    private static final Logger logger = LoggerFactory.getLogger(ShoppingCartServiceImpl.class);

    private static final String BUSINESS_CARD_CATEGORY_CODE = "BUSINESS_CARD";

    @Autowired
    private AllocationInfoRepository allocationInfoRepository;

    @Autowired
    private BudgetInfoRepository budgetInfoRepository;

    @Autowired
    private EcCompanyAssignRepository ecCompanyAssignRepository;

    @Autowired
    private EcClientRepository ecClientRepository;

    @Autowired
    private ShoppingCartRepository shoppingCartRepository;

    @Autowired
    private EcPlatformRepository ecPlatformRepository;

    @Autowired
    private AddressRepository addressRepository;

    @Autowired
    private PunchoutService punchoutService;

    @Autowired
    private MallOrderCenterService mallOrderCenterService;

    @Autowired
    private BudgetInfoService budgetInfoService;

    @Autowired
    private SnapshotUtil snapshotUtil;

    @Autowired
    private ShoppingCartService shoppingCartService;

    @Autowired
    private CustomizeSettingHelper customizeSettingHelper;

    @Autowired
    private MinPurchaseConfigRepository minPurchaseConfigRepository;

    @Autowired
    private SmdmRemoteService smdmRemoteService;

    @Autowired
    private ProductStockService productStockService;

    @Autowired
    private MallOrderService mallOrderService;

    @Autowired
    private MallOrderAsyncTask mallOrderAsyncTask;

    @Autowired
    private MixDeploymentService mixDeploymentService;

    @Autowired
    private SmdmRemoteNewService smdmRemoteNewService;


    @Autowired
    private WatsonsCeInfoRemoteService watsonsCeInfoRemoteService;

    @Autowired
    private MessageProducer messageProducer;

    @Autowired
    @Lazy
    private ProductService productService;

    @Autowired
    private SpcmRemoteNewService spcmRemoteNewService;

    @Autowired
    private SmpcRemoteService smpcRemoteService;

    @Autowired
    private SagmRemoteService sagmRemoteService;

    @Autowired
    private WatsonsSagmRemoteService watsonsSagmRemoteService;

    @Autowired
    private MallRegionRepository mallRegionRepository;

    @Autowired
    private WatsonsWflCheckRemoteService watsonsWflCheckRemoteService;

    @Autowired
    private ProductWorkbenchRepository productWorkbenchRepository;

    @Autowired
    private SifgOrderRemoteService sifgOrderRemoteService;


    @Autowired
    private CustomizedProductLineService customizedProductLineService;

    @Autowired
    private CustomizedProductLineRepository customizedProductLineRepository;

    @Autowired
    private CustomizedProductValueRepository customizedProductValueRepository;

    @Autowired
    private WatsonsCustomizedProductLineService watsonsCustomizedProductLineService;

    @Autowired
    @Lazy
    private AllocationInfoService allocationInfoService;

    private static final String erpForWatsons = "SRM";

    @Autowired
    private TransactionalComponent transactionalComponent;

    @Autowired
    private CommonService commonService;


    @Override
    public List<ShoppingCartDTO> watsonsShppingCartEnter(Long organizationId, ShoppingCart shoppingCart) {
        //?????????????????????????????????
        List<ShoppingCartDTO> shoppingCartDTOList = super.shppingCartEnter(organizationId, shoppingCart);
        List<WatsonsShoppingCartDTO> watsonsShoppingCartDTOS = transferToWatsonsShoppingCartDTOS(shoppingCartDTOList);
        selectCustomizedProductListForWatsons(organizationId,watsonsShoppingCartDTOS);
        if (!CollectionUtils.isEmpty(watsonsShoppingCartDTOS)) {
            List<AllocationInfo> allocationInfoList = allocationInfoRepository.selectByCondition(Condition.builder(AllocationInfo.class).andWhere(Sqls.custom()
                    .andIn(AllocationInfo.FIELD_CART_ID, watsonsShoppingCartDTOS.stream().map(WatsonsShoppingCartDTO::getCartId).collect(Collectors.toList()))).build());
            if (!CollectionUtils.isEmpty(allocationInfoList)) {
                for (AllocationInfo allocationInfo : allocationInfoList) {
                    allocationInfo.setTotalAmount(allocationInfo.getPrice().multiply(new BigDecimal(allocationInfo.getQuantity())));
                }
                Map<Long, List<AllocationInfo>> map = allocationInfoList.stream().collect(Collectors.groupingBy(AllocationInfo::getCartId));
                return watsonsShoppingCartDTOS.stream().map(s -> {
                    s.setAllocationInfoList(map.get(s.getCartId()));
                    String itemCode = checkItemCodeByItemId(s.getItemId(), organizationId, erpForWatsons);
                    logger.info("item code is " + itemCode);
                    String deliveryType = checkDeliveryType(itemCode, erpForWatsons, organizationId);
                    logger.info("delivery type is " + deliveryType);
                    if (!ObjectUtils.isEmpty(deliveryType)) {
                        if (deliveryType.equals(ScecConstants.ConstantNumber.STRING_1)) {
                            logger.info("set DIRECT_DELIVERY");
                            s.setDeliveryType("DIRECT_DELIVERY");
                            s.setDeliveryTypeMeaning("??????");
                        }
                    }
                    return s;
                }).collect(Collectors.toList());
            }
            return watsonsShoppingCartDTOS.stream().map(watsonsShoppingCartDTO -> {
                String itemCode = checkItemCodeByItemId(watsonsShoppingCartDTO.getItemId(), organizationId, erpForWatsons);
                logger.info("item code is " + itemCode);
                String deliveryType = checkDeliveryType(itemCode, erpForWatsons, organizationId);
                logger.info("delivery type is " + deliveryType);
                if (!ObjectUtils.isEmpty(deliveryType)) {
                    if (deliveryType.equals(ScecConstants.ConstantNumber.STRING_1)) {
                        logger.info("set DIRECT_DELIVERY");
                        watsonsShoppingCartDTO.setDeliveryType("DIRECT_DELIVERY");
                        watsonsShoppingCartDTO.setDeliveryTypeMeaning("??????");
                    }
                }
                return watsonsShoppingCartDTO;
            }).collect(Collectors.toList());
        }
        return shoppingCartDTOList;
    }

    private List<WatsonsShoppingCartDTO> transferToWatsonsShoppingCartDTOS(List<ShoppingCartDTO> shoppingCartDTOList) {
        List<WatsonsShoppingCartDTO> watsonsShoppingCartDTOS = new ArrayList<>();
        for (ShoppingCartDTO shoppingCartDTO : shoppingCartDTOList) {
            WatsonsShoppingCartDTO watsonsShoppingCartDTO = new WatsonsShoppingCartDTO();
            BeanUtils.copyProperties(shoppingCartDTO,watsonsShoppingCartDTO);
            watsonsShoppingCartDTOS.add(watsonsShoppingCartDTO);
        }
        return watsonsShoppingCartDTOS;
    }

    private String checkItemCodeByItemId(Long itemId, Long tenantId, String sourceCode) {
        return allocationInfoRepository.checkItemCodeByItemId(itemId, tenantId, sourceCode);
    }

    private String checkDeliveryType(String itemCode, String sourceCode, Long tenantId) {
        return allocationInfoRepository.checkDeliveryType(itemCode, sourceCode, tenantId);
    }

    @Override
    @SagaStart
    @Transactional(rollbackFor = Exception.class)
    public PreRequestOrderResponseDTO watsonsPreRequestOrder(Long tenantId, String customizeUnitCode, List<WatsonsPreRequestOrderDTO> watsonsPreRequestOrderDTOList) {
        //???????????????
        watsonsPreRequestOrderDTOList.forEach(watsonsPreRequestOrderDTO -> {
            checkCustomizedProductInfoForWatsons(tenantId, watsonsPreRequestOrderDTO.getWatsonsShoppingCartDTOList());
        });
        processOrderRemark(watsonsPreRequestOrderDTOList);
        processNormalReceiveContactId(watsonsPreRequestOrderDTOList);
        //??????ceNo???discription??????
        saveCeAndCMS(watsonsPreRequestOrderDTOList);
        //??????ce??????????????????????????????
        modifyProjectCostByCeInfo(watsonsPreRequestOrderDTOList);
        //wlf???????????????
        checkWLFFlow(tenantId, watsonsPreRequestOrderDTOList);
        //??????cms???????????????
        List<PcOccupyDTO> pcOccupyDTOS = occupyCMS(tenantId, watsonsPreRequestOrderDTOList);
        //        ??????ceNo??????
        checkCeInfo(tenantId, watsonsPreRequestOrderDTOList);
        //??????oms?????????????????????ce??????
        //??????oms???????????? ??????????????? cms???????????????
        //??????oms???????????? ????????????????????????????????????
        List<PrHeaderCreateDTO> errorListForWatsonsPrHeaderCreateDTO = new ArrayList<>();
        List<WatsonsPreRequestOrderDTO> errorListForWatsonsPreOrderDTOForCE = new ArrayList<>();
        Exception omsException = null;
        PreRequestOrderResponseDTO preRequestOrderResponseDTO = new PreRequestOrderResponseDTO();
        try {
            watsonsPreRequestOrderDTOList.forEach(watsonsPreRequestOrderDTO -> {watsonsPreRequestOrderDTO.priceFinancialPrecisionSetting();});
                preRequestOrderResponseDTO = super.preRequestOrder(tenantId, customizeUnitCode, new ArrayList<>(watsonsPreRequestOrderDTOList));
        }catch (Exception e){
                logger.error("oms create order error. all orders are failed!");
                logger.error("start to rollback ce occupy");
            errorListForWatsonsPreOrderDTOForCE.addAll(watsonsPreRequestOrderDTOList);
                omsException = e;
        }finally {
            if(!ObjectUtils.isEmpty(preRequestOrderResponseDTO.getPrResult())) {
                if(!CollectionUtils.isEmpty(preRequestOrderResponseDTO.getPrResult().getErrorList())) {
                    errorListForWatsonsPrHeaderCreateDTO.addAll(preRequestOrderResponseDTO.getPrResult().getErrorList());
                    logger.info("the errorListForWatsonsPrHeaderCreateDTO is {}", JSONObject.toJSON(errorListForWatsonsPrHeaderCreateDTO));
                }
            }
        }
        //????????????
        processPrheaderCreateDTOExceptionCERollback(tenantId, watsonsPreRequestOrderDTOList, errorListForWatsonsPrHeaderCreateDTO);
        processPrheaderCreateDTOExceptionCMSUpdate(tenantId, watsonsPreRequestOrderDTOList,errorListForWatsonsPrHeaderCreateDTO,pcOccupyDTOS);
        //????????????
        processOmsAllFailedExceptionCERollback(tenantId, errorListForWatsonsPreOrderDTOForCE);
        if(!ObjectUtils.isEmpty(omsException)){
            throw  new CommonException(omsException);
        }
        return preRequestOrderResponseDTO;
    }

    private void processOrderRemark(List<WatsonsPreRequestOrderDTO> watsonsPreRequestOrderDTOList) {
        for (WatsonsPreRequestOrderDTO watsonsPreRequestOrderDTO : watsonsPreRequestOrderDTOList) {
            for (ShoppingCartDTO shoppingCartDTO : watsonsPreRequestOrderDTO.getShoppingCartDTOList()) {
                for (WatsonsShoppingCartDTO watsonsShoppingCartDTO : watsonsPreRequestOrderDTO.getWatsonsShoppingCartDTOList()) {
                    if(watsonsShoppingCartDTO.getCartId().equals(shoppingCartDTO.getCartId())){
                        watsonsShoppingCartDTO.setRemark(shoppingCartDTO.getRemark());
                    }
                }
            }
        }
    }

    private void processNormalReceiveContactId(List<WatsonsPreRequestOrderDTO> watsonsPreRequestOrderDTOList) {
        for (WatsonsPreRequestOrderDTO watsonsPreRequestOrderDTO : watsonsPreRequestOrderDTOList) {
            if(ObjectUtils.isEmpty(watsonsPreRequestOrderDTO.getReceiverContactId())){
                watsonsPreRequestOrderDTO.setReceiverContactId(DetailsHelper.getUserDetails().getUserId());
            }
        }
    }

    private void modifyProjectCostByCeInfo(List<WatsonsPreRequestOrderDTO> watsonsPreRequestOrderDTOList) {
        for (WatsonsPreRequestOrderDTO watsonsPreRequestOrderDTO : watsonsPreRequestOrderDTOList) {
            if(!ObjectUtils.isEmpty(watsonsPreRequestOrderDTO.getCeNumber())){
                for (WatsonsShoppingCartDTO watsonsShoppingCartDTO : watsonsPreRequestOrderDTO.getWatsonsShoppingCartDTOList()) {
                    for (AllocationInfo allocationInfo : watsonsShoppingCartDTO.getAllocationInfoList()) {
                        allocationInfo.setProjectCostCode("1406");
                        allocationInfo.setProjectCostName("??????????????????");
                    }
                }
            }
        }
    }

    private void processPrheaderCreateDTOExceptionCMSUpdate(Long tenantId, List<WatsonsPreRequestOrderDTO> watsonsPreRequestOrderDTOList, List<PrHeaderCreateDTO> errorListForWatsonsPrHeaderCreateDTO,List<PcOccupyDTO> pcOccupyDTOS) {
        if(CollectionUtils.isEmpty(errorListForWatsonsPrHeaderCreateDTO) || CollectionUtils.isEmpty(pcOccupyDTOS)){
            return;
        }
        List<PcOccupyDTO> pcOccupyDTOListNeedToCancelForThisOrder = new ArrayList<>();
        for (PrHeaderCreateDTO prHeaderCreateDTO : errorListForWatsonsPrHeaderCreateDTO) {
            for (WatsonsPreRequestOrderDTO watsonsPreRequestOrderDTO : watsonsPreRequestOrderDTOList) {
                if(watsonsPreRequestOrderDTO.getPreRequestOrderNumber().equals(prHeaderCreateDTO.getPreRequestOrderNumber())){
                    logger.info("the oms error order is {}",JSONObject.toJSON(watsonsPreRequestOrderDTO));
                    for (WatsonsShoppingCartDTO watsonsShoppingCartDTO : watsonsPreRequestOrderDTO.getWatsonsShoppingCartDTOList()) {
                        for (PcOccupyDTO pcOccupyDTO : pcOccupyDTOS) {
                            if(!ObjectUtils.isEmpty(watsonsShoppingCartDTO.getCmsNumber()) && pcOccupyDTO.getPcNum().equals(watsonsShoppingCartDTO.getCmsNumber())){
                                pcOccupyDTOListNeedToCancelForThisOrder.add(pcOccupyDTO);
                            }
                        }
                    }
                }
                pcOccupyDTOListNeedToCancelForThisOrder.forEach(pcOccupyDTO -> {
                    pcOccupyDTO.setOperationType(WatsonsConstants.operationTypeCode.SPCM_CANCEL);
                    Long version = pcOccupyDTO.getVersion();
                    pcOccupyDTO.setVersion(version+1L);
                });
                logger.info("pcOccupyDTOListNeedToCancelForThisOrder is {}",JSONObject.toJSON(pcOccupyDTOListNeedToCancelForThisOrder));
                cancelBySpcmForPreOrder(tenantId,pcOccupyDTOListNeedToCancelForThisOrder);
            }
        }
    }

    private void processOmsAllFailedExceptionCERollback(Long tenantId, List<WatsonsPreRequestOrderDTO> errorListForWatsonsPreOrderDTO) {
        if (CollectionUtils.isEmpty(errorListForWatsonsPreOrderDTO)) {
            return;
        }
        for (WatsonsPreRequestOrderDTO watsonsPreRequestOrderDTO : errorListForWatsonsPreOrderDTO) {
            if (!ObjectUtils.isEmpty(watsonsPreRequestOrderDTO.getCeNumber())) {
                CheckCeInfoDTO checkCeInfoDTO = buildCECheckInfoDTO(tenantId, watsonsPreRequestOrderDTO);
                ResponseEntity<String> checkCeInfoRes = watsonsCeInfoRemoteService.checkCeInfo(tenantId, checkCeInfoDTO);
                if (ResponseUtils.isFailed(checkCeInfoRes)) {
                    String message = null;
                    try {
                        Exception exception = JSONObject.parseObject(checkCeInfoRes.getBody(), Exception.class);
                        message = exception.getMessage();
                    } catch (Exception e) {
                        message = checkCeInfoRes.getBody();
                    }
                    logger.error("check CE info for order total amount error!  ce id is " + watsonsPreRequestOrderDTO.getCeId());
                    throw new CommonException("??????CE???" + watsonsPreRequestOrderDTO.getCeNumber() + "??????," + message);
                }
                logger.info("check CE info for order total amount success! ce id is" + watsonsPreRequestOrderDTO.getCeId());
            }
        }
    }
    private void processPrheaderCreateDTOExceptionCERollback(Long tenantId, List<WatsonsPreRequestOrderDTO> watsonsPreRequestOrderDTOList, List<PrHeaderCreateDTO> errorListForWatsonsPrHeaderCreateDTO) {
        if (CollectionUtils.isEmpty(errorListForWatsonsPrHeaderCreateDTO)) {
            return;
        }
        for (PrHeaderCreateDTO prHeaderCreateDTO : errorListForWatsonsPrHeaderCreateDTO) {
            watsonsPreRequestOrderDTOList.forEach(watsonsPreRequestOrderDTO -> {
                if (watsonsPreRequestOrderDTO.getPreRequestOrderNumber().equals(prHeaderCreateDTO.getPreRequestOrderNumber())) {
                    if (!ObjectUtils.isEmpty(watsonsPreRequestOrderDTO.getCeNumber())) {
                        CheckCeInfoDTO checkCeInfoDTO = buildCECheckInfoDTO(tenantId, watsonsPreRequestOrderDTO);
                        ResponseEntity<String> checkCeInfoRes = watsonsCeInfoRemoteService.checkCeInfo(tenantId, checkCeInfoDTO);
                        if (ResponseUtils.isFailed(checkCeInfoRes)) {
                            String message = null;
                            try {
                                Exception exception = JSONObject.parseObject(checkCeInfoRes.getBody(), Exception.class);
                                message = exception.getMessage();
                            } catch (Exception e) {
                                message = checkCeInfoRes.getBody();
                            }
                            logger.error("check CE info for order total amount error!  ce id is " + watsonsPreRequestOrderDTO.getCeId());
                            throw new CommonException("??????CE???" + watsonsPreRequestOrderDTO.getCeNumber() + "??????," + message);
                        }
                        logger.info("check CE info for order total amount success! ce id is" + watsonsPreRequestOrderDTO.getCeId());
                    }
                }
            });
        }
    }
    private CheckCeInfoDTO buildCECheckInfoDTO(Long tenantId, WatsonsPreRequestOrderDTO watsonsPreRequestOrderDTO) {
        CheckCeInfoDTO checkCeInfoDTO = new CheckCeInfoDTO();
        checkCeInfoDTO.setCeId(watsonsPreRequestOrderDTO.getCeId());
        BigDecimal withoutTaxPriceTotal = queryWithoutTaxPrice(tenantId, watsonsPreRequestOrderDTO);
        //????????????????????????  ??????????????????
        checkCeInfoDTO.setChangeAmount(withoutTaxPriceTotal.negate());
        checkCeInfoDTO.setItemName(watsonsPreRequestOrderDTO.getItemName());
        checkCeInfoDTO.setTranscationId(watsonsPreRequestOrderDTO.getPreRequestOrderNumber());
        return checkCeInfoDTO;
    }

    private BigDecimal queryWithoutTaxPrice(Long tenantId, WatsonsPreRequestOrderDTO watsonsPreRequestOrderDTO) {
        //???????????????  ????????????????????????
        BigDecimal withoutTaxPriceTotal = new BigDecimal(0);
        for (WatsonsShoppingCartDTO watsonsShoppingCartDTO : watsonsPreRequestOrderDTO.getWatsonsShoppingCartDTOList()) {
            ProductDTO productDTO = productService.selectByProduct(watsonsShoppingCartDTO.getProductId(), tenantId, watsonsShoppingCartDTO.getCompanyId(), watsonsShoppingCartDTO.getPurchaseType(), watsonsShoppingCartDTO.getSecondRegionId(), watsonsShoppingCartDTO.getLevelPath());
            if (!ObjectUtils.isEmpty(productDTO.getWithoutTaxPrice())) {
                BigDecimal quantity = watsonsShoppingCartDTO.getQuantity();
                BigDecimal withoutTaxPriceParam = productDTO.getWithoutTaxPrice().multiply(quantity);
                //???????????????????????????
                withoutTaxPriceTotal = withoutTaxPriceTotal.add(withoutTaxPriceParam);
            }
            if ("LADDER_PRICE".equals(productDTO.getPriceType())) {
                BigDecimal quantity = watsonsShoppingCartDTO.getQuantity();
                for (LadderPriceResultDTO ladderPriceResultDTO : productDTO.getLadderPriceList()) {
                    if (ladderPriceResultDTO.getLadderFrom().compareTo(quantity) <= 0 && (ObjectUtils.isEmpty(ladderPriceResultDTO.getLadderTo()) || ladderPriceResultDTO.getLadderTo().compareTo(quantity) > 0)) {
                        BigDecimal withoutTaxPriceParam = ladderPriceResultDTO.getWithoutTaxPrice().multiply(quantity);
                        withoutTaxPriceTotal = withoutTaxPriceTotal.add(withoutTaxPriceParam);
                    }
                }
            }
        }
        withoutTaxPriceTotal = withoutTaxPriceTotal.add(watsonsPreRequestOrderDTO.getWithoutTaxFreightPrice());
        return withoutTaxPriceTotal;
    }

    private void checkWLFFlow(Long tenantId, List<WatsonsPreRequestOrderDTO> preRequestOrderDTOList) {
        //????????????????????????   ??????????????????????????????
        //??????wfl?????????
        List<WatsonsPreRequestOrderDTO> watsonsCheckSubmitList = preRequestOrderDTOList.stream().filter(item -> ScecConstants.ConstantNumber.INT_1 == item.getMinPurchaseFlag()).collect(Collectors.toList());
        List<WatsonsWflCheckDTO> watsonsWflCheckDTOS = buildWflCheckParams(tenantId, watsonsCheckSubmitList);
        ResponseEntity<String> watsonsWflCheckResultVOResponseEntity = watsonsWflCheckRemoteService.wflStartCheck(tenantId, watsonsWflCheckDTOS);
        if (ResponseUtils.isFailed(watsonsWflCheckResultVOResponseEntity)) {
            logger.error("????????????:??????wfl??????????????????????????????");
            throw new CommonException("????????????:??????wfl??????????????????????????????");
        } else {
            logger.info("check wfl flow success");
            WatsonsWflCheckResultVO response = ResponseUtils.getResponse(watsonsWflCheckResultVOResponseEntity, new TypeReference<WatsonsWflCheckResultVO>() {
            });
            if (response.getErrorFlag().equals(BaseConstants.Flag.YES)) {
                logger.error(response.getErrorMessage());
                throw new CommonException(response.getErrorMessage());
            }
        }
    }

    private void checkCeInfo(Long tenantId, List<WatsonsPreRequestOrderDTO> preRequestOrderDTOList) {
        for (WatsonsPreRequestOrderDTO watsonsPreRequestOrderDTO : preRequestOrderDTOList) {
            if (!ObjectUtils.isEmpty(watsonsPreRequestOrderDTO.getCeNumber())) {
                CheckCeInfoDTO checkCeInfoDTO = new CheckCeInfoDTO();
                checkCeInfoDTO.setCeId(watsonsPreRequestOrderDTO.getCeId());
                //???????????????  ????????????????????????
                BigDecimal withoutTaxPriceTotal = queryWithoutTaxPrice(tenantId, watsonsPreRequestOrderDTO);
                checkCeInfoDTO.setChangeAmount(withoutTaxPriceTotal);
                checkCeInfoDTO.setItemName(watsonsPreRequestOrderDTO.getItemName());
                checkCeInfoDTO.setTranscationId(watsonsPreRequestOrderDTO.getPreRequestOrderNumber());
                ResponseEntity<String> checkCeInfoRes = watsonsCeInfoRemoteService.checkCeInfo(tenantId, checkCeInfoDTO);
                if (ResponseUtils.isFailed(checkCeInfoRes)) {
                    String message = null;
                    try {
                        Exception exception = JSONObject.parseObject(checkCeInfoRes.getBody(), Exception.class);
                        message = exception.getMessage();
                    } catch (Exception e) {
                        message = checkCeInfoRes.getBody();
                    }
                    logger.error("check CE info for order total amount error!  ce id is " + watsonsPreRequestOrderDTO.getCeId());
                    throw new CommonException("??????CE???" + watsonsPreRequestOrderDTO.getCeNumber() + "??????," + message);
                }
                logger.info("check CE info for order total amount success! ce id is" + watsonsPreRequestOrderDTO.getCeId());
            }
        }
    }

    private List<PcOccupyDTO> occupyCMS(Long tenantId, List<WatsonsPreRequestOrderDTO> preRequestOrderDTOList) {
        List<PcOccupyDTO> pcOccupyDTOS4Show = new ArrayList<>();
        List<PcOccupyDTO> pcOccupyDTOS4Occupy = new ArrayList<>();
        for (WatsonsPreRequestOrderDTO watsonsPreRequestOrderDTO : preRequestOrderDTOList) {
            pcOccupyDTOS4Occupy.clear();
            //??????????????????????????????????????????????????????????????????  ??????????????????cms??????  ?????????????????????????????????????????????
            // ???????????????????????????
            for (WatsonsShoppingCartDTO watsonsShoppingCartDTO : watsonsPreRequestOrderDTO.getWatsonsShoppingCartDTOList()) {
                //????????????????????????????????????entry.getValue   entry?????????????????????????????????????????????????????????????????????
                if(!ObjectUtils.isEmpty(watsonsShoppingCartDTO.getCmsNumber())){
                    PcOccupyDTO pcOccupyDTO = new PcOccupyDTO();
                    pcOccupyDTO.setTenantId(watsonsShoppingCartDTO.getTenantId());
                    pcOccupyDTO.setSourceId(watsonsShoppingCartDTO.getAllocationInfoList().get(0).getAllocationId());
                    pcOccupyDTO.setSourceType(WatsonsConstants.smalSourceType.SMAL_PRE);
                    //?????????????????????
                    BigDecimal includeTaxPrice = new BigDecimal(0);
                    ProductDTO productDTO = productService.selectByProduct(watsonsShoppingCartDTO.getProductId(), tenantId, watsonsShoppingCartDTO.getCompanyId(), watsonsShoppingCartDTO.getPurchaseType(), watsonsShoppingCartDTO.getSecondRegionId(), watsonsShoppingCartDTO.getLevelPath());
                    if (!ObjectUtils.isEmpty(productDTO.getSellPrice())) {
                        BigDecimal quantity = watsonsShoppingCartDTO.getQuantity();
                        BigDecimal includeTaxPriceParam = productDTO.getSellPrice().multiply(quantity);
                        includeTaxPrice = includeTaxPrice.add(includeTaxPriceParam);
                    }
                    if (productDTO.getLadderEnableFlag().equals(1L)) {
                        BigDecimal quantity = watsonsShoppingCartDTO.getQuantity();
                        if (!CollectionUtils.isEmpty(productDTO.getLadderPriceList())) {
                            List<LadderPriceResultDTO> productPoolLadders = productDTO.getLadderPriceList().stream().map(LadderPriceResultDTO::new)
                                    .sorted(Comparator.comparing(LadderPriceResultDTO::getLadderFrom)).collect(Collectors.toList());
                            // ???????????????
                            boolean hasFlag = true;
                            for (LadderPriceResultDTO productPoolLadder : productPoolLadders) {
                                if (productPoolLadder.getLadderFrom().compareTo(quantity) <= 0 && (ObjectUtils.isEmpty(productPoolLadder.getLadderTo()) || productPoolLadder.getLadderTo().compareTo(quantity) > 0)) {
                                    BigDecimal includeTaxPriceParam = productPoolLadder.getSalePrice().multiply(quantity);
                                    includeTaxPrice = includeTaxPrice.add(includeTaxPriceParam);
                                    hasFlag = false;
                                    break;
                                }
                            }
                            if (hasFlag) {
//                        ????????????????????????????????????
                                LadderPriceResultDTO productPoolLadder = productPoolLadders.get(productPoolLadders.size() - 1);
                                BigDecimal includeTaxPriceParam = productPoolLadder.getSalePrice().multiply(quantity);
                                includeTaxPrice = includeTaxPrice.add(includeTaxPriceParam);
                            }
                        } else {
                            logger.warn("?????????????????????????????????!???????????????:" + productDTO.getProductNum());
                        }
                    }
                    pcOccupyDTO.setOccupyAmount(includeTaxPrice);
                    pcOccupyDTO.setOperationType(WatsonsConstants.operationTypeCode.SPCM_OCCUPY);
                    pcOccupyDTO.setPcNum(watsonsShoppingCartDTO.getCmsNumber());
                    pcOccupyDTO.setVersion(1L);
                    pcOccupyDTOS4Occupy.add(pcOccupyDTO);
                    pcOccupyDTOS4Show.add(pcOccupyDTO);
                }
            }
            //????????????????????????  ??????????????????cms???????????????????????????
            occupyBySpcmForPreOrder(tenantId, pcOccupyDTOS4Occupy);
        }
        return pcOccupyDTOS4Show;
    }

    private void occupyBySpcmForPreOrder(Long tenantId, List<PcOccupyDTO> pcOccupyDTOS) {
        if(!CollectionUtils.isEmpty(pcOccupyDTOS)){
            String sagaKey = SagaClient.getSagaKey();
            ResponseEntity<String> cmsOccupyResult = spcmRemoteNewService.occupy(sagaKey, tenantId, pcOccupyDTOS);
            if (ResponseUtils.isFailed(cmsOccupyResult)) {
                logger.error("occupy CMS price error! param pcOccupyDTOS: {}", JSONObject.toJSON(pcOccupyDTOS));
                throw new CommonException("CMS????????????????????????!");
            }
            ItfBaseBO itfBaseBO  = ResponseUtils.getResponse(cmsOccupyResult, new TypeReference<ItfBaseBO>() {
            });
            if(itfBaseBO.getErrorFlag() == 1 && !ObjectUtils.isEmpty(itfBaseBO.getErrorMessage())){
                logger.error("occupy CMS price error! param pcOccupyDTOS: {}", JSONObject.toJSON(pcOccupyDTOS));
                throw new CommonException("??????CMS???????????????,????????????: " + itfBaseBO.getErrorMessage());
            }
            logger.info("occupy CMS price success! param pcOccupyDTOS: {}", JSONObject.toJSON(pcOccupyDTOS));
        }
    }

    private void cancelBySpcmForPreOrder(Long tenantId, List<PcOccupyDTO> pcOccupyDTOS) {
        if(!CollectionUtils.isEmpty(pcOccupyDTOS)){
            String sagaKey = SagaClient.getSagaKey();
            ResponseEntity<String> cmsOccupyResult = spcmRemoteNewService.occupy(sagaKey, tenantId, pcOccupyDTOS);
            if (ResponseUtils.isFailed(cmsOccupyResult)) {
                logger.error("cancel CMS price error! param pcOccupyDTOS: {}", JSONObject.toJSON(pcOccupyDTOS));
                throw new CommonException("CMS??????????????????????????????!");
            }
            ItfBaseBO itfBaseBO  = ResponseUtils.getResponse(cmsOccupyResult, new TypeReference<ItfBaseBO>() {
            });
            if(itfBaseBO.getErrorFlag() == 1 && !ObjectUtils.isEmpty(itfBaseBO.getErrorMessage())){
                logger.error("cancel CMS price error! param pcOccupyDTOS: {}", JSONObject.toJSON(pcOccupyDTOS));
                throw new CommonException("CMS??????????????????,????????????: " + itfBaseBO.getErrorMessage());
            }
            logger.info("cancel CMS price success! param pcOccupyDTOS: {}", JSONObject.toJSON(pcOccupyDTOS));
        }
    }

    private void saveCeAndCMS(List<WatsonsPreRequestOrderDTO> preRequestOrderDTOList) {
        for (WatsonsPreRequestOrderDTO watsonsPreRequestOrderDTO : preRequestOrderDTOList) {
            if (!ObjectUtils.isEmpty(watsonsPreRequestOrderDTO.getCeNumber())) {
                for (WatsonsShoppingCartDTO watsonsShoppingCartDTO : watsonsPreRequestOrderDTO.getWatsonsShoppingCartDTOList()) {
                    for (AllocationInfo allocationInfo : watsonsShoppingCartDTO.getAllocationInfoList()) {
                        allocationInfo.setCeNumber(watsonsPreRequestOrderDTO.getCeNumber());
                        if (!ObjectUtils.isEmpty(watsonsPreRequestOrderDTO.getDiscription())) {
                            allocationInfo.setCeDiscription(watsonsPreRequestOrderDTO.getDiscription());
                        }
                        allocationInfoRepository.updateByPrimaryKeySelective(allocationInfo);
                    }
                }
            }
        }
    }

    private ItemCategoryDTO queryItemCategoryInfoById(Long tenantId, Long itemCategoryId) {
        if (ObjectUtils.isEmpty(itemCategoryId)) {
            throw new CommonException("???????????????????????????????????????????????????????????????!");
        }
        ResponseEntity<String> resString = smdmRemoteNewService.queryById(tenantId, itemCategoryId.toString());
        if (ResponseUtils.isFailed(resString)) {
            logger.error("????????????????????????????????????????????????????????????????????????????????????!");
            throw new CommonException("????????????????????????????????????????????????????????????????????????????????????!");
        } else {
            logger.info("query item category info success!");
            ItemCategoryDTO response = ResponseUtils.getResponse(resString, new TypeReference<ItemCategoryDTO>() {
            });
            return response;
        }
    }

    private Integer checkLevelOfItemCategory(Long tenantId, Long itemCategoryId) {
        if (ObjectUtils.isEmpty(itemCategoryId)) {
            throw new CommonException("???????????????????????????????????????????????????????????????! ");
        }
        ResponseEntity<String> stringResponseEntity = smdmRemoteNewService.queryById(tenantId, itemCategoryId.toString());
        if (ResponseUtils.isFailed(stringResponseEntity)) {
            logger.error("????????????????????????????????????????????????????????????????????????????????????!");
            throw new CommonException("????????????????????????????????????????????????????????????????????????????????????!");
        } else {
            logger.info("query item category info success!");
            ItemCategoryDTO response = ResponseUtils.getResponse(stringResponseEntity, new TypeReference<ItemCategoryDTO>() {
            });
            return response.getLevelPath().split("\\|").length;
        }
    }

    private List<WatsonsWflCheckDTO> buildWflCheckParams(Long tenantId, List<WatsonsPreRequestOrderDTO> canSubmitList) {
        List<WatsonsWflCheckDTO> watsonsWflCheckDTOS = new ArrayList<>();
        for (WatsonsPreRequestOrderDTO watsonsPreRequestOrderDTO : canSubmitList) {
            for (WatsonsShoppingCartDTO watsonsShoppingCartDTO : watsonsPreRequestOrderDTO.getWatsonsShoppingCartDTOList()) {
                Long firstItemCategoryId = null;
                Integer level = null;
                Long id = watsonsShoppingCartDTO.getItemCategoryId();
                level = checkLevelOfItemCategory(tenantId, id);
                while (level > 2) {
                    Integer levelRes = checkLevelOfItemCategory(tenantId, id);
                    level = levelRes;
                    ItemCategoryDTO itemCategoryDTO = queryItemCategoryInfoById(tenantId, id);
                    id = itemCategoryDTO.getParentCategoryId();
                }
                firstItemCategoryId = id;
                WatsonsWflCheckDTO watsonsWflCheckDTO = new WatsonsWflCheckDTO();
                if (ObjectUtils.isEmpty(firstItemCategoryId)) {
                    logger.error("?????????????????????????????????{}", JSONObject.toJSON(watsonsShoppingCartDTO));
                    throw new CommonException("?????????????????????????????????{}", JSONObject.toJSON(watsonsShoppingCartDTO));
                }
                watsonsWflCheckDTO.setCategoryId(firstItemCategoryId);
                List<String> costShopCodes = watsonsShoppingCartDTO.getAllocationInfoList().stream().map(AllocationInfo::getCostShopCode).collect(Collectors.toList());
                watsonsWflCheckDTO.setStoreIdList(costShopCodes);
                watsonsWflCheckDTOS.add(watsonsWflCheckDTO);
            }
        }
        return watsonsWflCheckDTOS;
    }


    @Override
    public List<WatsonsAddressDTO> checkAddress(Long organizationId, Long watsonsOrganizationId, String watsonsOrganizationCode) {

        if (ObjectUtils.isEmpty(watsonsOrganizationId) && ObjectUtils.isEmpty(watsonsOrganizationCode)) {
            throw new CommonException("?????????????????????id??????????????????, ?????????????????????????????????????????????????????????????????????!");
        }
        //?????????id???
        if (!ObjectUtils.isEmpty(watsonsOrganizationId)) {
            logger.info("??????????????????id?????????????????????????????????!");
            List<WatsonsAddressDTO> watsonsAddressDTOS = new ArrayList<>();
            //??????????????????????????????watsonsAddress
            List<Address> addressList = addressRepository.selectByCondition(Condition.builder(Address.class).andWhere(
                    Sqls.custom().andEqualTo(Address.FIELD_TENANTID_ID, organizationId).andEqualTo(Address.FIELD_ADDRESS_TYPE, ScecConstants.AdressType.RECEIVER)
                            .andEqualTo(Address.FIELD_INV_ORGANIZATION_ID, watsonsOrganizationId)).build());
            if (!CollectionUtils.isEmpty(addressList)) {
                //address?????????watsonsAddress
                for (Address address : addressList) {
                    WatsonsAddressDTO watsonsAddressDTO = new WatsonsAddressDTO();
                    BeanUtils.copyProperties(address, watsonsAddressDTO);
                    watsonsAddressDTOS.add(watsonsAddressDTO);
                }
                //??????????????????
                for (WatsonsAddressDTO watsonsAddressDTO : watsonsAddressDTOS) {
                    Long regionId = watsonsAddressDTO.getRegionId();
                    WatsonsRegionDTO watsonsRegionDTO = allocationInfoRepository.selectRegionInfoByRegionId(regionId);
                    if (ObjectUtils.isEmpty(watsonsRegionDTO)) {
                        throw new CommonException("??????????????????id?????????????????????!");
                    }
                    String levelPath = watsonsRegionDTO.getLevelPath();
                    String[] splitRes = levelPath.split("\\.");
                    StringBuffer sb = new StringBuffer();
                    for (int i = 0; i < splitRes.length; i++) {
                        WatsonsRegionDTO res = allocationInfoRepository.selectRegionInfoByRegionCode(splitRes[i]);
                        if (ObjectUtils.isEmpty(res)) {
                            throw new CommonException("??????????????????code?????????????????????!");
                        }
                        sb.append(res.getRegionName());
                    }
                    String regionRes = sb.toString();
                    watsonsAddressDTO.setAddressRegion(regionRes);
                }
                return watsonsAddressDTOS;
            } else {
                WhLovResultDTO infoDTO = allocationInfoRepository.selectInvInfoByInvId(watsonsOrganizationId, organizationId);
                logger.error(infoDTO.getInventoryCode() + "-" + infoDTO.getInventoryName() + "????????????????????????????????????????????????????????????!");
                throw new CommonException(infoDTO.getInventoryCode() + "-" + infoDTO.getInventoryName() + "????????????????????????????????????????????????????????????!");
            }
        }

        //id?????????code???
        if (!ObjectUtils.isEmpty(watsonsOrganizationCode)) {
            logger.info("??????????????????code?????????????????????????????????!");
            List<WatsonsAddressDTO> watsonsAddressDTOS = new ArrayList<>();
            //??????????????????????????????watsonsAddress
            //hpfm??????code??????id
            AddressDTO addressDTO = allocationInfoRepository.selectIdByCode(organizationId, watsonsOrganizationCode);
            if (!ObjectUtils.isEmpty(addressDTO.getInvOrganizationId())) {
                List<Address> addressList = addressRepository.selectByCondition(Condition.builder(Address.class).andWhere(
                        Sqls.custom().andEqualTo(Address.FIELD_TENANTID_ID, organizationId).andEqualTo(Address.FIELD_ADDRESS_TYPE, ScecConstants.AdressType.RECEIVER)
                                .andEqualTo(Address.FIELD_INV_ORGANIZATION_ID, addressDTO.getInvOrganizationId())).build());
                if (!CollectionUtils.isEmpty(addressList)) {
                    //address?????????watsonsAddress
                    for (Address address : addressList) {
                        WatsonsAddressDTO watsonsAddressDTO = new WatsonsAddressDTO();
                        BeanUtils.copyProperties(address, watsonsAddressDTO);
                        watsonsAddressDTOS.add(watsonsAddressDTO);
                    }
                    //??????????????????
                    for (WatsonsAddressDTO watsonsAddressDTO : watsonsAddressDTOS) {
                        Long regionId = watsonsAddressDTO.getRegionId();
                        WatsonsRegionDTO watsonsRegionDTO = allocationInfoRepository.selectRegionInfoByRegionId(regionId);
                        if (ObjectUtils.isEmpty(watsonsRegionDTO)) {
                            throw new CommonException("??????????????????id?????????????????????!");
                        }
                        String levelPath = watsonsRegionDTO.getLevelPath();
                        String[] splitRes = levelPath.split("\\.");
                        StringBuffer sb = new StringBuffer();
                        for (int i = 0; i < splitRes.length; i++) {
                            WatsonsRegionDTO res = allocationInfoRepository.selectRegionInfoByRegionCode(splitRes[i]);
                            if (ObjectUtils.isEmpty(res)) {
                                throw new CommonException("??????????????????code?????????????????????!");
                            }
                            sb.append(res.getRegionName());
                        }
                        String regionRes = sb.toString();
                        watsonsAddressDTO.setAddressRegion(regionRes);
                    }
                    return watsonsAddressDTOS;
                } else {
                    AddressDTO infoDTO = allocationInfoRepository.selectIdByCode(organizationId, watsonsOrganizationCode);
                    logger.error(infoDTO.getInvOrganizationCode() + "-" + infoDTO.getInvOrganizationName() + "????????????????????????????????????????????????????????????!");
                    throw new CommonException(infoDTO.getInvOrganizationCode() + "-" + infoDTO.getInvOrganizationName() + "????????????????????????????????????????????????????????????!");
                }
            } else {
                logger.warn("???????????????code????????????????????????id!");
                throw new CommonException("???????????????????????????????????????????????????????????????????????????????????????????????????id!????????????" + watsonsOrganizationCode);
            }
        }
        return null;
    }

    @Override
    public String checkAddressValidate(Long organizationId, List<WatsonsShoppingCartDTO> watsonsShoppingCartDTOS) {
        //????????????  ??????????????????????????????????????????
        List<AllocationInfo> allocationInfos = new ArrayList<>();
        for (WatsonsShoppingCartDTO watsonsShoppingCartDTO : watsonsShoppingCartDTOS) {
            for (AllocationInfo allocationInfo : watsonsShoppingCartDTO.getAllocationInfoList()) {
                allocationInfo.setFromWhichShoppingCart(watsonsShoppingCartDTO.getProductName());
                allocationInfos.add(allocationInfo);
            }
        }

        Map<Long, List<AllocationInfo>> collectRes = allocationInfos.stream().collect(Collectors.groupingBy(AllocationInfo::getCostShopId));
        for (Map.Entry<Long, List<AllocationInfo>> longListEntry : collectRes.entrySet()) {
            List<AllocationInfo> value = longListEntry.getValue();
            String address4Check = value.get(0).getAddressRegion() + value.get(0).getFullAddress();
            for (AllocationInfo allocationInfo : value) {
                if (!((allocationInfo.getAddressRegion() + allocationInfo.getFullAddress()).equals(address4Check))) {
                    throw new CommonException(
                            allocationInfo.getFromWhichShoppingCart() + allocationInfo.getCostShopCode() + allocationInfo.getCostShopName() + "????????????????????????????????????!");
                }
            }
        }
        return null;
    }

    /**
     * ???????????????
     *
     * @param shoppingCart ?????????
     * @param productPool  ?????????
     */
    private void handLadderPrice(ShoppingCart shoppingCart, ProductPool productPool) {
        List<ProductPoolLadder> productPoolLadders = productPool.getLadderPriceList().stream().map(ProductPoolLadder::new).sorted(Comparator.comparing(ProductPoolLadder::getLadderFrom)).collect(Collectors.toList());
        // ???????????????
        boolean hasFlag = true;
        for (ProductPoolLadder productPoolLadder : productPoolLadders) {
            BigDecimal quantity = shoppingCart.getQuantity();
            if (productPoolLadder.getLadderFrom().compareTo(quantity) <= 0 && (ObjectUtils.isEmpty(productPoolLadder.getLadderTo()) || productPoolLadder.getLadderTo().compareTo(quantity) > 0)) {
                shoppingCart.setLatestPrice(productPoolLadder.getTaxPrice());
                shoppingCart.setUnitPrice(productPoolLadder.getTaxPrice());
                hasFlag = false;
                break;
            }
        }
        if (hasFlag) {
            //????????????????????????????????????
            ProductPoolLadder productPoolLadder = productPoolLadders.get(productPoolLadders.size() - 1);
            shoppingCart.setLatestPrice(productPoolLadder.getTaxPrice());
            shoppingCart.setUnitPrice(productPoolLadder.getTaxPrice());
        }
    }
    @Override
    public List<WatsonsPreRequestOrderDTO> watsonsPreRequestOrderView(Long tenantId, List<WatsonsShoppingCartDTO> watsonsShoppingCartDTOList) {
        //??????????????????????????????????????????????????????????????????/??????/??????????????????,??????????????????+??????????????????????????????
        checkAddressRegionAndFullAddress(watsonsShoppingCartDTOList);
        //???????????????????????????
        checkCustomizedProductInfoForWatsons(tenantId, watsonsShoppingCartDTOList);
        //?????????????????????????????????list??????????????????list
        List<ShoppingCartDTO> re = new ArrayList<>();
        //?????????????????????categoryId
        SkuCenterQueryDTO skuCenterQueryDTO = new SkuCenterQueryDTO();
        skuCenterQueryDTO.setCategoryCodeList(Collections.singletonList(BUSINESS_CARD_CATEGORY_CODE));
        SkuCenterResultDTO<List<Category>> response = ResponseUtils.getResponse(smpcRemoteService.queryCategoryInfo(tenantId, skuCenterQueryDTO), new TypeReference<SkuCenterResultDTO<List<Category>>>() {
        });
        List<Category> categories = response.isSuccess() ? response.getResult() : null;
        Long businessCardCategoryId = -1L;
        if (!CollectionUtils.isEmpty(categories)) {
//            businessCardCategoryId = businessCardCategory.getId();
            businessCardCategoryId = categories.get(0).getCategoryId();
        }
        // ??????????????????
        // ?????????????????????????????????????????????????????????????????????
//        watsonsShoppingCartDTOList????????????shoppingCartDTOList??????????????????
        List<ShoppingCartDTO> shoppingCartDTOList = new ArrayList<>();
        for (WatsonsShoppingCartDTO watsonsShoppingCartDTO : watsonsShoppingCartDTOList) {
            ShoppingCartDTO shoppingCartDTO4Transfer = new ShoppingCartDTO();
            BeanUtils.copyProperties(watsonsShoppingCartDTO, shoppingCartDTO4Transfer);
            shoppingCartDTOList.add(shoppingCartDTO4Transfer);
        }
        Map<String, Map<Long, PriceResultDTO>> priceResultDTOMap = queryPriceResult(shoppingCartDTOList);
        Iterator iterator = watsonsShoppingCartDTOList.iterator();
        while (iterator.hasNext()) {
            ShoppingCartDTO shoppingCartDTO = (ShoppingCartDTO) iterator.next();
            Map<Long, PriceResultDTO> resultDTOMap = priceResultDTOMap.get(shoppingCartDTO.skuRegionGroupKey());
            if (ObjectUtils.isEmpty(resultDTOMap)) {
                throw new CommonException(ScecConstants.ErrorCode.PRODUCT_CANNOT_SELL);
            }
            PriceResultDTO priceResultDTO = resultDTOMap.get(shoppingCartDTO.getProductId());
            if (ObjectUtils.isEmpty(priceResultDTO) || !BaseConstants.Flag.YES.equals(priceResultDTO.getSaleEnable())) {
                throw new CommonException(ScecConstants.ErrorCode.PRODUCT_CANNOT_SELL);
            }
            //?????????????????????????????????
            BigDecimal number = shoppingCartDTO.getQuantity().divide(shoppingCartDTO.getMinPackageQuantity(), 10, BigDecimal.ROUND_HALF_UP);
            if (new BigDecimal(number.intValue()).compareTo(number) != 0) {
                throw new CommonException("????????????????????????" + shoppingCartDTO.getMinPackageQuantity() + "????????????");
            }
            if (businessCardCategoryId.equals(shoppingCartDTO.getCid())) {
                shoppingCartDTO.setBusinessCardFlag(1);
            }
            shoppingCartDTO.setAgreementLineId(priceResultDTO.getPurAgreementLineId());
            if (!ObjectUtils.isEmpty(shoppingCartDTO.getCatalogId())) {
                //????????????????????????
                BigDecimal priceLimit = ResponseUtils.getResponse(smpcRemoteService.queryPriceLimit(tenantId, new org.srm.mall.common.feign.dto.product.CatalogPriceLimit(shoppingCartDTO.getTenantId(), shoppingCartDTO.getOwnerId(), shoppingCartDTO.getProductId(), shoppingCartDTO.getCatalogId(), null)), BigDecimal.class);
//                BigDecimal priceLimit = catalogPriceLimitService.priceLimit(new CatalogPriceLimit(shoppingCartDTO.getTenantId(), shoppingCartDTO.getOwnerId(), shoppingCartDTO.getProductId(), shoppingCartDTO.getCatalogId(), null));
                if (Objects.nonNull(priceLimit) && priceLimit.compareTo(shoppingCartDTO.getLatestPrice()) == -1) {
                    //????????????????????????????????????
                    //?????????????????????2???????????????0??????????????????10???
                    NumberFormat nf = NumberFormat.getInstance();
                    nf.setMinimumFractionDigits(2);
                    nf.setMaximumFractionDigits(10);
                    nf.setGroupingUsed(false);
                    throw new CommonException(ScecConstants.ErrorCode.CATALOG_OVER_LIMIT_PRICE, nf.format(priceLimit));
                }
            } else {
                throw new CommonException(ScecConstants.ErrorCode.PRODUCT_CATALOG_NOT_EXISTS);
            }
            if (CollectionUtils.isNotEmpty(shoppingCartDTO.getSeSkuList())) {
                re.addAll(shoppingCartDTO.getSeSkuList());
            }
            if (!ObjectUtils.isEmpty(shoppingCartDTO.getItemId())) {
                shoppingCartDTO.setHasItemFlag(BaseConstants.Flag.YES);
            } else {
                shoppingCartDTO.setHasItemFlag(BaseConstants.Flag.NO);
                shoppingCartDTO.setWarehousing(BaseConstants.Flag.YES);
            }
            checkPrice(shoppingCartDTO, priceResultDTO);
            // ?????????????????????????????????
            convertParam(shoppingCartDTO, priceResultDTO);
        }
        //??????????????????
        //??????????????????
        validateShppingAddress(shoppingCartDTOList);
        //?????????????????????????????????????????????????????????
        validateShoppingExistCar(shoppingCartDTOList);
        if (CollectionUtils.isNotEmpty(re)) {
            validateShoppingExistCar(re);
        }
        boolean hideSupplier = mallOrderCenterService.checkHideField(tenantId, shoppingCartDTOList.get(0).getCompanyId(), ScecConstants.HideField.SUPPLIER);
        if (CollectionUtils.isNotEmpty(shoppingCartDTOList)) {
            List<WatsonsPreRequestOrderDTO> watsonsPreRequestOrderDTOList = new ArrayList<>();
            //??????????????????????????????????????????????????????????????????
            refreshInvOrganizationAndAddress(watsonsShoppingCartDTOList);
            PurReqMergeRule purReqMergeRule = PurReqMergeRule.getDefaultMergeRule();
            getPostageInfo(tenantId, watsonsShoppingCartDTOList);
            splitShoppingCartByCostConfig(watsonsShoppingCartDTOList);
            //??????shoppingCartDTOList?????????   ?????????????????????????????????????????????????????????????????????
            Map<String, List<WatsonsShoppingCartDTO>> result = watsonsShoppingCartDTOList.stream().collect(Collectors.groupingBy(s -> s.groupKey(purReqMergeRule)));
            checkNeedToSplitByFreightType(watsonsShoppingCartDTOList, purReqMergeRule);
            result = watsonsGroupPurchaseRequest(tenantId, purReqMergeRule, result);
            //??????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????????   ?????????????????????????????????
            recursionSplitShoppingCart(result);
            //??????????????????????????????s
            int distinguishId = 0;
            for (Map.Entry<String, List<WatsonsShoppingCartDTO>> entry : result.entrySet()) {
                WatsonsPreRequestOrderDTO watsonsPreRequestOrderDTO = new WatsonsPreRequestOrderDTO();
                watsonsPreRequestOrderDTO.setKeyForView(entry.getKey());
                List<WatsonsShoppingCartDTO> watsonsShoppingCartDTOList4Trans = entry.getValue();
                List<ShoppingCartDTO> shoppingCartDTO4Freight = new ArrayList<>();

                for (WatsonsShoppingCartDTO watsonsShoppingCartDTO : watsonsShoppingCartDTOList4Trans) {
                    ShoppingCartDTO shoppingCartDTO = new ShoppingCartDTO();
                    BeanUtils.copyProperties(watsonsShoppingCartDTO, shoppingCartDTO);
                    shoppingCartDTO4Freight.add(shoppingCartDTO);
                }
                watsonsPreRequestOrderDTO.setShoppingCartDTOList(shoppingCartDTO4Freight);
                watsonsPreRequestOrderDTO.setDistinguishId(++distinguishId);
                watsonsPreRequestOrderDTO.setCount(entry.getValue().stream().map(WatsonsShoppingCartDTO::getQuantity).reduce(BigDecimal.ZERO, BigDecimal::add));
                WatsonsShoppingCartDTO watsonsShoppingCartDTO = entry.getValue().get(0);
                watsonsPreRequestOrderDTO.setAddress(watsonsShoppingCartDTO.getAddress());
                boolean checkHideSupplier = hideSupplier && ScecConstants.SourceType.CATALOGUE.equals(watsonsShoppingCartDTO.getProductSource());
                watsonsPreRequestOrderDTO.setSupplierCompanyName(checkHideSupplier ? ScecConstants.HideField.HIDE_SUPPLIER_NAME_CODE : watsonsShoppingCartDTO.getSupplierCompanyName());
                watsonsPreRequestOrderDTO.setOuName(watsonsShoppingCartDTO.getOuName());
                watsonsPreRequestOrderDTO.setPurOrganizationName(watsonsShoppingCartDTO.getPurOrganizationName());
                watsonsPreRequestOrderDTO.setPurOrganizationId(watsonsShoppingCartDTO.getPurOrganizationId());
                watsonsPreRequestOrderDTO.setOrganizationName(watsonsShoppingCartDTO.getOrganizationName());
                watsonsPreRequestOrderDTO.setReceiverEmailAddress(watsonsShoppingCartDTO.getContactEmail());
                watsonsPreRequestOrderDTO.setReceiverTelNum(watsonsShoppingCartDTO.getContactMobile());
                watsonsPreRequestOrderDTO.setProxySupplierCompanyId(watsonsShoppingCartDTO.getProxySupplierCompanyId());
                watsonsPreRequestOrderDTO.setProxySupplierCompanyName(watsonsShoppingCartDTO.getProxySupplierCompanyName());
                watsonsPreRequestOrderDTO.setShowSupplierCompanyId(watsonsShoppingCartDTO.getShowSupplierCompanyId());
                watsonsPreRequestOrderDTO.setShowSupplierName(checkHideSupplier ? ScecConstants.HideField.HIDE_SUPPLIER_NAME_CODE : watsonsShoppingCartDTO.getShowSupplierName());
                String addressRegion = watsonsShoppingCartDTOList4Trans.get(0).getAllocationInfoList().get(0).getAddressRegion();
                String fullAddress = watsonsShoppingCartDTOList4Trans.get(0).getAllocationInfoList().get(0).getFullAddress();
                //??????????????????????????????????????????????????????+?????????????????????  ?????????????????????????????????
                watsonsPreRequestOrderDTO.setReceiverAddress(addressRegion + fullAddress);
                watsonsPreRequestOrderDTO.setWatsonsShoppingCartDTOList(watsonsShoppingCartDTOList4Trans);
                // ????????????(????????????)
                BigDecimal price = entry.getValue().stream().map(WatsonsShoppingCartDTO::getTotalPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
                validateMinPurchaseAmount(tenantId, watsonsShoppingCartDTO, price, watsonsPreRequestOrderDTO);
                // ??????????????????????????????????????? ???????????????????????????
                BigDecimal freightPrice = price;
                if (ScecConstants.AgreementType.SALE.equals(watsonsShoppingCartDTO.getAgreementType())) {
                    //  ????????????????????? ???????????????????????????????????????
                    freightPrice = entry.getValue().stream().map(WatsonsShoppingCartDTO::getPurTotalPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
                }
                queryFreight(tenantId, entry, watsonsPreRequestOrderDTO, freightPrice);
                //????????????  ??????+??????
                watsonsPreRequestOrderDTO.setPrice(price.add(watsonsPreRequestOrderDTO.getFreight()));
                if (ScecConstants.AgreementType.SALE.equals(watsonsShoppingCartDTO.getAgreementType())) {
                    //  ????????????????????? ???????????????????????????????????????
                    watsonsPreRequestOrderDTO.setPurPrice(freightPrice.add(watsonsPreRequestOrderDTO.getFreight()));
                }
                PaymentInfo paymentInfo = shoppingCartRepository.queryPaymentInfo(watsonsShoppingCartDTO);
                if (null != paymentInfo) {
                    BeanConvertor.convert(paymentInfo, watsonsPreRequestOrderDTO);
                }
                watsonsPreRequestOrderDTO.setPreRequestOrderNumber(UUID.randomUUID().toString());
                watsonsPreRequestOrderDTO.setMobile(watsonsShoppingCartDTO.getMobile());
                CustomUserDetails userDetails = DetailsHelper.getUserDetails();
                watsonsPreRequestOrderDTO.setReceiverContactName(userDetails.getRealName());
                watsonsPreRequestOrderDTO.setStoreNo(watsonsShoppingCartDTOList4Trans.get(0).getAllocationInfoList().get(0).getCostShopCode());
                snapshotUtil.saveSnapshot(AbstractKeyGenerator.getKey(ScecConstants.CacheCode.SERVICE_NAME, ScecConstants.CacheCode.PURCHASE_REQUISITION_PREVIEW, watsonsPreRequestOrderDTO.getPreRequestOrderNumber()), watsonsPreRequestOrderDTO.getPreRequestOrderNumber(), watsonsPreRequestOrderDTO, commonService.queryDelayTime(tenantId), TimeUnit.SECONDS);
                watsonsPreRequestOrderDTOList.add(watsonsPreRequestOrderDTO);
            }
            setCMSInfo(tenantId, watsonsPreRequestOrderDTOList);
            // handleCheck()
            return watsonsPreRequestOrderDTOList;
        }
        return null;
    }
    private void checkCustomizedProductInfoForWatsons(Long tenantId, List<WatsonsShoppingCartDTO> watsonsShoppingCartDTOS) {
        //????????????????????????
        List<Long> productIdList = watsonsShoppingCartDTOS.stream().filter(s -> s.getCustomFlag() != null && s.getCustomFlag() == 1).map(ShoppingCartDTO::getProductId).collect(Collectors.toList());
        if (CollectionUtils.isEmpty(productIdList)) {
            return;
        }
        List<SkuCustomDTO> skuCustomList = productWorkbenchRepository.selectSkuListCustomAttrNoException(tenantId, productIdList);
        Map<Long, SkuCustomDTO> skuCustomDTOMap = skuCustomList.stream().collect(Collectors.toMap(SkuCustomDTO::getSkuId, Function.identity(), (k1, k2) -> k1));
        //??????????????????
        for (WatsonsShoppingCartDTO watsonsShoppingCartDTO : watsonsShoppingCartDTOS) {
            if (watsonsShoppingCartDTO.getCustomFlag() != null && watsonsShoppingCartDTO.getCustomFlag() == 1) {
                SkuCustomDTO skuCustomDTO = skuCustomDTOMap.get(watsonsShoppingCartDTO.getProductId());
                //??????????????????????????????
                watsonsShoppingCartDTO.checkCustomizedProductInfo(skuCustomDTO.getSpuCustomGroupList());
                //????????????????????????????????????
                if ((ObjectUtils.isEmpty(skuCustomDTO) && !CollectionUtils.isEmpty(watsonsShoppingCartDTO.getCustomizedProductLineList()))
                        || !ObjectUtils.isEmpty(skuCustomDTO) && CollectionUtils.isEmpty(watsonsShoppingCartDTO.getCustomizedProductLineList())) {
                    throw new CommonException(ScecConstants.ProductCustomized.ERROR_PRODUCT_CUSTOMIZED_CHANGE);
                }
                for (CustomizedProductLine customizedProductLine : watsonsShoppingCartDTO.getCustomizedProductLineList()) {
                    CustomizedProductCheckDTO customizedProductCheckDTO = customizedProductLine.check(skuCustomDTO.getSpuCustomGroupList());
                    customizedProductLineService.updateCustomizedProductInfo(customizedProductCheckDTO);
                    if (customizedProductCheckDTO.getSuccess() == 0) {
                        throw new CommonException(ScecConstants.ProductCustomized.ERROR_PRODUCT_CUSTOMIZED_CHANGE);
                    }
                }
                calculateCustomizedProductForShoppingCartDTO(watsonsShoppingCartDTO);
            }
        }
    }

    private void checkAddressRegionAndFullAddress(List<WatsonsShoppingCartDTO> watsonsShoppingCartDTOList) {
        List<AllocationInfo> allocationInfos = new ArrayList<>();
        for (WatsonsShoppingCartDTO watsonsShoppingCartDTO : watsonsShoppingCartDTOList) {
            for (AllocationInfo allocationInfo : watsonsShoppingCartDTO.getAllocationInfoList()) {
                allocationInfo.setFromWhichShoppingCart(watsonsShoppingCartDTO.getProductName());
                allocationInfos.add(allocationInfo);
            }
        }
        Map<Long, List<AllocationInfo>> collectRes = allocationInfos.stream().collect(Collectors.groupingBy(AllocationInfo::getCostShopId));
        for (Map.Entry<Long, List<AllocationInfo>> longListEntry : collectRes.entrySet()) {
            List<AllocationInfo> value = longListEntry.getValue();
            String address4Check = value.get(0).getAddressRegion() + value.get(0).getFullAddress();
            for (AllocationInfo allocationInfo : value) {
                if (!((allocationInfo.getAddressRegion() + allocationInfo.getFullAddress()).equals(address4Check))) {
                    throw new CommonException(
                            "??????" + value.get(0).getFromWhichShoppingCart() + "???" + value.get(0).getCostShopCode() + value.get(0).getCostShopName() +
                                    "?????????" + allocationInfo.getFromWhichShoppingCart() + "???" + allocationInfo.getCostShopCode() + allocationInfo.getCostShopName() + "????????????????????????????????????!");
                }
            }
        }
    }

    private void queryFreight(Long tenantId, Map.Entry<String, List<WatsonsShoppingCartDTO>> entry, WatsonsPreRequestOrderDTO watsonsPreRequestOrderDTO, BigDecimal freightPrice) {
        watsonsPreRequestOrderDTO.setFreight(BigDecimal.ZERO);
        watsonsPreRequestOrderDTO.setWithoutTaxFreightPrice(BigDecimal.ZERO);
        if (ScecConstants.ECommercePlatformCode.PLATFORM_JD.equals(entry.getValue().get(0).getProductSource()) || ScecConstants.SourceType.NJD.equals(entry.getValue().get(0).getProductSource())) {
            //??????????????????
            if (freightPrice.compareTo(new BigDecimal(ScecConstants.JDFreightLevel.FREIGHT_FREE)) > -1) {
                //????????????99???????????????
                watsonsPreRequestOrderDTO.setFreight(BigDecimal.ZERO);
            } else if (freightPrice.compareTo(new BigDecimal(ScecConstants.JDFreightLevel.FREIGHT_MIDDLE)) > -1) {
                //?????????50-99??????????????????
                watsonsPreRequestOrderDTO.setFreight(new BigDecimal(ScecConstants.JDFreightLevel.FREIGHT_COST_LOW));
            } else {
                //?????????0-49??????????????????
                watsonsPreRequestOrderDTO.setFreight(new BigDecimal(ScecConstants.JDFreightLevel.FREIGHT_COST_HIGN));
            }
        } else if(ScecConstants.SourceType.CATALOGUE.equals(entry.getValue().get(0).getProductSource())){
            //???????????????
            this.orderFreight(tenantId, watsonsPreRequestOrderDTO);
        }else {
            processWatsonsEcFreight(tenantId, entry.getValue(), watsonsPreRequestOrderDTO);
        }
    }

    private void processWatsonsEcFreight(Long tenantId, List<WatsonsShoppingCartDTO> shoppingCartDTOS, WatsonsPreRequestOrderDTO preRequestOrderDTO) {
        logger.info("using yb freight");
        //?????????????????????????????????????????????????????????
        List<EcPlatform> ecPlatforms = ecPlatformRepository.selectByCondition(Condition.builder(EcPlatform.class).andWhere(Sqls.custom()
                .andEqualTo(EcPlatform.FIELD_EC_PLATFORM_CODE, shoppingCartDTOS.get(0).getProductSource())).build());
        if(ScecConstants.Flags.ENABLE_FLAG == (ecPlatforms.get(0).getFreightQueryEnabled())){
            String countyId = null;
            String provinceId = null;
            String cityId = null;
            String regionId = null;
            String address = null;
            List<EcSkuInfo> ecSkuInfos = new ArrayList<>();
            Assert.notNull(shoppingCartDTOS.get(0).getRegionLevelPath(),"????????????????????????????????????????????????????????????!");
            String[] split = shoppingCartDTOS.get(0).getRegionLevelPath().split("\\.");
            if(split.length<3){
                logger.error("???????????????????????????????????????????????????????????????????????????!,???????????????{}", JSONObject.toJSON(shoppingCartDTOS));
                throw new CommonException("???????????????????????????????????????????????????????????????????????????!,???????????????{}", JSONObject.toJSON(shoppingCartDTOS));
            }else{
                List<MallRegion> province = mallRegionRepository.selectByCondition(Condition.builder(MallRegion.class).andWhere(Sqls.custom()
                        .andEqualTo(MallRegion.FIELD_ENABLED_FLAG, 1)
                        .andEqualTo(MallRegion.FIELD_REGION_CODE, split[0])).build());
                provinceId = province.get(0).getRegionCode();
                List<MallRegion> city = mallRegionRepository.selectByCondition(Condition.builder(MallRegion.class).andWhere(Sqls.custom()
                        .andEqualTo(MallRegion.FIELD_ENABLED_FLAG, 1)
                        .andEqualTo(MallRegion.FIELD_REGION_CODE, split[1])).build());
                cityId = city.get(0).getRegionCode();
                List<MallRegion> region = mallRegionRepository.selectByCondition(Condition.builder(MallRegion.class).andWhere(Sqls.custom()
                        .andEqualTo(MallRegion.FIELD_ENABLED_FLAG, 1)
                        .andEqualTo(MallRegion.FIELD_REGION_CODE, split[2])).build());
                regionId =  region.get(0).getRegionCode();
                address += province.get(0).getRegionName()+city.get(0).getRegionName()+region.get(0).getRegionName();
                if(split.length > 3){
                    List<MallRegion> county = mallRegionRepository.selectByCondition(Condition.builder(MallRegion.class).andWhere(Sqls.custom()
                            .andEqualTo(MallRegion.FIELD_ENABLED_FLAG, 1)
                            .andEqualTo(MallRegion.FIELD_REGION_CODE, split[3])).build());
                    countyId =  county.get(0).getRegionCode();
                    address+=county.get(0).getRegionName();
                }
            }
            if (!ObjectUtils.isEmpty(provinceId) && !ObjectUtils.isEmpty(cityId) && !ObjectUtils.isEmpty(regionId) && !ObjectUtils.isEmpty(address)) {
                logger.info("the full address is {}",JSONObject.toJSON(address));
                FreightPriceDto freightPriceDto = new FreightPriceDto();
                freightPriceDto.setSupplierCode(preRequestOrderDTO.getShoppingCartDTOList().get(0).getProductSource());
                freightPriceDto.setTenantId(tenantId);
                freightPriceDto.setProvinceId(provinceId);
                freightPriceDto.setCityId(cityId);
                freightPriceDto.setCountyId(regionId);
                freightPriceDto.setAddress(address);
                List<Long> skuIds = new ArrayList<>();
                shoppingCartDTOS.forEach(c->{skuIds.add(c.getProductId());});
                List<SkuBaseInfoDTO> skuBaseInfoDTOS = productWorkbenchRepository.querySkuBaseInfoList(tenantId, skuIds);
                for (ShoppingCartDTO cartDTO : shoppingCartDTOS) {
                    skuBaseInfoDTOS.forEach(skuBaseInfoDTO -> {
                        if(skuBaseInfoDTO.getSkuId().equals(cartDTO.getProductId())){
                            EcSkuInfo ecSkuInfo = new EcSkuInfo();
                            ecSkuInfo.setSkuId(skuBaseInfoDTO.getThirdSkuCode());
                            ecSkuInfo.setSkuNum(cartDTO.getQuantity().intValue());
                            ecSkuInfos.add(ecSkuInfo);
                        }
                    });
                }
                freightPriceDto.setSkuInfos(ecSkuInfos);
                logger.info("the ec freigth param is {}", JSONObject.toJSON(freightPriceDto));
                ResponseEntity<String> stringResponseEntity = sifgOrderRemoteService.getFreightPrice(freightPriceDto);
                if(ResponseUtils.isFailed(stringResponseEntity)){
                    logger.error("??????????????????,?????????{}",JSON.toJSONString(stringResponseEntity));
                    throw new CommonException("??????????????????,"+JSON.toJSONString(stringResponseEntity));
                }else {
                    ECResult<String> response = ResponseUtils.getResponse(stringResponseEntity, new TypeReference<ECResult<String>>() {
                    });
                    logger.info("????????????????????????,?????????"+JSONObject.toJSON(response));
                    BigDecimal includeTaxFreight = BigDecimal.valueOf(Long.parseLong(response.getResult()));
                    preRequestOrderDTO.setFreight(includeTaxFreight);
                    BigDecimal withoutTaxFreightPrice = includeTaxFreight.subtract(includeTaxFreight.multiply(new BigDecimal("0.06")));
                    preRequestOrderDTO.setWithoutTaxFreightPrice(withoutTaxFreightPrice);
                }
            }else {
                logger.error("??????????????????????????????????????????????????????????????????????????????! ?????????{}",JSONObject.toJSON(shoppingCartDTOS));
                throw new CommonException("??????????????????????????????????????????????????????????????????????????????!");
            }
        }
    }

    private void setCMSInfo(Long tenantId, List<WatsonsPreRequestOrderDTO> watsonsPreRequestOrderDTOList) {
        //        ??????cms???????????????
        watsonsPreRequestOrderDTOList.stream().forEach(watsonsPreRequestOrderDTO -> {
            for (WatsonsShoppingCartDTO watsonsShoppingCartDTO : watsonsPreRequestOrderDTO.getWatsonsShoppingCartDTOList()) {
                logger.info("??????????????????????????????cms??????");
                ResponseEntity<String> stringResponseEntity = watsonsSagmRemoteService.queryAgreementLineById(tenantId, watsonsShoppingCartDTO.getAgreementLineId());
                if (ResponseUtils.isFailed(stringResponseEntity)) {
                    logger.error("????????????????????????cms???????????????!");
                } else {
                    AgreementLine agreementLine = ResponseUtils.getResponse(stringResponseEntity, new TypeReference<AgreementLine>() {
                    });
                    //attributeVarchar1???cms?????????
                    if (ObjectUtils.isEmpty(agreementLine)) {
                        logger.error(watsonsShoppingCartDTO.getProductName() + "????????????????????????????????????!");
                    }
                    if (!ObjectUtils.isEmpty(agreementLine) && ObjectUtils.isEmpty(agreementLine.getAttributeVarchar1())) {
                        logger.error(watsonsShoppingCartDTO.getProductName() + "???????????????????????????CMS?????????!");
                    }
                    if (!ObjectUtils.isEmpty(agreementLine) && !ObjectUtils.isEmpty(agreementLine.getAttributeVarchar1())) {
                        watsonsShoppingCartDTO.setCmsNumber(agreementLine.getAttributeVarchar1());
                    }
                    if (!ObjectUtils.isEmpty(agreementLine) && !ObjectUtils.isEmpty(agreementLine.getAttributeVarchar2())) {
                        //????????????
                        watsonsShoppingCartDTO.setAttributeVarchar3(agreementLine.getAttributeVarchar2());
                    }
                }
            }
        });
    }
    private void refreshInvOrganizationAndAddress(List<WatsonsShoppingCartDTO> watsonsShoppingCartDTOList) {
        //??????addressId???ouid??????????????? ??????????????????
        for (WatsonsShoppingCartDTO watsonsShoppingCartDTO : watsonsShoppingCartDTOList) {
            watsonsShoppingCartDTO.setAddressId(watsonsShoppingCartDTOList.get(0).getAddressId());
            watsonsShoppingCartDTO.setInvOrganizationId(watsonsShoppingCartDTOList.get(0).getInvOrganizationId());
            watsonsShoppingCartDTO.setOuId(watsonsShoppingCartDTOList.get(0).getOuId());
        }
    }

    private void checkNeedToSplitByFreightType(List<WatsonsShoppingCartDTO> shoppingCartDTOList, PurReqMergeRule purReqMergeRule) {
        for (ShoppingCartDTO shoppingCartDTO : shoppingCartDTOList) {
            logger.info("the postage info for each shoppingcart is {}", JSONObject.toJSON(shoppingCartDTO.getFreightPricingMethod() + "-" + shoppingCartDTO.getVolumeUnitPrice()));
            if (!ObjectUtils.isEmpty(shoppingCartDTO.getVolumeUnitPrice()) && ScecConstants.CacheCode.ACTUAL_CALCULATION.equals(shoppingCartDTO.getFreightPricingMethod())) {
                purReqMergeRule.setFreightType(BaseConstants.Flag.YES);
                break;
            } else {
                purReqMergeRule.setFreightType(BaseConstants.Flag.NO);
            }
        }
    }

    /**
     * ??????????????????????????????????????????????????????
     *
     * @param tenantId
     * @param shoppingCartDTOList
     */
    private void getPostageInfo(Long tenantId, List<WatsonsShoppingCartDTO> shoppingCartDTOList) {
        //??????????????????
        Map<Long, List<ShoppingCartDTO>> cartByAddressId = shoppingCartDTOList.stream().collect(Collectors.groupingBy(ShoppingCartDTO::getAddressId));
        List<PostageCalculateDTO> postageCalculateDTOS = buildPostageInfoParamsForShoppingCart(shoppingCartDTOList, cartByAddressId);
        logger.info("query freight dto is {}", JSONObject.toJSON(postageCalculateDTOS));
        ResponseEntity<String> queryPostageInfoRes = sagmRemoteService.queryPostageInfo(tenantId, postageCalculateDTOS);
        if (ResponseUtils.isFailed(queryPostageInfoRes)) {
            throw new CommonException("??????????????????: ???????????????????????????????????????????????????");
        }
        List<PostageCalculateDTO> queryPostageResult = ResponseUtils.getResponse(queryPostageInfoRes, new TypeReference<List<PostageCalculateDTO>>() {
        });
        logger.info("query Postage info result is {}", JSONObject.toJSON(queryPostageResult));
        //????????????????????????????????????
        for (PostageCalculateDTO postageCalculateDTO : queryPostageResult) {
            for (Map.Entry<Long, List<ShoppingCartDTO>> entry : cartByAddressId.entrySet()) {
                if (entry.getKey().equals(postageCalculateDTO.getAddressId())) {
                    entry.getValue().forEach(shoppingCartDTO -> {
                        postageCalculateDTO.getPostageCalculateLineDTOS().forEach(postageCalculateLineDTO -> {
                            if (shoppingCartDTO.getAgreementLineId().equals(postageCalculateLineDTO.getAgreementLineId())) {
                                if (!ObjectUtils.isEmpty(postageCalculateLineDTO.getPostage())) {
                                    //??????????????????
                                    shoppingCartDTO.setFreightPricingMethod(postageCalculateLineDTO.getPostage().getPricingMethod());
                                    //????????????
                                    shoppingCartDTO.setVolumeUnitPrice(postageCalculateLineDTO.getPostage().getPostageLine().getVolumeUnitPrice());
                                    //????????????
                                    shoppingCartDTO.setFreightTaxId(postageCalculateLineDTO.getPostage().getTaxId());
                                    shoppingCartDTO.setFreightTaxCode(postageCalculateLineDTO.getPostage().getTaxCode());
                                    shoppingCartDTO.setFreightTaxRate(new BigDecimal(postageCalculateLineDTO.getPostage().getTaxRate()));
                                    //????????????
                                    shoppingCartDTO.setFreightItemId(postageCalculateLineDTO.getPostage().getItemId());
                                    shoppingCartDTO.setFreightItemCode(postageCalculateLineDTO.getPostage().getItemCode());
                                    shoppingCartDTO.setFreightItemName(postageCalculateLineDTO.getPostage().getItemName());
                                } else {
                                    logger.info("?????????" + shoppingCartDTO.getProductId() + "?????????????????????????????????");
                                }
                            }
                        });
                    });
                }
            }
        }
        logger.info("the shopping carts after built postage {}", JSON.toJSON(shoppingCartDTOList));
    }
    private  List<PostageCalculateDTO>  buildPostageInfoParamsForShoppingCart(List<WatsonsShoppingCartDTO> shoppingCartDTOList, Map<Long, List<ShoppingCartDTO>> cartByAddressId) {
        List<PostageCalculateDTO> postageCalculateDTOS = new ArrayList<>();
        for (Map.Entry<Long, List<ShoppingCartDTO>> cart : cartByAddressId.entrySet()) {
            List<PostageCalculateLineDTO> postageCalculateLineDTOS = new ArrayList<>();
            List<ShoppingCartDTO> shoppingCartDTOS = cart.getValue();
            PostageCalculateDTO postageCalculateDTO = new PostageCalculateDTO();
            //regionId????????????
            postageCalculateDTO.setAddressId(shoppingCartDTOS.get(0).getAddressId());
            shoppingCartDTOS.forEach(s -> {
                PostageCalculateLineDTO postageCalculateLineDTO = new PostageCalculateLineDTO();
                postageCalculateLineDTO.setAgreementLineId(s.getAgreementLineId());
                postageCalculateLineDTO.setProductSource(s.getProductSource());
                postageCalculateLineDTO.setPurPrice(s.getPurTotalPrice());
                postageCalculateLineDTO.setPrice(s.getTotalPrice());
                postageCalculateLineDTO.setQuantity(s.getQuantity());
                postageCalculateLineDTO.setAgreementType(s.getAgreementType());
                postageCalculateLineDTO.setCartId(s.getCartId());
                postageCalculateLineDTOS.add(postageCalculateLineDTO);
            });
            postageCalculateDTO.setPostageCalculateLineDTOS(postageCalculateLineDTOS);
            postageCalculateDTOS.add(postageCalculateDTO);
        }
        return postageCalculateDTOS;
    }

    /**
     * ??????????????????
     */
    private void orderFreight(Long tenantId,WatsonsPreRequestOrderDTO watsonsPreRequestOrderDTO){
        BigDecimal withoutTaxFreightPrice = BigDecimal.ZERO;
        List<PostageCalculateDTO> postageCalculateDTOS = buildPostageInfoParamsForPreReq(watsonsPreRequestOrderDTO);
        ResponseEntity<String> calculatePostageRes = sagmRemoteService.freightCalculateNew(tenantId, postageCalculateDTOS);
        if (ResponseUtils.isFailed(calculatePostageRes)) {
            throw new CommonException("??????????????????: ????????????????????????");
        }
        List<PostageCalculateDTO> calculatePostage = ResponseUtils.getResponse(calculatePostageRes, new TypeReference<List<PostageCalculateDTO>>() {
        });
        logger.info("calculate freight result is {}", JSONObject.toJSON(calculatePostage));
        watsonsPreRequestOrderDTO.setFreight(calculatePostage.get(0).getFreightPrice());
        logger.info("calculate without tax freight result is {}", JSONObject.toJSON(calculatePostage.get(0).getWithoutTaxFreightPrice()));
        withoutTaxFreightPrice = calculatePostage.get(0).getWithoutTaxFreightPrice();
        if(ObjectUtils.isEmpty(calculatePostage.get(0).getWithoutTaxFreightPrice())){
            withoutTaxFreightPrice = BigDecimal.ZERO;
        }
        watsonsPreRequestOrderDTO.setWithoutTaxFreightPrice(withoutTaxFreightPrice);
    }

    private List<PostageCalculateDTO> buildPostageInfoParamsForPreReq(WatsonsPreRequestOrderDTO preRequestOrderDTO) {
        List<PostageCalculateDTO> postageCalculateDTOS = new ArrayList<>();
        List<PostageCalculateLineDTO> postageCalculateLineDTOS = new ArrayList<>();
        PostageCalculateDTO postageCalculateDTO = new PostageCalculateDTO();
        Long lastRegionId = preRequestOrderDTO.getWatsonsShoppingCartDTOList().get(0).getAllocationInfoList().get(0).getLastRegionId();
        processSecondRegionIdForWatsons(postageCalculateDTO, lastRegionId);
        preRequestOrderDTO.getShoppingCartDTOList().forEach(s -> {
            PostageCalculateLineDTO postageCalculateLineDTO = new PostageCalculateLineDTO();
            postageCalculateLineDTO.setAgreementLineId(s.getAgreementLineId());
            postageCalculateLineDTO.setProductSource(s.getProductSource());
            postageCalculateLineDTO.setPurPrice(s.getPurTotalPrice());
            postageCalculateLineDTO.setPrice(s.getTotalPrice());
            postageCalculateLineDTO.setQuantity(s.getQuantity());
            postageCalculateLineDTO.setAgreementType(s.getAgreementType());
            postageCalculateLineDTO.setCartId(s.getCartId());
            postageCalculateLineDTOS.add(postageCalculateLineDTO);
        });
        postageCalculateDTO.setPostageCalculateLineDTOS(postageCalculateLineDTOS);
        postageCalculateDTOS.add(postageCalculateDTO);
        return postageCalculateDTOS;
    }

    private void processSecondRegionIdForWatsons(PostageCalculateDTO postageCalculateDTO, Long lastRegionId) {
        logger.info("the last region id is" + lastRegionId);
        List<MallRegion> region = mallRegionRepository.selectByCondition(Condition.builder(MallRegion.class).andWhere(Sqls.custom()
                .andEqualTo(MallRegion.FIELD_ENABLED_FLAG, 1)
                .andEqualTo(MallRegion.FIELD_REGION_ID, lastRegionId)).build());
        MallRegion param = region.get(0);
        if (param.getLevelPath().split("\\.").length < 2) {
            throw new CommonException("???????????????????????????????????????????????????????????????????????????!");
        } else if (param.getLevelPath().split("\\.").length == 2) {
            logger.info("the second region id is " + param.getRegionId());
            postageCalculateDTO.setRegionId(param.getRegionId());
        } else {
            while (param.getLevelPath().split("\\.").length > 2) {
                List<MallRegion> temp = mallRegionRepository.selectByCondition(Condition.builder(MallRegion.class).andWhere(Sqls.custom()
                        .andEqualTo(MallRegion.FIELD_ENABLED_FLAG, 1)
                        .andEqualTo(MallRegion.FIELD_REGION_ID, param.getRegionId())).build());
                List<MallRegion> temp_2 = mallRegionRepository.selectByCondition(Condition.builder(MallRegion.class).andWhere(Sqls.custom()
                        .andEqualTo(MallRegion.FIELD_ENABLED_FLAG, 1)
                        .andEqualTo(MallRegion.FIELD_REGION_CODE, temp.get(0).getParentRegionCode())).build());
                param = temp_2.get(0);
            }
            logger.info("the second region id is " + param.getRegionId());
            postageCalculateDTO.setRegionId(param.getRegionId());
        }
    }

    private void splitShoppingCartByCostConfig(List<WatsonsShoppingCartDTO> watsonsShoppingCartDTOList) {
        //??????????????????????????????????????????  ?????????????????????????????????????????? ??????newShoppingCart????????????????????????????????????
        //??????pre-req???????????????????????????
        List<WatsonsShoppingCartDTO> splitCosttInfoList = new ArrayList<>();
        Iterator<WatsonsShoppingCartDTO> it = watsonsShoppingCartDTOList.iterator();
        //?????????????????????????????? ????????????????????????????????? ??????????????????????????????
        while (it.hasNext()) {
            WatsonsShoppingCartDTO watsonsShoppingCartDTO = it.next();
            List<AllocationInfo> allocationInfoList = watsonsShoppingCartDTO.getAllocationInfoList();
            if (!CollectionUtils.isEmpty(allocationInfoList) && allocationInfoList.size() > 1) {
                for (int i = 0; i < allocationInfoList.size(); i++) {
                    WatsonsShoppingCartDTO newWatsonsShoppingCartDTO = new WatsonsShoppingCartDTO();
                    BeanUtils.copyProperties(watsonsShoppingCartDTO, newWatsonsShoppingCartDTO);
                    newWatsonsShoppingCartDTO.setQuantity(new BigDecimal(allocationInfoList.get(i).getQuantity()));
                    newWatsonsShoppingCartDTO.setAllocationInfoList(Collections.singletonList(allocationInfoList.get(i)));
                    newWatsonsShoppingCartDTO.setTotalPrice(ObjectUtils.isEmpty(newWatsonsShoppingCartDTO.getLatestPrice()) ? BigDecimal.ZERO : newWatsonsShoppingCartDTO.getLatestPrice().multiply(newWatsonsShoppingCartDTO.getQuantity()));
                    //?????????????????????
                    if (newWatsonsShoppingCartDTO.getCustomFlag() != null && newWatsonsShoppingCartDTO.getCustomFlag() == 1 && !CollectionUtils.isEmpty(newWatsonsShoppingCartDTO.getCustomizedProductLineList())){
                        //??????????????????????????????
                        CustomizedProductLine check = newWatsonsShoppingCartDTO.getCustomizedProductLineList().get(0);
                        if (check.getShipperFlag() == 1){
                            CustomizedProductLine customizedProductLine = newWatsonsShoppingCartDTO.getCustomizedProductLineList().get(i);
                            customizedProductLine.setLatestPrice(newWatsonsShoppingCartDTO.getLatestPrice());
                            allocationInfoService.calculateForCpLine(customizedProductLine);
                            newWatsonsShoppingCartDTO.setTotalPrice(ObjectUtils.isEmpty(customizedProductLine.getCpAmount()) ? BigDecimal.ZERO : customizedProductLine.getCpAmount());
                            newWatsonsShoppingCartDTO.setCustomizedProductLineList(Collections.singletonList(customizedProductLine));
                        } else {
                            //?????????????????????
                            newWatsonsShoppingCartDTO.setCustomizedProductLineList( i < newWatsonsShoppingCartDTO.getCustomizedProductLineList().size() ? Collections.singletonList(newWatsonsShoppingCartDTO.getCustomizedProductLineList().get(i)) : new ArrayList<>());
                        }
                    }
                    splitCosttInfoList.add(newWatsonsShoppingCartDTO);
                }
                it.remove();
            }
        }
        watsonsShoppingCartDTOList.addAll(splitCosttInfoList);
    }

    private Map<String, Map<Long, PriceResultDTO>> queryPriceResult(List<ShoppingCartDTO> shoppingCartDTOList) {
        Map<String, List<ShoppingCartDTO>> skuShoppingCartDTO = shoppingCartDTOList.stream().collect(Collectors.groupingBy(ShoppingCartDTO::skuRegionGroupKey));
        Map<String, Map<Long, PriceResultDTO>> priceResultDTOMap = new HashMap<>();
        // ????????????????????????
        List<Future<Map<String, Map<Long, PriceResultDTO>>>> asyncCallback = new ArrayList<>();

        SecurityContext context = SecurityContextHolder.getContext();
        for (Map.Entry<String, List<ShoppingCartDTO>> entry : skuShoppingCartDTO.entrySet()) {
            Future<Map<String, Map<Long, PriceResultDTO>>> checkSaleResultResult = shoppingCartService.asyncQueryPrice(entry, context);
            asyncCallback.add(checkSaleResultResult);
        }
        for (Future<Map<String, Map<Long, PriceResultDTO>>> checkSaleResult : asyncCallback) {
            try {
                priceResultDTOMap.putAll(checkSaleResult.get(10, TimeUnit.SECONDS));
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                e.printStackTrace();
                logger.error("????????????????????????{}", ExceptionUtil.getDetailMessage(e));
            }
        }
        return priceResultDTOMap;
    }


    private void checkPrice(ShoppingCartDTO shoppingCartDTO, PriceResultDTO priceResultDTO) {
        // ????????????????????? ???????????? ?????????????????????
        if (!ObjectUtils.isEmpty(priceResultDTO.getLadderPriceList())) {
//            ???????????????????????????
            ProductPool productPool = new ProductPool();
            productPool.setLadderPriceList(priceResultDTO.getLadderPriceList());
            ShoppingCart shoppingCart = new ShoppingCart();
            BeanUtils.copyProperties(shoppingCartDTO, shoppingCart);
            handLadderPrice(shoppingCart, productPool);
            priceResultDTO.setSellPrice(shoppingCart.getLatestPrice());
        }
        // ?????????????????????
        if (shoppingCartDTO.getLatestPrice().compareTo(priceResultDTO.getSellPrice()) != 0) {
            throw new CommonException(ScecConstants.ErrorCode.ERROR_INCONSISTENT_PRODUCT_PRICE);
        }
        BigDecimal totalPrice = ObjectUtils.isEmpty(priceResultDTO.getSellPrice()) ? BigDecimal.ZERO : (priceResultDTO.getSellPrice().multiply(shoppingCartDTO.getQuantity()));
        //punchout ???????????????
        if (punchoutService.isPuhchout(shoppingCartDTO.getProductSource())) {
            return;
        }
        //???????????????????????????????????????????????????
        if (shoppingCartDTO.getCustomFlag() != null && shoppingCartDTO.getCustomFlag() == 1 && shoppingCartDTO.getShipperFlag() != null && shoppingCartDTO.getShipperFlag() == 1){
            totalPrice = ObjectUtils.isEmpty(priceResultDTO.getSellPrice()) ? BigDecimal.ZERO : (priceResultDTO.getSellPrice().multiply(shoppingCartDTO.getTotalCqNum()));
        }
        if (shoppingCartDTO.getTotalPrice().compareTo(totalPrice) != 0) {
            throw new CommonException(ScecConstants.ErrorCode.ERROR_INCONSISTENT_PRODUCT_PRICE);
        }
    }


    /**
     * ?????????????????????
     *
     * @param shoppingCartDTO ?????????
     * @param priceResultDTO  ????????????????????????
     */
    private void convertParam(ShoppingCartDTO shoppingCartDTO, PriceResultDTO priceResultDTO) {
        shoppingCartDTO.setPurLastPrice(priceResultDTO.getPurchasePrice());
        BigDecimal proxyTotalPrice = ObjectUtils.isEmpty(shoppingCartDTO.getPurLastPrice()) ? BigDecimal.ZERO : (shoppingCartDTO.getPurLastPrice().multiply(shoppingCartDTO.getQuantity()));
        shoppingCartDTO.setPurTotalPrice(proxyTotalPrice);
        shoppingCartDTO.setShowSupplierCompanyId(priceResultDTO.getShowSupplierCompanyId());
        shoppingCartDTO.setShowSupplierName(priceResultDTO.getShowSupplierName());
        shoppingCartDTO.setSupplierCompanyId(priceResultDTO.getSupplierCompanyId());
        shoppingCartDTO.setSupplierCompanyName(priceResultDTO.getSupplierCompanyName());
        shoppingCartDTO.setProxySupplierCompanyId(priceResultDTO.getProxySupplierCompanyId());
        shoppingCartDTO.setProxySupplierCompanyName(priceResultDTO.getProxySupplierCompanyName());
        shoppingCartDTO.setAgreementType(priceResultDTO.getAgreementType());
        shoppingCartDTO.setAgreementBusinessType(priceResultDTO.getAgreementBusinessType());
    }


    private void validateShppingAddress(List<ShoppingCartDTO> shoppingCartDTOList) {
        List<Long> addressIds = shoppingCartDTOList.stream().map(ShoppingCartDTO::getAddressId).collect(Collectors.toList());
        if (CollectionUtils.isEmpty(addressIds) || addressIds.size() != shoppingCartDTOList.size()) {
            throw new CommonException(ScecConstants.ErrorCode.ADDRESS_NOT_EXISTS);
        }
        Set<Long> addressIdSet = new HashSet(addressIds);
        List<Address> addressList = addressRepository.selectByIds(org.apache.commons.lang3.StringUtils.join(addressIdSet, ","));
        if (CollectionUtils.isEmpty(addressList) || addressList.size() != addressIdSet.size()) {
            throw new CommonException(ScecConstants.ErrorCode.ADDRESS_NOT_EXISTS);
        }
        String customerSetting = customizeSettingHelper.queryBySettingCode(shoppingCartDTOList.get(0).getTenantId(), CustomizeSettingCode.Purchase.CataloguePurchase.PERSONAL_ADDRESS);
        if (org.apache.commons.lang3.StringUtils.isNotEmpty(customerSetting) && customerSetting.equals(ScecConstants.ConstantNumber.STRING_0)) {
            //???????????????????????????
            addressList.forEach(a -> {
                if (Objects.isNull(a.getBelongType()) || a.getBelongType() == 2) {
                    //???????????????????????????????????????
                    throw new CommonException(ScecConstants.ErrorCode.PERSION_ADDRESS_DISABLED);
                }
            });
        }
    }
    private void validateShoppingExistCar(List<ShoppingCartDTO> shoppingCartDTOList) {
        //?????????????????????????????????????????????????????????
        String ids = "";
        for (ShoppingCartDTO shoppingCartDTO : shoppingCartDTOList) {
            ids += shoppingCartDTO.getCartId() + ",";
        }
        if (org.apache.commons.lang3.StringUtils.isNotEmpty(ids)) {
            ids = ids.substring(0, ids.length() - 1);
        }
        List<ShoppingCart> shoppingCarts = shoppingCartRepository.selectByIds(ids);
        if (shoppingCarts == null || shoppingCarts.size() == 0 || shoppingCarts.size() < shoppingCartDTOList.size()) {
            throw new CommonException(ScecConstants.ErrorCode.ERROR_REFRESH_SHOPPINGCAR);
        }
    }



    private void validateMinPurchaseAmount(Long tenantId, WatsonsShoppingCartDTO watsonsShoppingCartDTO, BigDecimal price, WatsonsPreRequestOrderDTO watsonsPreRequestOrderDTO) {
        //???????????????????????? 1 ?????? 0 ?????????
        watsonsPreRequestOrderDTO.setMinPurchaseFlag(ScecConstants.ConstantNumber.INT_1);
        //????????????????????????????????????????????????????????????
        if (ScecConstants.ConstantNumber.STRING_1.equals(customizeSettingHelper.queryBySettingCode(tenantId, ScecConstants.SettingCenterCode.MIN_PURCHASE_AMOUNT_CODE))) {
            MinPurchaseConfig minPurchaseConfig = new MinPurchaseConfig();
            minPurchaseConfig.setTenantId(tenantId);
            minPurchaseConfig.setCurrencyName(watsonsShoppingCartDTO.getCurrencyCode());
            minPurchaseConfig.setSupplierCompanyId(watsonsShoppingCartDTO.getSupplierCompanyId());
            MinPurchaseConfig minPurchaseAmount = minPurchaseConfigRepository.selectOne(minPurchaseConfig);
            if (!ObjectUtils.isEmpty(minPurchaseAmount)) {
                //?????????????????????????????????????????????
                if ((new BigDecimal(minPurchaseAmount.getMinPurchaseAmount())).compareTo(price) > ScecConstants.ConstantNumber.INT_0) {
                    watsonsPreRequestOrderDTO.setMinPurchaseResult("??????????????????????????? {" + minPurchaseAmount.getMinPurchaseAmount() + "}");
                    watsonsPreRequestOrderDTO.setMinPurchaseAmount(minPurchaseAmount.getMinPurchaseAmount());
                    watsonsPreRequestOrderDTO.setMinPurchaseFlag(ScecConstants.ConstantNumber.INT_0);
                }
            }
        }
    }

    private void recursionSplitShoppingCart(Map<String, List<WatsonsShoppingCartDTO>> result) {
        //??????????????????????????????id?????????????????? ????????? ????????????
        Map<String, List<WatsonsShoppingCartDTO>> splitResultMap = new HashMap<>();
        List<WatsonsShoppingCartDTO> nonCustomizedProductList = new ArrayList<>();
        List<String> removeKeyList = new ArrayList<>();
        logger.info("the all values are {}",JSONObject.toJSON(result));
        for (Map.Entry<String, List<WatsonsShoppingCartDTO>> entry : result.entrySet()) {
            logger.info("the entry values are {}",JSONObject.toJSON(entry.getValue()));
            Set<WatsonsShoppingCartDTO> set = new TreeSet<>(Comparator.comparing(WatsonsShoppingCartDTO::getProductId));
            nonCustomizedProductList = entry.getValue().stream().filter(watsonsShoppingCartDTO -> {
                    return !(watsonsShoppingCartDTO.getCustomFlag() != null && watsonsShoppingCartDTO.getCustomFlag().equals(ScecConstants.ConstantNumber.INT_1));
                }).collect(Collectors.toList());
            logger.info("the nonCustomizedProductList are {}",JSONObject.toJSON(nonCustomizedProductList));
            if(CollectionUtils.isEmpty(nonCustomizedProductList)){
                logger.info("all customized product");
                continue;
            }
            set.addAll(nonCustomizedProductList);
            //????????????????????????????????????
            if (set.size() == nonCustomizedProductList.size()) {
                logger.info("the set size is equal  nonCustomizedProductList size ");
                splitResultMap.put(entry.getKey(), nonCustomizedProductList);
            } else {
                logger.info("the set size is not equal  nonCustomizedProductList size start filter");
                //????????????????????????????????????????????????????????????????????????list???????????????????????????list???????????????????????????????????????????????????????????????list??????
                //???????????????????????????  ????????????????????????    ???????????????????????????id   ???????????????????????????????????????  ????????????????????????????????????
                Map<Long, List<WatsonsShoppingCartDTO>> map = nonCustomizedProductList.stream().collect(Collectors.groupingBy(WatsonsShoppingCartDTO::getProductId));
                Set<Long> productIdList = nonCustomizedProductList.stream().map(WatsonsShoppingCartDTO::getProductId).filter(Objects::nonNull).collect(Collectors.toSet());
                for (Long productId : productIdList) {
                    List<WatsonsShoppingCartDTO> list = map.get(productId);
                    //??????
                    for (int i = 0; i < list.size(); i++) {
                        //????????????key???????????????key??????
                        String key = entry.getKey() + i;

                        if (splitResultMap.containsKey(key)) {
                            splitResultMap.get(key).add(list.get(i));
                        } else {
                            List<WatsonsShoppingCartDTO> l = new ArrayList<>();
                            l.add(list.get(i));
                            splitResultMap.put(key, l);
                        }
                    }
                }
                //???????????????????????????????????????????????????
                if(nonCustomizedProductList.size() == entry.getValue().size()){
                    removeKeyList.add(entry.getKey());
                }
                logger.info("removeKeyList are {}",JSONObject.toJSON(removeKeyList));
            }
        }
        result.putAll(splitResultMap);
        for (String key : removeKeyList) {
            logger.info("delete keys");
            result.remove(key);
        }
        logger.info("the final result is {}",JSONObject.toJSON(result));
    }


    public Map<String, List<WatsonsShoppingCartDTO>> watsonsGroupPurchaseRequest(Long tenantId, PurReqMergeRule purReqMergeRule, Map<String, List<WatsonsShoppingCartDTO>> groupMap) {

        Map<String, List<WatsonsShoppingCartDTO>> resultMap = new HashMap<>();
        for (String key : groupMap.keySet()) {
            List<WatsonsShoppingCartDTO> watsonsShoppingCartDTOList = groupMap.get(key);
            this.setPurMergeRuleForWatsons(purReqMergeRule);
            if (BaseConstants.Flag.YES.equals(purReqMergeRule.getWarehousing())) {
                for (WatsonsShoppingCartDTO watsonsShoppingCartDTO : watsonsShoppingCartDTOList) {
                    if (BaseConstants.Flag.YES.equals(watsonsShoppingCartDTO.getHasItemFlag())) {
                        //eric ????????????????????????????????????
                        ResponseEntity<String> response = smdmRemoteService.detailByItemCOde(tenantId, watsonsShoppingCartDTO.getItemCode(), watsonsShoppingCartDTO.getInvOrganizationId());
                        if (ResponseUtils.isFailed(response)) {
                            logger.error("detailByItemCOde:{}", response);
                            continue;
                        }
                        logger.info("detailByItemCOde:{}", response);
                        ItemOrgRel itemOrgRelResponseEntity = ResponseUtils.getResponse(response, ItemOrgRel.class);
                        if (BaseConstants.Flag.YES.toString().equals(itemOrgRelResponseEntity.getAttributeVarchar1())) {
                            watsonsShoppingCartDTO.setWarehousing(BaseConstants.Flag.YES);
                        }
                    }
                }
            }
            if (!ObjectUtils.isEmpty(purReqMergeRule)) {
                for (WatsonsShoppingCartDTO watsonsShoppingCartDTO : watsonsShoppingCartDTOList) {
                    StringBuffer keyRes = new StringBuffer();
                    //?????????????????????,?????????????????????,??????
                    if (ObjectUtils.isEmpty(watsonsShoppingCartDTO.getItemId()) && ObjectUtils.isEmpty(watsonsShoppingCartDTO.getItemCategoryId())) {
                        throw new CommonException("????????????????????????????????????????????????,?????????????????????!");
                    }
                    //??????????????????????????????itemId  ???????????????
                    //???itemId???????????????  ???????????????
                    //??????itemId  ???ItemCategoryId??????levelPath
                    //??????????????????levelPath  ?????????????????????????????????????????????
                    //??????????????????levelPath  ????????????????????????parentCategoryId??????
                    //???????????????????????????????????????
                    //?????????????????????????????????
                    processCheckFirstItemCategoryByItemId(tenantId, purReqMergeRule, watsonsShoppingCartDTO, keyRes);
                    processCheckFirstItemCategoryByItemCategoryId(tenantId, purReqMergeRule, watsonsShoppingCartDTO, keyRes);
                }
                Map<String, List<WatsonsShoppingCartDTO>> result = watsonsShoppingCartDTOList.stream().collect(Collectors.groupingBy(WatsonsShoppingCartDTO::getKey));
                resultMap.putAll(result);
            } else {
                resultMap.put(key, groupMap.get(key));
            }
        }
        return resultMap;
    }

    private void processCheckFirstItemCategoryByItemId(Long tenantId, PurReqMergeRule purReqMergeRule, WatsonsShoppingCartDTO watsonsShoppingCartDTO, StringBuffer keyRes) {
        //?????????itemId
        //???????????????itemId ?????? ?????????
        if (!ObjectUtils.isEmpty(watsonsShoppingCartDTO.getItemId())) {
            ResponseEntity<String> responseOne = smdmRemoteService.selectCategoryByItemId(tenantId, watsonsShoppingCartDTO.getItemId(), BaseConstants.Flag.YES);
            if (ResponseUtils.isFailed(responseOne)) {
                logger.error("selectCategoryByItemId error:{}", JSONObject.toJSON(responseOne));
                throw new CommonException("????????????????????????????????????!");
            }
            List<WatsonsItemCategoryDTO> itemCategoryResultOne = ResponseUtils.getResponse(responseOne, new TypeReference<List<WatsonsItemCategoryDTO>>() {
            });
            if (CollectionUtils.isEmpty(itemCategoryResultOne)) {
                logger.error("selectCategoryByItemId error:{}", JSONObject.toJSON(itemCategoryResultOne));
                throw new CommonException("????????????????????????????????????!");
            }
            logger.info("selectCategoryByItemId success:{}", JSONObject.toJSON(itemCategoryResultOne));
            if (itemCategoryResultOne.size() > 1) {
                throw new CommonException("?????????id " + watsonsShoppingCartDTO.getItemId() + "???????????????????????????!");
            }
            WatsonsItemCategoryDTO watsonsItemCategoryDTO = itemCategoryResultOne.get(0);
            while (watsonsItemCategoryDTO.getLevelPath().split("\\|").length > 1) {
                if (ObjectUtils.isEmpty(watsonsItemCategoryDTO.getParentCategoryId())) {
                    throw new CommonException("?????????????????????" + watsonsItemCategoryDTO.getCategoryCode() + "???????????????????????????id!");
                }
                ResponseEntity<String> paramResponse = smdmRemoteNewService.queryById(tenantId, watsonsItemCategoryDTO.getParentCategoryId().toString());
                if (ResponseUtils.isFailed(paramResponse)) {
                    throw new CommonException("?????????????????????:???????????????????????????????????????");
                }
                ItemCategoryDTO response = ResponseUtils.getResponse(paramResponse, new TypeReference<ItemCategoryDTO>() {
                });
                logger.info("the item category info is {}", JSONObject.toJSON(response));
                BeanUtils.copyProperties(response, watsonsItemCategoryDTO);
                logger.info("the final item category info is {}", JSONObject.toJSON(watsonsItemCategoryDTO));
            }
            handleNormalSplit(purReqMergeRule, watsonsShoppingCartDTO, keyRes);
            if (BaseConstants.Flag.YES.equals(purReqMergeRule.getCategory())) {
                keyRes.append(watsonsItemCategoryDTO.getCategoryId()).append("-");
            }
            String keyFinal = String.valueOf(keyRes);
            logger.info("the split key is" + keyFinal);
            watsonsShoppingCartDTO.setItemCategoryId(watsonsItemCategoryDTO.getCategoryId());
            watsonsShoppingCartDTO.setItemCategoryName(watsonsItemCategoryDTO.getCategoryName());
            watsonsShoppingCartDTO.setKey(keyFinal);
        }
    }

    private void processCheckFirstItemCategoryByItemCategoryId(Long tenantId, PurReqMergeRule purReqMergeRule, WatsonsShoppingCartDTO watsonsShoppingCartDTO, StringBuffer keyRes) {
        //????????????itemCategoryId
        //??????itemId  ???ItemCategoryId??????levelPath
        //??????????????????levelPath  ?????????????????????????????????????????????
        //??????????????????levelPath
        //???????????????????????????????????????
        //?????????????????????????????????
        if (ObjectUtils.isEmpty(watsonsShoppingCartDTO.getItemId()) && !ObjectUtils.isEmpty(watsonsShoppingCartDTO.getItemCategoryId())) {

            ResponseEntity<String> itemCategoryInfoRes = smdmRemoteNewService.queryById(tenantId, String.valueOf(watsonsShoppingCartDTO.getItemCategoryId()));
            if (ResponseUtils.isFailed(itemCategoryInfoRes)) {
                logger.error("query itemCategory info By itemCategoryId error! param itemCategoryId: {}", watsonsShoppingCartDTO.getItemCategoryId());
                throw new CommonException("??????????????????id??????????????????????????????!");
            }
            logger.info("query itemCategory info By itemCategoryId success! param itemCategoryId: {}", watsonsShoppingCartDTO.getItemCategoryId());
            ItemCategoryDTO itemCategoryResultOne = ResponseUtils.getResponse(itemCategoryInfoRes, new TypeReference<ItemCategoryDTO>() {
            });
            String levelPath = itemCategoryResultOne.getLevelPath();

            if (!StringUtils.isEmpty(levelPath)) {
                String[] splitRes = levelPath.split("\\|");
                if (splitRes.length > 3) {
                    logger.info("???????????????????????????id, ?????????????????????????????????");
                    throw new CommonException("???????????????????????????????????????,?????????????????????!");
                }
                if (splitRes.length == 3) {
                    //???itemCategoryId??????????????????id
                    logger.info("???????????????????????????id, ????????????????????????");
                    //???????????????????????????id?????????????????????
                    ResponseEntity<String> bLevel = smdmRemoteNewService.queryById(tenantId, String.valueOf(itemCategoryResultOne.getParentCategoryId()));
                    if (ResponseUtils.isFailed(bLevel)) {
                        logger.error("query itemCategory info By itemCategoryId error! param itemCategoryId: {}", itemCategoryResultOne.getParentCategoryId());
                        throw new CommonException("????????????????????????id????????????????????????????????????!");
                    }
                    logger.info("query itemCategory info By itemCategoryId success! param itemCategoryId: {}", itemCategoryResultOne.getParentCategoryId());
                    ItemCategoryDTO bLevelRes = ResponseUtils.getResponse(bLevel, new TypeReference<ItemCategoryDTO>() {
                    });

                    //???????????????????????????id?????????????????????
                    ResponseEntity<String> alevel = smdmRemoteNewService.queryById(tenantId, String.valueOf(bLevelRes.getParentCategoryId()));
                    if (ResponseUtils.isFailed(alevel)) {
                        logger.error("query itemCategory info By itemCategoryId error! param itemCategoryId: {}", bLevelRes.getParentCategoryId());
                        throw new CommonException("????????????????????????id????????????????????????????????????!");
                    }
                    logger.info("query itemCategory info By itemCategoryId success! param itemCategoryId: {}", bLevelRes.getParentCategoryId());
                    ItemCategoryDTO aLevelRes = ResponseUtils.getResponse(alevel, new TypeReference<ItemCategoryDTO>() {
                    });

                    //????????????????????????
                    handleNormalSplit(purReqMergeRule, watsonsShoppingCartDTO, keyRes);
                    if (BaseConstants.Flag.YES.equals(purReqMergeRule.getCategory())) {
                        keyRes.append(aLevelRes.getCategoryId()).append("-");
                    }
                    String keyFinal = String.valueOf(keyRes);
                    logger.info("the split key is" + keyFinal);
                    watsonsShoppingCartDTO.setItemCategoryId(aLevelRes.getCategoryId());
                    watsonsShoppingCartDTO.setItemCategoryName(aLevelRes.getCategoryName());
                    watsonsShoppingCartDTO.setKey(keyFinal);
                }
                if (splitRes.length == 2) {
                    logger.info("???????????????????????????id, ????????????????????????");
                    //???itemCategoryId??????????????????id   ???parentCategoryId???????????????????????????
                    handleNormalSplit(purReqMergeRule, watsonsShoppingCartDTO, keyRes);
                    if (BaseConstants.Flag.YES.equals(purReqMergeRule.getCategory())) {
                        keyRes.append(itemCategoryResultOne.getParentCategoryId()).append("-");
                    }
                    String keyFinal = String.valueOf(keyRes);
                    logger.info("the split key is" + keyFinal);
                    watsonsShoppingCartDTO.setItemCategoryId(itemCategoryResultOne.getParentCategoryId());
                    //??????????????????name
                    ResponseEntity<String> itemCategoryALevel = smdmRemoteNewService.queryById(tenantId, String.valueOf(itemCategoryResultOne.getParentCategoryId()));
                    if (ResponseUtils.isFailed(itemCategoryALevel)) {
                        logger.error("query itemCategory info By itemCategoryId error! param itemCategoryId: {}", itemCategoryResultOne.getParentCategoryId());
                        throw new CommonException("????????????????????????id????????????????????????????????????!");
                    }
                    logger.info("query itemCategory info By itemCategoryId success! param itemCategoryId: {}", itemCategoryResultOne.getParentCategoryId());
                    ItemCategoryDTO itemCategoryALevelRes = ResponseUtils.getResponse(itemCategoryALevel, new TypeReference<ItemCategoryDTO>() {
                    });
                    watsonsShoppingCartDTO.setItemCategoryName(itemCategoryALevelRes.getCategoryName());
                    watsonsShoppingCartDTO.setKey(keyFinal);
                }
                if (splitRes.length == 1) {
                    //???itemCategoryId??????????????????id ????????????
                    logger.info("???????????????????????????id, ????????????????????????");
                    handleNormalSplit(purReqMergeRule, watsonsShoppingCartDTO, keyRes);
                    if (BaseConstants.Flag.YES.equals(purReqMergeRule.getCategory())) {
                        keyRes.append(itemCategoryResultOne.getCategoryId()).append("-");
                    }
                    String keyFinal = String.valueOf(keyRes);
                    logger.info("the split key is" + keyFinal);
                    watsonsShoppingCartDTO.setItemCategoryId(itemCategoryResultOne.getCategoryId());
                    watsonsShoppingCartDTO.setItemCategoryName(itemCategoryResultOne.getCategoryName());
                    watsonsShoppingCartDTO.setKey(keyFinal);
                }
                if (splitRes.length == 0) {
                    logger.error("???????????????????????????id, ???????????????levelPath");
                    throw new CommonException("????????????id????????????????????????!");
                }
            } else {
                logger.error("?????????????????????????????????????????????????????????levelPath??????");
                throw new CommonException("??????????????????????????????????????????!");
            }
        }
    }

    private void handleNormalSplit(PurReqMergeRule purReqMergeRule, WatsonsShoppingCartDTO watsonsShoppingCartDTO, StringBuffer keyRes) {
        keyRes.append(watsonsShoppingCartDTO.getSupplierCompanyId()).append("-");
        if (BaseConstants.Flag.YES.equals(purReqMergeRule.getAddressFlag())) {
            if (watsonsShoppingCartDTO.getAllocationInfoList() == null) {
                logger.error("????????????????????????:{}", watsonsShoppingCartDTO.getAllocationInfoList());
                throw new CommonException("?????????????????????!");
            }
            keyRes.append(watsonsShoppingCartDTO.getAllocationInfoList().get(0).getAddressRegion()).append("-").append(watsonsShoppingCartDTO.getAllocationInfoList().get(0).getFullAddress()).append("-");
        }
        keyRes.append(watsonsShoppingCartDTO.getAllocationInfoList().get(0).getDeliveryType()).append("-");
        keyRes.append(watsonsShoppingCartDTO.getAllocationInfoList().get(0).getCostShopId()).append("-");
        if (BaseConstants.Flag.YES.equals(purReqMergeRule.getFreightType())) {
            keyRes.append(watsonsShoppingCartDTO.getVolumeUnitPrice()).append("-");
        }
    }

    private void setPurMergeRuleForWatsons(PurReqMergeRule purReqMergeRule) {
        purReqMergeRule.setCategory(BaseConstants.Flag.YES);
        purReqMergeRule.setWarehousing(BaseConstants.Flag.YES);
//        purReqMergeRule.setAddressFlag(BaseConstants.Flag.YES);
    }


    private void selectCustomizedProductListForWatsons(Long tenantId,List<WatsonsShoppingCartDTO> watsonsShoppingCartDTOS) {
        for (WatsonsShoppingCartDTO watsonsShoppingCartDTO : watsonsShoppingCartDTOS) {
            List<AllocationInfo> allocationInfoList = allocationInfoRepository.selectByCondition(Condition.builder(AllocationInfo.class).andWhere(Sqls.custom()
                    .andEqualTo(AllocationInfo.FIELD_CART_ID, watsonsShoppingCartDTO.getCartId())).build());
            watsonsShoppingCartDTO.setAllocationInfoList(allocationInfoList);
        }
        List<WatsonsShoppingCartDTO> filterShoppingCart = watsonsShoppingCartDTOS.stream().filter(s -> s.getCustomFlag() != null && s.getCustomFlag() == 1).collect(Collectors.toList());
        if (CollectionUtils.isEmpty(filterShoppingCart)) {
            return;
        }
        WatsonsCustomizedProductDTO watsonsCustomizedProductDTO = new WatsonsCustomizedProductDTO(watsonsShoppingCartDTOS);
        List<CustomizedProductLine> customizedProductLineList = watsonsCustomizedProductLineService.selectCustomizedProductList(tenantId, watsonsCustomizedProductDTO);
        logger.info("the customizedProductLineList are {}",JSONObject.toJSON(customizedProductLineList));
        //????????????????????????????????????
        customizedProductLineService.checkCustomizedProduct(tenantId, customizedProductLineList);
        //???shoppingCartDTO??????
        Map<Long, List<CustomizedProductLine>> map = customizedProductLineList.stream().collect(Collectors.groupingBy(CustomizedProductLine::getRelationId));
        //???????????????????????????
        List<Long> productIdList = filterShoppingCart.stream().map(WatsonsShoppingCartDTO::getProductId).collect(Collectors.toList());
        List<SkuCustomDTO> skuCustomList = productWorkbenchRepository.selectSkuListCustomAttrNoException(tenantId, productIdList);
        Map<Long, SkuCustomDTO> skuCustomMap = new HashMap<>();
        if (!CollectionUtils.isEmpty(skuCustomList)){
            skuCustomMap = skuCustomList.stream().collect(Collectors.toMap(SkuCustomDTO::getSkuId, Function.identity(), (k1,k2)->k1));
        }
        for (WatsonsShoppingCartDTO watsonsShoppingCartDTO: filterShoppingCart) {
            SkuCustomDTO skuCustomDTO = skuCustomMap.get(watsonsShoppingCartDTO.getProductId());
            watsonsShoppingCartDTO.assignmentShipperInfo(ObjectUtils.isEmpty(skuCustomDTO) ? new ArrayList<>() : skuCustomDTO.getSpuCustomGroupList());
            assignmentCustomizedProductList(map,watsonsShoppingCartDTO);
            calculateCustomizedProductForShoppingCartDTO(watsonsShoppingCartDTO);
            watsonsShoppingCartDTO.checkCustomizedProductChange();
        }
        logger.info("the customized shopping carts are {}",JSONObject.toJSON(watsonsShoppingCartDTOS));
    }

    @Override
    public ShoppingCart creates(ShoppingCart shoppingCart, Long organizationId) {
        ShoppingCart shoppingCartParam = super.creates(shoppingCart,organizationId);
        List<AllocationInfo> allocationInfoList = new ArrayList<>();
        if (!ObjectUtils.isEmpty(shoppingCart.getUpdateOrganizationFlag()) && shoppingCart.getUpdateOrganizationFlag() == 1) {
            //    ???????????????????????????????????????????????????????????????????????????????????? ??????????????????, ???????????????????????????cartId, ??????????????????????????????cartId
            //??????????????????????????????????????????????????????????????????cartId??????????????????cartId
            allocationInfoList = allocationInfoRepository.select(AllocationInfo.FIELD_CART_ID, shoppingCart.getCartId());
        }
        if (!ObjectUtils.isEmpty(shoppingCartParam.getUpdateOrganizationFlag()) && shoppingCartParam.getUpdateOrganizationFlag() == 1) {
            //?????????????????????????????????????????????????????????cartId??????????????????cartId
            if (!CollectionUtils.isEmpty(allocationInfoList)) {
                for (AllocationInfo allocationInfo : allocationInfoList) {
                    allocationInfo.setCartId(shoppingCartParam.getCartId());
                    allocationInfoRepository.updateOptional(allocationInfo, AllocationInfo.FIELD_CART_ID);
                }
            }
        }
        return shoppingCartParam;
    }

    public void calculateCustomizedProductForShoppingCartWhenAllocationUpdate(WatsonsShoppingCart watsonsShoppingCart){
        //????????????????????????????????????????????????????????????????????????
        if (ObjectUtils.isEmpty(watsonsShoppingCart.getCustomFlag()) || watsonsShoppingCart.getCustomFlag() != 1 || ObjectUtils.isEmpty(watsonsShoppingCart.getShipperFlag()) || watsonsShoppingCart.getShipperFlag() == 0 ){
            return;
        }
        if (CollectionUtils.isEmpty(watsonsShoppingCart.getCustomizedProductLineList())){
            watsonsShoppingCart.setTotalPrice(watsonsShoppingCart.getQuantity().add(watsonsShoppingCart.getLatestPrice()));
            return;
        }
        //??????????????????????????????????????????
        BigDecimal calTotalPrice = null;
        //??????????????????????????????
        BigDecimal calTotalCqNum = null;
        for (CustomizedProductLine customizedProductLine : watsonsShoppingCart.getCustomizedProductLineList()){
            customizedProductLine.setLatestPrice(watsonsShoppingCart.getLatestPrice());
            allocationInfoService.calculateForCpLine(customizedProductLine);
            if (ObjectUtils.isEmpty(customizedProductLine.getCpAmount()) || ObjectUtils.isEmpty(customizedProductLine.getLineCqNum()) || ObjectUtils.isEmpty(customizedProductLine.getLineTotalCqNum())) {
                continue;
            }
            //?????????????????????????????????
            calTotalPrice = customizedProductLine.getCpAmount().add(calTotalPrice == null ? BigDecimal.ZERO : calTotalPrice);
            //???????????????????????? ????????????
            calTotalCqNum = customizedProductLine.getLineTotalCqNum().add(calTotalCqNum == null ? BigDecimal.ZERO : calTotalCqNum);
        }
        watsonsShoppingCart.setTotalPrice(calTotalPrice);
        watsonsShoppingCart.setTotalCqNum(calTotalCqNum);
        logger.info("after calculate customized the shopping cart is {}",JSONObject.toJSON(watsonsShoppingCart));
    }

    /**
     * ????????????????????????????????????
     */
    public void calculateCustomizedProductForShoppingCartDTO(WatsonsShoppingCartDTO watsonsShoppingCartDTO){
        //????????????????????????????????????????????????????????????????????????
        if (watsonsShoppingCartDTO.getCustomFlag() == null || watsonsShoppingCartDTO.getCustomFlag() != 1 || watsonsShoppingCartDTO.getShipperFlag() == null || watsonsShoppingCartDTO.getShipperFlag() == 0){
            return;
        }
        if (org.springframework.util.CollectionUtils.isEmpty(watsonsShoppingCartDTO.getCustomizedProductLineList())){
            watsonsShoppingCartDTO.setTotalPrice(watsonsShoppingCartDTO.getQuantity().multiply(watsonsShoppingCartDTO.getLatestPrice()));
            return;
        }
        //??????????????????????????????????????????
        BigDecimal calTotalPrice = null;
        //??????????????????????????????
        BigDecimal calTotalCqNum = null;
        for (CustomizedProductLine customizedProductLine : watsonsShoppingCartDTO.getCustomizedProductLineList()){
            customizedProductLine.setLatestPrice(watsonsShoppingCartDTO.getLatestPrice());
            allocationInfoService.calculateForCpLine(customizedProductLine);
            if (ObjectUtils.isEmpty(customizedProductLine.getCpAmount()) || ObjectUtils.isEmpty(customizedProductLine.getLineCqNum()) || ObjectUtils.isEmpty(customizedProductLine.getLineTotalCqNum())) {
                continue;
            }
            //????????????????????????
            calTotalPrice = customizedProductLine.getCpAmount().add(calTotalPrice == null ? BigDecimal.ZERO : calTotalPrice);
            //?????????????????????
            calTotalCqNum = customizedProductLine.getLineTotalCqNum().add(calTotalCqNum == null ? BigDecimal.ZERO : calTotalCqNum);
        }
        watsonsShoppingCartDTO.setTotalPrice(calTotalPrice);
        watsonsShoppingCartDTO.setTotalCqNum(calTotalCqNum);
        logger.info("after calculate price the watsonsShoppingCart is {}",JSONObject.toJSON(watsonsShoppingCartDTO));
    }

    public void assignmentCustomizedProductList(Map<Long, List<CustomizedProductLine>> customizedProductMap,WatsonsShoppingCartDTO watsonsShoppingCartDTO){
            //????????????id??????
            List<CustomizedProductLine> allList = new ArrayList<>();
            if (org.springframework.util.CollectionUtils.isEmpty(watsonsShoppingCartDTO.getAllocationInfoList())){
                watsonsShoppingCartDTO.setCustomizedProductLineList(new ArrayList<>());
            } else {
                watsonsShoppingCartDTO.setCustomizedProductLineList(new ArrayList<>());
                for (AllocationInfo allocationInfo : watsonsShoppingCartDTO.getAllocationInfoList()){
                    List<CustomizedProductLine> customizedProductLineList = customizedProductMap.getOrDefault(allocationInfo.getAllocationId(), new ArrayList<>());
                    allList.addAll(customizedProductLineList);
                }
                watsonsShoppingCartDTO.setCustomizedProductLineList(allList);
            }
            logger.info("after assignmentCustomizedProductList, the watsonsShoppingCart is {}",JSONObject.toJSON(watsonsShoppingCartDTO));
    }

    @Override
    public void calculatePrice(ShoppingCart shoppingCart) {
        //????????????????????????????????????????????????????????????????????????
        if (ObjectUtils.isEmpty(shoppingCart.getCustomFlag()) || shoppingCart.getCustomFlag() != 1 || ObjectUtils.isEmpty(shoppingCart.getShipperFlag()) || shoppingCart.getShipperFlag() == 0){
            return;
        }
        if (CollectionUtils.isEmpty(shoppingCart.getCustomizedProductLineList())){
            shoppingCart.setTotalPrice(shoppingCart.getQuantity().multiply(shoppingCart.getLatestPrice()));
            return;
        }
        //??????????????????????????????????????????
        BigDecimal calTotalPrice = null;
        //??????????????????????????????
        BigDecimal calTotalCqNum = null;
        for (CustomizedProductLine customizedProductLine : shoppingCart.getCustomizedProductLineList()){
            customizedProductLine.setLatestPrice(shoppingCart.getLatestPrice());
            customizedProductLine.calculate();
            if (ObjectUtils.isEmpty(customizedProductLine.getCpAmount()) || ObjectUtils.isEmpty(customizedProductLine.getLineCqNum()) || ObjectUtils.isEmpty(customizedProductLine.getLineTotalCqNum())) {
                continue;
            }
            //????????????????????????
            calTotalPrice = customizedProductLine.getCpAmount().add(calTotalPrice == null ? BigDecimal.ZERO : calTotalPrice);
            //?????????????????????
            calTotalCqNum = customizedProductLine.getLineTotalCqNum().add(calTotalCqNum == null ? BigDecimal.ZERO : calTotalCqNum);
        }
        shoppingCart.setTotalPrice(calTotalPrice);
        shoppingCart.setTotalCqNum(calTotalCqNum);
        logger.info("after calculate price the watsonsShoppingCart is {}",JSONObject.toJSON(shoppingCart));
    }

    @Override
    public CustomizedSameResultDTO checkCustomizedProductLine(ShoppingCart shoppingCart, List<ShoppingCart> existShoppingCarts) {
        Long tenantId = DetailsHelper.getUserDetails().getTenantId();
        SkuBaseInfoDTO skuBaseInfoDTO = productWorkbenchRepository.querySingleSkuBaseInfo(tenantId, shoppingCart.getProductId());
        if (ObjectUtils.isEmpty(skuBaseInfoDTO) || ObjectUtils.isEmpty(skuBaseInfoDTO.getCustomFlag()) || skuBaseInfoDTO.getCustomFlag() != 1) {
            shoppingCart.setNeedInsertCustomized(0);
            return new CustomizedSameResultDTO(skuBaseInfoDTO.getCustomFlag());
        }
        return new CustomizedSameResultDTO();
    }
}
