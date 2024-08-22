package com.whao.excel.service;

import cn.hutool.core.io.resource.ClassPathResource;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.enums.CellExtraTypeEnum;
import com.alibaba.excel.read.listener.ReadListener;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.whao.excel.domain.read.DatasourceTimeDto;
import com.whao.excel.domain.read.InputFeatureDataDto;
import com.whao.excel.domain.read.ModelDataSourceDto;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * @author xiongwh
 * @date 2024/8/10 1:23 PM
 */
@Slf4j
public abstract class AbstractExcelAnalyze<Req extends MultipartFile, Res> implements ExcelAnalyzeService<Req, Res> {

    private static final String INPUT_FEATURE_PATH = "/data/inputFeature.csv";

    private static final String MODEL_DATASOURCE_PATH = "/data/modelDatasourceMap.xlsx";

    private static final String DATASOURCE_TIME_PATH = "/data/datasourceTime.xlsx";

    protected static final String COMMON_PATH = System.getProperty("user.home") + "/Desktop/特征一致率数据汇总/";

    protected static final int MAX_SHEET_NAME_LENGTH = 31;

    protected static final String INVALID_CHARACTERS = "[\\[\\]/\\\\?*]";

    /**
     * 特征名称映射关系 model -> DW_
     */
    protected static final BiMap<String, String> FEATURE_NAME_MAP = HashBiMap.create();

    /**
     * 特征名映射hive表关系 model -> hive name
     */
    protected static final Map<String, String> MODEL_DATASOURCE_MAP = new HashMap<>();

    /**
     * 数据源映射分区时区
     */
    protected static final Map<String, String> DATASOURCE_TIME_MAP = new HashMap<>();

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
    public void preProcess() throws IOException {
        if (!FEATURE_NAME_MAP.isEmpty() && !MODEL_DATASOURCE_MAP.isEmpty() && !DATASOURCE_TIME_MAP.isEmpty()) {
            return;
        }
        ClassPathResource resource1 = new ClassPathResource(INPUT_FEATURE_PATH);
        ClassPathResource resource2 = new ClassPathResource(MODEL_DATASOURCE_PATH);
        ClassPathResource resource3 = new ClassPathResource(DATASOURCE_TIME_PATH);
        try (InputStream inputStream1 = resource1.getStream();
             InputStream inputStream2 = resource2.getStream();
             InputStream inputStream3 = resource3.getStream()) {
            EasyExcel.read(inputStream1, InputFeatureDataDto.class, new ReadListener<InputFeatureDataDto>() {
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
            }).sheet().doRead();

            EasyExcel.read(inputStream2, ModelDataSourceDto.class, new ReadListener<ModelDataSourceDto>() {
                @Override
                public void invoke(ModelDataSourceDto data, AnalysisContext analysisContext) {
                    String modelKey = data.getModelKey();
                    String datasource = data.getDatasource();
                    MODEL_DATASOURCE_MAP.put(modelKey, datasource);
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext analysisContext) {
                    log.info("model datasource map init success!");
                }
            }).sheet().doRead();

            EasyExcel.read(inputStream3, DatasourceTimeDto.class, new ReadListener<DatasourceTimeDto>() {
                @Override
                public void invoke(DatasourceTimeDto data, AnalysisContext analysisContext) {
                    String datasource = data.getDatasource();
                    String time = data.getTime();
                    if (StringUtils.isNotBlank(time)) {
                        DATASOURCE_TIME_MAP.put(datasource, time);
                    }
                }

                @Override
                public void doAfterAllAnalysed(AnalysisContext analysisContext) {
                    log.info("datasource time map init success!");
                }

            }).extraRead(CellExtraTypeEnum.MERGE).sheet().doRead();
        }
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
