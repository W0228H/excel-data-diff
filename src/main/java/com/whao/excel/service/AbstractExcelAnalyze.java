package com.whao.excel.service;

import cn.hutool.core.io.resource.ClassPathResource;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.whao.excel.domain.read.InputFeatureDataDto;
import com.whao.excel.factory.ExcelAnalyzeFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.formula.functions.T;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
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

    protected static final String COMMON_PATH = System.getProperty("user.home") + "/Desktop/特征一致率数据汇总/";

    protected static final int MAX_SHEET_NAME_LENGTH = 31;

    protected static final String INVALID_CHARACTERS = "[\\[\\]/\\\\?*]";

    /**
     * 特征名称映射关系 model -> DW_
     */
    protected static final BiMap<String, String> FEATURE_NAME_MAP = HashBiMap.create();

    static {
        File commonFile = new File(COMMON_PATH);
        if (commonFile.mkdirs()) {
            log.info("文件夹初始化创建完成!");
        }
    }

    /**
     * 前置处理, 将特征映射到本地缓存
     */
    @Override
    public void preProcess() {
        if (!FEATURE_NAME_MAP.isEmpty()) {
            return;
        }
        ClassPathResource resource = new ClassPathResource(INPUT_FEATURE_PATH);
        EasyExcel.read(resource.getStream(), InputFeatureDataDto.class, new ReadListener<InputFeatureDataDto>() {
                    @Override
                    public void invoke(InputFeatureDataDto data, AnalysisContext context) {
                        String modelKey = data.getModelKey();
                        String code = data.getCode();
                        FEATURE_NAME_MAP.put(modelKey, code);
                    }

                    @Override
                    public void doAfterAllAnalysed(AnalysisContext context) {
                        log.info("input feature map init success!");
                    }
                })
                .sheet().doRead();
    }

    @Override
    public abstract Res analyzeExcel(MultipartFile file) throws IOException;

    @Override
    public void outputExcel(Res res) {

    }

    @Override
    public abstract String getAnalyzeOption();

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
