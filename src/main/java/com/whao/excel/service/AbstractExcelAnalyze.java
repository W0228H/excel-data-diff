package com.whao.excel.service;

import cn.hutool.core.io.resource.ClassPathResource;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.whao.excel.domain.read.InputFeatureDataDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author xiongwh
 * @date 2024/8/10 1:23 PM
 */
@Slf4j
public abstract class AbstractExcelAnalyze<Req extends MultipartFile, Res> implements ExcelAnalyzeService<Req, Res> {

    private static final String INPUT_FEATURE_PATH = "/data/inputFeature.csv";

    protected static final int MAX_SHEET_NAME_LENGTH = 31;

    protected static final String INVALID_CHARACTERS = "[\\[\\]/\\\\?*]";

    /**
     * 特征名称映射关系
     */
    protected static final Map<String, String> FEATURE_NAME_MAP = new ConcurrentHashMap<>();

    /**
     * 前置处理, 将特征映射到本地缓存
     */
    @Override
    public void preProcess() {
        ClassPathResource resource = new ClassPathResource(INPUT_FEATURE_PATH);
        EasyExcel.read(resource.getStream(), InputFeatureDataDto.class, new ReadListener<InputFeatureDataDto>() {
                    @Override
                    public void invoke(InputFeatureDataDto data, AnalysisContext context) {
                        String modelKey = data.getModelKey();
                        String code = data.getCode();
                        FEATURE_NAME_MAP.put(code, modelKey);
                    }
                    @Override
                    public void doAfterAllAnalysed(AnalysisContext context) {
                        log.info("input feature map handler finished!");
                    }
                })
                .sheet().doRead();
    }

    @Override
    public abstract Res analyzeExcel(MultipartFile file) throws IOException;

    @Override
    public void outputExcel(Res res) {

    }

    public Map<String, String> getFeatureNameMap() {
        return FEATURE_NAME_MAP;
    }

    public void execute(MultipartFile file) throws IOException {
        preProcess();
        outputExcel(analyzeExcel(file));
    }

    protected String canonicalSheetName(String sheetName) {
        // 规范化 sheet 名称
        String sheetUpdate = sheetName.length() > MAX_SHEET_NAME_LENGTH ? sheetName.substring(0, MAX_SHEET_NAME_LENGTH) : sheetName;
        return sheetUpdate.replaceAll(INVALID_CHARACTERS, "_");
    }
}
