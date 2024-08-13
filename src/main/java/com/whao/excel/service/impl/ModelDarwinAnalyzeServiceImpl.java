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
public class ModelDarwinAnalyzeServiceImpl extends AbstractExcelAnalyze<MultipartFile, Map<String, List<FeatureWriteFeatureData>>> {

    @Value("${output.path}")
    private String outputPath;

    /**
     * 一致率总结
     */
    private static final Map<String, BigDecimal> CONCORDANCE_RATIO_SUMMARIZE = new LinkedHashMap<>();

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
        log.info("start write excel...");
        outputPath = System.getProperty("user.home") + "/Desktop/" + outputPath;

        List<FeatureSummarizeSheet> summarizeList = featureMapData.entrySet().stream()
                .map(entry -> {
                    BigDecimal concordanceRate = analyzeConcordanceRatio(entry.getValue());
                    CONCORDANCE_RATIO_SUMMARIZE.put(entry.getKey(), concordanceRate);
                    return new FeatureSummarizeSheet(entry.getKey(), concordanceRate);
                })
                .sorted(Comparator.comparing(FeatureSummarizeSheet::getConcordanceRate).reversed())
                .collect(Collectors.toList());

        try (ExcelWriter excelWriter = EasyExcel.write(outputPath).build()) {
            WriteSheet summarizeSheet = EasyExcel.writerSheet(0, "特征一致率总结")
                    .head(FeatureSummarizeSheet.class)
                    .build();
            excelWriter.write(summarizeList, summarizeSheet);

            AtomicInteger sheetIndex = new AtomicInteger(1);
            featureMapData.forEach((key, data) -> {
                BigDecimal concordanceRate = CONCORDANCE_RATIO_SUMMARIZE.get(key);
                FeatureWriteFeatureData featureWriteFeatureData = data.get(0);
                featureWriteFeatureData.setConcordanceRate(new WriteCellData<>(concordanceRate));
                featureWriteFeatureData.beautifulFormat();
                WriteSheet sheet = EasyExcel.writerSheet(sheetIndex.getAndIncrement(), canonicalSheetName(key))
                        .head(FeatureWriteFeatureData.class)
                        .build();
                excelWriter.write(data, sheet);
            });
        }
    }

    @Override
    public String getAnalyzeOption() {
        return "model-darwin";
    }
}