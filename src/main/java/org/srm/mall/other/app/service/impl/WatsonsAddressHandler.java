package org.srm.mall.other.app.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.hzero.boot.scheduler.infra.annotation.JobHandler;
import org.hzero.boot.scheduler.infra.enums.ReturnT;
import org.hzero.boot.scheduler.infra.handler.IJobHandler;
import org.hzero.boot.scheduler.infra.tool.SchedulerTool;
import org.hzero.core.base.BaseConstants;
import org.hzero.mybatis.domian.Condition;
import org.hzero.mybatis.util.Sqls;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.srm.mall.context.dto.CompanyDTO;
import org.srm.mall.infra.constant.WatsonsConstants;
import org.srm.mall.other.api.dto.WatsonsAddressDTO;
import org.srm.mall.other.async.WatsonsAddressAsyncTask;
import org.srm.mall.other.domain.repository.WatsonsAddressRepository;
import org.srm.mall.platform.domain.repository.CompanyRepository;
import org.srm.mall.region.domain.entity.Region;
import org.srm.mall.region.domain.repository.RegionRepository;
import org.srm.web.annotation.Tenant;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@JobHandler("watsonsUpdateAddress")
@Tenant(WatsonsConstants.TENANT_NUMBER)
@Service
public class WatsonsAddressHandler implements IJobHandler {

    @Autowired
    private WatsonsAddressRepository watsonsAddressRepository;

    @Autowired
    private WatsonsAddressAsyncTask addressAsyncTask;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private RegionRepository regionRepository;

    @Override
    public ReturnT execute(Map<String, String> map, SchedulerTool tool) {
        //?????????????????????25???????????????????????????
        Date date = getSelectParamDate(tool);
        //????????????????????????id
        Long tenantId = watsonsAddressRepository.selectTenantId(WatsonsConstants.TENANT_NUMBER);
        //????????? ??????  ????????????id  ????????????????????????????????????????????????
        List<WatsonsAddressDTO> addressList = watsonsAddressRepository.selectUpdateAddressInfo(tenantId, date);
        if (CollectionUtils.isEmpty(addressList)){
            return ReturnT.SUCCESS;
        }
        tool.info("??????" + addressList.size() + "??????????????????");
        List<WatsonsAddressDTO> successList = new ArrayList<>();
        List<WatsonsAddressDTO> errorList = new ArrayList<>();
        //???????????????????????????id
        selectInvOrgAddress(tenantId, addressList);
        for (WatsonsAddressDTO watsonsAddressDTO : addressList){
            if (StringUtils.isEmpty(watsonsAddressDTO.getAddress()) || ObjectUtils.isEmpty(watsonsAddressDTO.getCompanyId())){
                watsonsAddressDTO.setSuccess(false);
                watsonsAddressDTO.setResultMsg("???????????????"+ watsonsAddressDTO.getInvOrganizationId() +"???????????????id???????????????:" + watsonsAddressDTO);
                errorList.add(watsonsAddressDTO);
            } else {
                watsonsAddressDTO.setSuccess(true);
                successList.add(watsonsAddressDTO);
            }
        }
        //?????????????????????id?????????????????????
        //???????????????
        List<WatsonsAddressDTO> resultList = selectBaiduReverseGeocoding(successList, tenantId);
        successList = resultList.stream().filter(WatsonsAddressDTO::getSuccess).collect(Collectors.toList());
        errorList.addAll(resultList.stream().filter(s -> !s.getSuccess()).collect(Collectors.toList()));
        if (CollectionUtils.isEmpty(successList)){
            tool.error("?????????????????????????????????????????? 0 , ???????????????" + errorList.size());
            createErrorInfo(errorList, tool);
            return ReturnT.FAILURE;
        }
        //???????????????????????????????????????????????????
        List<WatsonsAddressDTO> list = selectRegionMapping(successList, tool);
        if (CollectionUtils.isEmpty(list)){
            return ReturnT.FAILURE;
        }
        //??????
        List<WatsonsAddressDTO> result = updateMallAddress(list, tenantId);
        List<WatsonsAddressDTO> successResults = result.stream().filter(WatsonsAddressDTO::getSuccess).collect(Collectors.toList());
        List<WatsonsAddressDTO> errorResults = result.stream().filter(s -> !s.getSuccess()).collect(Collectors.toList());
        //?????????????????????????????????
        errorList.addAll(errorResults);
        createErrorInfo(errorList, tool);
        //???????????????????????????
        tool.info("?????????????????????" + successResults.size() + ", ?????????????????????" + errorResults.size());
        return ReturnT.SUCCESS;
    }

