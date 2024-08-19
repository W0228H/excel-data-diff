package com.whao.excel.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.metadata.data.WriteCellData;
import com.alibaba.excel.read.listener.ReadListener;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.whao.excel.domain.common.FeatureSummarizeSheet;
import com.whao.excel.domain.common.FeatureTimeoutRate;
import com.whao.excel.domain.read.DarwinMuseDiffData;
import com.whao.excel.domain.write.MuseDarwinWriteFeatureData;
import com.whao.excel.service.AbstractExcelAnalyze;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @author xiongwh
 * @date 2024/8/12 10:03 PM
 */
@Service
@Slf4j
public class MuseDarwinAnalyzeServiceImpl extends AbstractExcelAnalyze<MultipartFile, Map<String, List<MuseDarwinWriteFeatureData>>> {

    @Value("${output.path.summarize}")
    private String concordanceRatePath;

    @Value("${output.path.timeoutConclusion}")
    private String timeoutPath;

    /**
     * 一致率总结
     */
    private static final Map<String, BigDecimal> CONCORDANCE_RATIO_SUMMARIZE = new ConcurrentHashMap<>();

    @Override
    public Map<String, List<MuseDarwinWriteFeatureData>> analyzeExcel(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return Collections.emptyMap();
        }

        List<DarwinMuseDiffData> darwinMuseDiffData = new ArrayList<>();

        EasyExcel.read(file.getInputStream(), DarwinMuseDiffData.class, new ReadListener<DarwinMuseDiffData>() {
                    @Override
                    public void invoke(DarwinMuseDiffData data, AnalysisContext context) {
                        darwinMuseDiffData.add(data.rebuildData());
                    }

                    @Override
                    public void doAfterAllAnalysed(AnalysisContext context) {
                        log.info("Darwin Muse diff read finished!");
                    }
                })
                .sheet().doRead();

        log.info("darwinMuseDiffData.size:{}", darwinMuseDiffData.size());

