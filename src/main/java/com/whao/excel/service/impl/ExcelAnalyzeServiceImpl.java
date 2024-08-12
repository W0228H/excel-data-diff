package com.whao.excel.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.whao.excel.domain.read.DarwinModelDiffData;
import com.whao.excel.domain.write.FeatureWriteFeatureData;
import com.whao.excel.service.AbstractExcelAnalyze;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * @author xiongwh
 * @date 2024/8/10 1:10 PM
 */
@Service
@Slf4j
public class ExcelAnalyzeServiceImpl extends AbstractExcelAnalyze<MultipartFile, Map<String, List<FeatureWriteFeatureData>>> {

    @Value("${output.path}")
    private String outputPath;

    /**
     * 一致率总结
     */
    private static final Map<String, Double> CONCORDANCE_RATIO_SUMMARIZE = new LinkedHashMap<>();

    @Override
    public Map<String, List<FeatureWriteFeatureData>> analyzeExcel(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return Collections.emptyMap();
        }

        List<DarwinModelDiffData> darwinModelDiffData = new ArrayList<>();

        EasyExcel.read(file.getInputStream(), DarwinModelDiffData.class, new ReadListener<DarwinModelDiffData>() {
                    @Override
                    public void invoke(DarwinModelDiffData data, AnalysisContext context) {
                        darwinModelDiffData.add(data.rebuildData());
                    }
                    @Override
                    public void doAfterAllAnalysed(AnalysisContext context) {
                        log.info("Darwin model diff read finished!");
                    }
                })
                .sheet().doRead();

        return darwinModelDiffData.stream().flatMap(rowData -> {
            String traceId = rowData.getTraceId();
            Date sameTime = rowData.getSameTime();
            JSONObject modelDataJson = JSON.parseObject(rowData.getModelData());
            JSONObject darwinDataJson = JSON.parseObject(rowData.getDarwinData());
            return darwinDataJson.keySet().stream().map(darwinKey -> {
                FeatureWriteFeatureData featureWriteFeatureData = new FeatureWriteFeatureData();
                featureWriteFeatureData.setFeatureName(darwinKey);
                featureWriteFeatureData.setTraceId(traceId);
                featureWriteFeatureData.setTime(sameTime);
                featureWriteFeatureData.setDarwinValue(getOrDefaultString(darwinDataJson, darwinKey));
                featureWriteFeatureData.setModelValue(getOrDefaultString(modelDataJson, FEATURE_NAME_MAP.getOrDefault(darwinKey, "")));
                return featureWriteFeatureData;
            });
        }).collect(Collectors.groupingBy(FeatureWriteFeatureData::getFeatureName));
    }

    private BigDecimal analyzeConcordanceRatio(List<FeatureWriteFeatureData> featureWriteFeatureData) {
        long concordanceCounts = featureWriteFeatureData.stream()
                .filter(datum -> Objects.equals(datum.getDarwinValue(), datum.getModelValue()))
                .count();

        return BigDecimal.valueOf(concordanceCounts)
                .divide(BigDecimal.valueOf(featureWriteFeatureData.size()), 4, RoundingMode.HALF_UP);
    }

    private String getOrDefaultString(JSONObject json, String key) {
        return Optional.ofNullable(json.get(key)).map(Object::toString).orElse("null");
    }

    @Override
    public void outputExcel(Map<String, List<FeatureWriteFeatureData>> featureMapData) {
        outputPath = System.getProperty("user.home") + "/Desktop/" + outputPath;

        List<FeatureWriteFeatureData.FeatureSummarizeSheet> summarizeList = featureMapData.entrySet().stream()
                .map(entry -> new AbstractMap.SimpleEntry<>(
                        entry.getKey(),
                        analyzeConcordanceRatio(entry.getValue())
                ))
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(entry -> {
                    FeatureWriteFeatureData.FeatureSummarizeSheet summarizeSheet = new FeatureWriteFeatureData.FeatureSummarizeSheet();
                    summarizeSheet.setFeatureName(entry.getKey());
                    summarizeSheet.setConcordanceRate(entry.getValue().doubleValue());
                    CONCORDANCE_RATIO_SUMMARIZE.put(entry.getKey(), entry.getValue().doubleValue());
                    return summarizeSheet;
                })
                .collect(Collectors.toList());

        try (ExcelWriter excelWriter = EasyExcel.write(outputPath).build()) {
            WriteSheet summarizeSheet = EasyExcel.writerSheet(0, "特征一致率总结")
                    .head(FeatureWriteFeatureData.FeatureSummarizeSheet.class)
                    .build();
            excelWriter.write(summarizeList, summarizeSheet);

            AtomicInteger sheetIndex = new AtomicInteger(1);
            featureMapData.forEach((key, data) -> {
                WriteSheet sheet = EasyExcel.writerSheet(sheetIndex.getAndIncrement(), canonicalSheetName(key))
                        .head(FeatureWriteFeatureData.class)
                        .build();
                excelWriter.write(data, sheet);
            });
        }
    }
}