    private Date getSelectParamDate(SchedulerTool tool){
        String schedulePram = tool.getJobDataDTO().getParam();
        Date date = null;
        try{
            if (!StringUtils.isEmpty(schedulePram)){
                JSONObject jsonObject = JSON.parseObject(schedulePram);
                String time = (String) jsonObject.get("time");
                if (!StringUtils.isEmpty(time)){
                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat(BaseConstants.Pattern.DATETIME);
                    date = simpleDateFormat.parse(time);
                }
            }
        } catch (ParseException e) {
            log.error("??????????????????");
            e.printStackTrace();
        }
        if (date == null){
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, calendar.get(Calendar.HOUR_OF_DAY) - 25);
            date = calendar.getTime();
        }
        return date;
    }

    private void selectInvOrgAddress(Long tenantId, List<WatsonsAddressDTO> list){
        List<Long> invOrganizationIdList = list.stream().map(WatsonsAddressDTO::getInvOrganizationId).collect(Collectors.toList());
        //???????????????id??????????????????id ???????????????system
        List<WatsonsAddressDTO> resultList = watsonsAddressRepository.selectInvorgAddress(invOrganizationIdList, tenantId);
        CompanyDTO companyDTO = companyRepository.selectByCompanyName("?????????");
        //?????????????????????????????????
        resultList = resultList.stream().filter(s -> !StringUtils.isEmpty(s.getAddress()) && companyDTO != null && !ObjectUtils.isEmpty(companyDTO.getCompanyId())).collect(Collectors.toList());
        if (CollectionUtils.isEmpty(resultList)){
            return;
        }
        //??????id??????????????????????????????????????????id???key
        Map<Long, WatsonsAddressDTO> map = resultList.stream().collect(Collectors.toMap(WatsonsAddressDTO::getInvOrganizationId, Function.identity(), (k1, k2) -> k1));
        for (WatsonsAddressDTO address : list){
            WatsonsAddressDTO watsonsAddressDTO = map.get(address.getInvOrganizationId());
            if (watsonsAddressDTO != null){
                address.setAddress(watsonsAddressDTO.getAddress());
                address.setCompanyId(companyDTO == null ? null : companyDTO.getCompanyId());
            }
        }
    }

    private List<WatsonsAddressDTO> selectBaiduReverseGeocoding(List<WatsonsAddressDTO> addressList, Long tenantId){
        List<WatsonsAddressDTO> resultList = new ArrayList<>();
        List<Future<WatsonsAddressDTO>> asyncCallbackList = new ArrayList<>();
        for (WatsonsAddressDTO address : addressList){
            Future<WatsonsAddressDTO> result = addressAsyncTask.asyncReverseGeocoding(tenantId, address);
            asyncCallbackList.add(result);
        }
        for (Future<WatsonsAddressDTO> future : asyncCallbackList){
            try{
                resultList.add(future.get(60, TimeUnit.SECONDS));
            }catch (InterruptedException | ExecutionException | TimeoutException e){
                log.error("?????????????????????????????????????????????:" + e);
            }
        }
        return resultList;
    }


    private List<WatsonsAddressDTO> updateMallAddress(List<WatsonsAddressDTO> addressList, Long tenantId){
        List<WatsonsAddressDTO> resultList = new ArrayList<>();
        List<Future<WatsonsAddressDTO>> asyncCallbackList = new ArrayList<>();
        for (WatsonsAddressDTO address : addressList){
            Future<WatsonsAddressDTO> result = addressAsyncTask.asyncUpdateMallAddress(tenantId, address);
            asyncCallbackList.add(result);
        }
        for (Future<WatsonsAddressDTO> future : asyncCallbackList){
            try{
                resultList.add(future.get(60, TimeUnit.SECONDS));
            }catch (InterruptedException | ExecutionException | TimeoutException e){
                log.error("????????????????????????:" + e);
            }
        }
        return resultList;
    }

    private List<WatsonsAddressDTO> selectRegionMapping(List<WatsonsAddressDTO> successList, SchedulerTool tool){
        //???????????????????????????????????????????????????
        List<Integer> adCodeList = successList.stream().map(s -> s.getAddressResult().getAddressComponent().getAdcode()).collect(Collectors.toList());
        if (CollectionUtils.isEmpty(adCodeList)){
            tool.error("???????????????????????????????????????????????????????????????adcode??????");
            return new ArrayList<>();
        }
        //?????????????????????
        List<WatsonsAddressDTO> mappingAddressList = watsonsAddressRepository.selectRegionMapping(adCodeList);
        List<Region> mallRegionList = regionRepository.selectByCondition(Condition.builder(Region.class).andWhere(Sqls.custom().andIn(Region.FIELD_REGION_CODE, adCodeList)).build());
        if (CollectionUtils.isEmpty(mappingAddressList)){
            tool.error("?????????????????????????????????adcode?????????????????????????????????????????????adcodes??????" + adCodeList);
            return new ArrayList<>();
        }
        Map<Integer, WatsonsAddressDTO> mappingAddressMap = mappingAddressList.stream().collect(Collectors.toMap(WatsonsAddressDTO::getAdCode, Function.identity(), (k1, k2) -> k1));
        Map<String, Region> mallRegionMap = mallRegionList.stream().collect(Collectors.toMap(Region::getRegionCode, Function.identity(), (k1, k2) -> k1));
        for (WatsonsAddressDTO externalAddress : successList){
            //????????????????????????????????????????????????????????????
            externalAddress.setAdCode(externalAddress.getAddressResult().getAddressComponent().getAdcode());
            WatsonsAddressDTO temp = mappingAddressMap.get(externalAddress.getAdCode());
            Region region = mallRegionMap.get(String.valueOf(externalAddress.getAdCode()));
            if (temp == null && region == null){
                //??????????????????????????????
                externalAddress.setSuccess(false);
                externalAddress.setResultMsg("?????????????????????????????????adcode?????????????????????????????????????????????adcodes??????" + adCodeList);
            } else {
                if (temp != null){
                    externalAddress.setMallLevelPath(temp.getMallLevelPath());
                    externalAddress.setMallRegionId(temp.getMallRegionId());
                    externalAddress.setMallRegionLevel(temp.getMallRegionLevel());
                }else {
                    externalAddress.setMallLevelPath(region.getLevelPath());
                    externalAddress.setMallRegionId(region.getRegionId());
                    externalAddress.setMallRegionLevel(region.getLevelNumber());
                }

            }
        }
        return successList;
    }

    private void createErrorInfo(List<WatsonsAddressDTO> errorList, SchedulerTool tool){
        if (!CollectionUtils.isEmpty(errorList)){
            for (WatsonsAddressDTO watsonsAddressDTO : errorList){
                tool.error("??????????????????Id???:" + watsonsAddressDTO.getInvOrganizationId() + "????????????????????????: " + watsonsAddressDTO.getLongitude() +
                        "????????????" +watsonsAddressDTO.getLatitude() + ",???????????????" + watsonsAddressDTO.getResultMsg());
            }
        }
    }
}
