package com.whao.excel.service.impl;

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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
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
    private static final Map<String, BigDecimal> CONCORDANCE_RATIO_SUMMARIZE = new LinkedHashMap<>();

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
                .filter(rowData -> rowData.getDarwinTime().toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime().isAfter(LocalDateTime.of(2024, 8, 14, 14, 0)))
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
                        museDarwinWriteFeatureData.setDarwinValue(getOrDefaultString(darwinDataJson, modelKey));
                        museDarwinWriteFeatureData.setMuseValue(getOrDefaultString(museDataJson, modelKey));
                        museDarwinWriteFeatureData.setDarwinTime(darwinTime);
                        museDarwinWriteFeatureData.setMuseTime(museTime);
                        return museDarwinWriteFeatureData;
                    });
        }).collect(Collectors.groupingBy(MuseDarwinWriteFeatureData::getModelName));
    }

    @Override
    public void outputExcel(Map<String, List<MuseDarwinWriteFeatureData>> featureMapData) {
        log.info("start write excel...");

        String timeoutDataPath = COMMON_PATH + concordanceRatePath;

        String concordanceRateOutputPath = COMMON_PATH + timeoutPath;

        // 计算每一个特征的一致率并且按照特征名称进行字典排序
        List<FeatureSummarizeSheet> summarizeList = consensusRateCalculation(featureMapData);

        // 分析每个特征的超时个数以及超时率
        List<FeatureTimeoutRate> timeoutRates = analyzeFeatureTimeoutRate(featureMapData);

        // 只输出不一致的数据
        Map<String, List<MuseDarwinWriteFeatureData>> diffData = featureMapData.entrySet().stream()
                .map(entry -> new AbstractMap.SimpleEntry<>(
                        entry.getKey(),
                        entry.getValue().stream()
                                .filter(data -> !Objects.equals(data.getDarwinValue(), "empty") || !Objects.equals(data.getMuseValue(), "empty"))
                                .filter(data -> new BigDecimal(data.getDarwinValue()).compareTo(new BigDecimal(data.getMuseValue())) != 0)
                                .collect(Collectors.toList())))
                .filter(entry -> !entry.getValue().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

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
     * 一致率计算
     *
     * @param featureMapData data
     */
    private List<FeatureSummarizeSheet> consensusRateCalculation(Map<String, List<MuseDarwinWriteFeatureData>> featureMapData) {
        return featureMapData.entrySet().stream()
                .map(entry -> {
                    String modelKey = entry.getKey();
                    BigDecimal bigDecimal = analyzeConcordanceRatio(entry.getValue());
                    CONCORDANCE_RATIO_SUMMARIZE.put(modelKey, bigDecimal);
                    return new FeatureSummarizeSheet(FEATURE_NAME_MAP.get(modelKey), modelKey, bigDecimal);
                })
                .sorted(Comparator.comparing(FeatureSummarizeSheet::getModelName))
                .collect(Collectors.toList());
    }

    private BigDecimal analyzeConcordanceRatio(List<MuseDarwinWriteFeatureData> featureWriteFeatureData) {
        if (isEmptyFeature(featureWriteFeatureData)) {
            return null;
        }

        long concordanceCounts = featureWriteFeatureData.stream()
                .filter(datum -> new BigDecimal(datum.getDarwinValue()).compareTo(new BigDecimal(datum.getMuseValue())) == 0)
                .count();

        return BigDecimal.valueOf(concordanceCounts)
                .divide(BigDecimal.valueOf(featureWriteFeatureData.size()), 15, RoundingMode.HALF_UP);
    }

    private boolean isEmptyFeature(List<MuseDarwinWriteFeatureData> featureWriteFeatureData) {
        return featureWriteFeatureData.stream().noneMatch(datum -> !Objects.equals(datum.getDarwinValue(), "empty") && !Objects.equals(datum.getMuseValue(), "empty"));
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
        List<FeatureTimeoutRate> timeoutRates = new ArrayList<>();
        for (Map.Entry<String, List<MuseDarwinWriteFeatureData>> entry : featureMapData.entrySet()) {
            FeatureTimeoutRate featureTimeoutRate = new FeatureTimeoutRate();
            String modelName = entry.getKey();
            featureTimeoutRate.setModelName(modelName);
            featureTimeoutRate.setDarwinName(FEATURE_NAME_MAP.get(modelName));
            List<MuseDarwinWriteFeatureData> data = entry.getValue();
            long count = 0;

            if (isEmptyFeature(data)) {
                featureTimeoutRate.setTimeoutCounts(0L);
                featureTimeoutRate.setTotalCounts(0);
                timeoutRates.add(featureTimeoutRate);
                continue;
            }
            for (MuseDarwinWriteFeatureData datum : data) {
                try {
                    if (new BigDecimal(datum.getDarwinValue()).compareTo(new BigDecimal("-9999")) == 0
                            && new BigDecimal(datum.getMuseValue()).compareTo(new BigDecimal("-9999")) != 0) {
                        count++;
                    }
                } catch (Exception e) {
                    String darwinValue = datum.getDarwinValue();
                    String museValue = datum.getMuseValue();
                    log.error("darwinValue:{}, museValue:{}", darwinValue, museValue);
                    System.exit(0);
                }
            }
            featureTimeoutRate.setTimeoutCounts(count);
            featureTimeoutRate.setTotalCounts(data.size());
            featureTimeoutRate.setTimeoutRate(BigDecimal.valueOf(count).divide(BigDecimal.valueOf(data.size()), 6, RoundingMode.HALF_UP));
            timeoutRates.add(featureTimeoutRate);
        }
        return timeoutRates;
    }

    @Override
    public String getAnalyzeOption() {
        return "muse-darwin";
    }
}