        return darwinMuseDiffData.stream()
                /*.filter(rowData -> rowData.getDarwinTime().toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime().isAfter(LocalDateTime.of(2024, 8, 16, 16, 0)))*/
                .flatMap(rowData -> {
            String traceId = rowData.getTraceId();
            Date museTime = rowData.getMuseTime();
            Date darwinTime = rowData.getDarwinTime();
            JSONObject museDataJson = JSON.parseObject(rowData.getMuseData());
            JSONObject darwinDataJson = JSON.parseObject(rowData.getDarwinData());

            return FEATURE_NAME_MAP.keySet().stream()
                    .map(modelKey -> {
                        MuseDarwinWriteFeatureData museDarwinWriteFeatureData = new MuseDarwinWriteFeatureData();
                        museDarwinWriteFeatureData.setTraceId(traceId);
                        museDarwinWriteFeatureData.setDarwinName(FEATURE_NAME_MAP.get(modelKey));
                        museDarwinWriteFeatureData.setModelName(modelKey);
                        String darwinData = getOrDefaultString(darwinDataJson, modelKey);
                        String museData = getOrDefaultString(museDataJson, modelKey);
                        if (Objects.equals(darwinData, "empty") && Objects.equals(museData, "empty")) {
                            // 过滤掉
                            return null;
                        }
                        museDarwinWriteFeatureData.setDarwinValue(darwinData);
                        museDarwinWriteFeatureData.setMuseValue(museData);
                        museDarwinWriteFeatureData.setDiff(new BigDecimal(darwinData).compareTo(new BigDecimal(museData)) == 0);
                        museDarwinWriteFeatureData.setDarwinTime(darwinTime);
                        museDarwinWriteFeatureData.setMuseTime(museTime);
                        return museDarwinWriteFeatureData;
                    })
                    .filter(Objects::nonNull);
        }).collect(Collectors.groupingBy(MuseDarwinWriteFeatureData::getModelName));
    }

    @Override
    public void outputExcel(Map<String, List<MuseDarwinWriteFeatureData>> featureMapData) {
        log.info("start write excel...");

        if (featureMapData.isEmpty()) {
            log.error("无数据");
            return;
        }

        String timeoutDataPath = COMMON_PATH + timeoutPath;

        String concordanceRateOutputPath = COMMON_PATH + concordanceRatePath;

        // 计算每一个特征的一致率并且按照特征名称进行字典排序
        List<FeatureSummarizeSheet> summarizeList = consensusRateCalculation(featureMapData);

        // 分析每个特征的超时个数以及超时率
        List<FeatureTimeoutRate> timeoutRates = analyzeFeatureTimeoutRate(featureMapData);

        // 只输出不一致的数据
        Map<String, List<MuseDarwinWriteFeatureData>> diffData = extraProcess(featureMapData);

        try (ExcelWriter summarizeWriter = EasyExcel.write(timeoutDataPath).build()) {
            WriteSheet summarizeSheet = EasyExcel.writerSheet(0, "特征超时率总结")
                    .head(FeatureTimeoutRate.class)
                    .build();
            summarizeWriter.write(timeoutRates, summarizeSheet);
        }

        try (ExcelWriter summarizeWriter = EasyExcel.write(concordanceRateOutputPath).build()) {
            WriteSheet summarizeSheet = EasyExcel.writerSheet(0, "特征一致率总结")
                    .head(FeatureSummarizeSheet.class)
                    .build();
            summarizeWriter.write(summarizeList, summarizeSheet);
        }

        for (Map.Entry<String, List<MuseDarwinWriteFeatureData>> entry : diffData.entrySet()) {
            String modelKey = entry.getKey();
            List<MuseDarwinWriteFeatureData> data = entry.getValue();
            try (ExcelWriter excelWriter = EasyExcel.write(COMMON_PATH + modelKey + ".xlsx").build()) {
                BigDecimal rate = CONCORDANCE_RATIO_SUMMARIZE.get(modelKey);
                if (rate == null) {
                    continue;
                }
                MuseDarwinWriteFeatureData museDarwinWriteFeatureData = data.get(0);
                museDarwinWriteFeatureData.setConcordanceRate(new WriteCellData<>(rate));
                museDarwinWriteFeatureData.beautifulFormat();
                WriteSheet sheet = EasyExcel.writerSheet(0, canonicalSheetName(modelKey))
                        .head(MuseDarwinWriteFeatureData.class)
                        .build();
                excelWriter.write(data, sheet);
            }
        }

        log.info("write success !!!");
    }

    /**
     * 额外处理
     * 
     * @return data
     */
    private Map<String, List<MuseDarwinWriteFeatureData>> extraProcess(Map<String, List<MuseDarwinWriteFeatureData>> featureMapData) {
        return featureMapData.entrySet().stream()
                .map(entry -> new AbstractMap.SimpleEntry<>(
                        entry.getKey(),
                        entry.getValue().stream()
                                .filter(data -> new BigDecimal(data.getDarwinValue()).compareTo(new BigDecimal(data.getMuseValue())) != 0)
                                .collect(Collectors.toList())))
                .filter(entry -> !entry.getValue().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * 一致率计算
     *
     * @param featureMapData data
     */
    private List<FeatureSummarizeSheet> consensusRateCalculation(Map<String, List<MuseDarwinWriteFeatureData>> featureMapData) {
        List<FeatureSummarizeSheet> featureSummarizeSheetList = new ArrayList<>();
        for (Map.Entry<String, List<MuseDarwinWriteFeatureData>> entry : featureMapData.entrySet()) {
            String modelKey = entry.getKey();
            BigDecimal bigDecimal = analyzeConcordanceRatio(entry.getValue());
            CONCORDANCE_RATIO_SUMMARIZE.put(modelKey, bigDecimal);
        }
        for (Map.Entry<String, String> entry : FEATURE_NAME_MAP.entrySet()) {
            FeatureSummarizeSheet featureSummarize = new FeatureSummarizeSheet();
            String modelKey = entry.getKey();
            featureSummarize.setModelName(modelKey);
            featureSummarize.setDarwinName(entry.getValue());
            featureSummarize.setConcordanceRate(CONCORDANCE_RATIO_SUMMARIZE.get(modelKey));
            featureSummarizeSheetList.add(featureSummarize);
        }
        featureSummarizeSheetList.sort(Comparator.comparing(FeatureSummarizeSheet::getModelName));
        return featureSummarizeSheetList;
    }

    private BigDecimal analyzeConcordanceRatio(List<MuseDarwinWriteFeatureData> featureWriteFeatureData) {
        long concordanceCounts = featureWriteFeatureData.stream()
                .filter(datum -> new BigDecimal(datum.getDarwinValue()).compareTo(new BigDecimal(datum.getMuseValue())) == 0)
                .count();

        return BigDecimal.valueOf(concordanceCounts)
                .divide(BigDecimal.valueOf(featureWriteFeatureData.size()), 15, RoundingMode.HALF_UP);
    }

    private String getOrDefaultString(JSONObject json, String key) {
        return Optional.ofNullable(json.get(key)).map(Object::toString).orElse("empty");
    }

    /**
     * 超时率
     *
     * @param featureMapData data
     * @return 超时率
     */
    private List<FeatureTimeoutRate> analyzeFeatureTimeoutRate(Map<String, List<MuseDarwinWriteFeatureData>> featureMapData) {
        Map<String, FeatureTimeoutRate> timeoutRateMap = new HashMap<>();
        for (Map.Entry<String, List<MuseDarwinWriteFeatureData>> entry : featureMapData.entrySet()) {
            String modelName = entry.getKey();
            List<MuseDarwinWriteFeatureData> data = entry.getValue();
            int count = 0;
            for (MuseDarwinWriteFeatureData datum : data) {
                if (new BigDecimal(datum.getDarwinValue()).compareTo(new BigDecimal("-9999")) == 0
                        && new BigDecimal(datum.getMuseValue()).compareTo(new BigDecimal("-9999")) != 0) {
                    count++;
                }
            }
            BigDecimal rate = BigDecimal.valueOf(count).divide(BigDecimal.valueOf(data.size()), 6, RoundingMode.HALF_UP);
            timeoutRateMap.put(modelName, new FeatureTimeoutRate(FEATURE_NAME_MAP.get(modelName), modelName, count, data.size(), rate));
        }
        List<FeatureTimeoutRate> timeoutRates = new ArrayList<>();
        for (Map.Entry<String, String> entry : FEATURE_NAME_MAP.entrySet()) {
            String modelName = entry.getKey();
            FeatureTimeoutRate featureTimeoutRate = new FeatureTimeoutRate();
            featureTimeoutRate.setModelName(modelName);
            featureTimeoutRate.setDarwinName(FEATURE_NAME_MAP.get(modelName));
            FeatureTimeoutRate featureTimeoutRateSource = timeoutRateMap.get(modelName);
            if (featureTimeoutRateSource == null) {
                timeoutRates.add(featureTimeoutRate);
                continue;
            }
            BeanUtil.copyProperties(featureTimeoutRateSource, featureTimeoutRate);
            timeoutRates.add(featureTimeoutRate);
        }
        timeoutRates.sort(Comparator.comparing(FeatureTimeoutRate::getModelName));
        return timeoutRates;
    }

    @Override
    public String getAnalyzeOption() {
        return "muse-darwin";
    }
}
