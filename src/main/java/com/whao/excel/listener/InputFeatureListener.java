package com.whao.excel.listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.whao.excel.domain.read.InputFeatureDataDto;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class InputFeatureListener implements ReadListener<InputFeatureDataDto> {

    private static AtomicLong aCounts = new AtomicLong();

    /**
     * 拿到映射关系
     */
    private static final Map<String, String> featureNameMap = new ConcurrentHashMap<>();

    @Override
    public void invoke(InputFeatureDataDto inputFeatureDataDto, AnalysisContext analysisContext) {
        String modelKey = inputFeatureDataDto.getModelKey();
        String code = inputFeatureDataDto.getCode();
        featureNameMap.put(code, modelKey);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {

    }

    public Map<String, String> getFeatureNameMap() {
        return featureNameMap;
    }
}
