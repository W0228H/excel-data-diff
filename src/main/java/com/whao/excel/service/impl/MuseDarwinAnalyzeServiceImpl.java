package com.whao.excel.service.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.metadata.data.WriteCellData;
import com.alibaba.excel.read.listener.ReadListener;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.BiMap;
import com.whao.excel.domain.common.FeatureSummarizeSheet;
import com.whao.excel.domain.read.DarwinModelDiffData;
import com.whao.excel.domain.read.DarwinMuseDiffData;
import com.whao.excel.domain.write.FeatureWriteFeatureData;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * @author xiongwh
 * @date 2024/8/12 10:03 PM
 */
@Service
@Slf4j
public class MuseDarwinAnalyzeServiceImpl extends AbstractExcelAnalyze<MultipartFile, Map<String, List<MuseDarwinWriteFeatureData>>> {

    @Value("${output.path}")
    private String outputPath;

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

        BiMap<String, String> inverse = FEATURE_NAME_MAP.inverse();
        return darwinMuseDiffData.stream()
                .filter(rowData -> rowData.getDarwinTime().toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime().isAfter(LocalDateTime.of(2024, 8, 12, 19, 30)))
                .flatMap(rowData -> {
            String traceId = rowData.getTraceId();
            Date museTime = rowData.getMuseTime();
            Date darwinTime = rowData.getDarwinTime();
            JSONObject museDataJson = JSON.parseObject(rowData.getMuseData());
            JSONObject darwinDataJson = JSON.parseObject(rowData.getDarwinData());

            return inverse.keySet().stream().map(modelKey -> {
                        MuseDarwinWriteFeatureData museDarwinWriteFeatureData = new MuseDarwinWriteFeatureData();
                        museDarwinWriteFeatureData.setTraceId(traceId);
                        museDarwinWriteFeatureData.setFeatureName(inverse.get(modelKey));
                        museDarwinWriteFeatureData.setDarwinValue(getOrDefaultString(darwinDataJson, modelKey));
                        museDarwinWriteFeatureData.setMuseValue(getOrDefaultString(museDataJson, modelKey));
                        museDarwinWriteFeatureData.setDarwinTime(darwinTime);
                        museDarwinWriteFeatureData.setMuseTime(museTime);
                        return museDarwinWriteFeatureData;
                    });
        }).collect(Collectors.groupingBy(MuseDarwinWriteFeatureData::getFeatureName));
    }

    private BigDecimal analyzeConcordanceRatio(List<MuseDarwinWriteFeatureData> featureWriteFeatureData) {
        long concordanceCounts = featureWriteFeatureData.stream()
                .filter(datum -> Objects.equals(datum.getDarwinValue(), datum.getMuseValue()))
                .count();

        return BigDecimal.valueOf(concordanceCounts)
                .divide(BigDecimal.valueOf(featureWriteFeatureData.size()), 4, RoundingMode.HALF_UP);
    }

    private String getOrDefaultString(JSONObject json, String key) {
        return Optional.ofNullable(json.get(key)).map(Object::toString).orElse("null");
    }

    @Override
    public void outputExcel(Map<String, List<MuseDarwinWriteFeatureData>> featureMapData) {
        log.info("start write excel...");
        String path = System.getProperty("user.home") + "/Desktop/" + outputPath;

        List<FeatureSummarizeSheet> summarizeList = featureMapData.entrySet().stream()
                .map(entry -> {
                    BigDecimal concordanceRate = analyzeConcordanceRatio(entry.getValue());
                    CONCORDANCE_RATIO_SUMMARIZE.put(entry.getKey(), concordanceRate);
                    return new FeatureSummarizeSheet(entry.getKey(), concordanceRate);
                })
                .sorted(Comparator.comparing(FeatureSummarizeSheet::getConcordanceRate).reversed())
                .collect(Collectors.toList());

        try (ExcelWriter excelWriter = EasyExcel.write(path).build()) {
            WriteSheet summarizeSheet = EasyExcel.writerSheet(0, "特征一致率总结")
                    .head(FeatureSummarizeSheet.class)
                    .build();
            excelWriter.write(summarizeList, summarizeSheet);

            AtomicInteger sheetIndex = new AtomicInteger(1);
            featureMapData.forEach((key, data) -> {
                BigDecimal concordanceRate = CONCORDANCE_RATIO_SUMMARIZE.get(key);
                MuseDarwinWriteFeatureData featureWriteFeatureData = data.get(0);
                featureWriteFeatureData.setConcordanceRate(new WriteCellData<>(concordanceRate));
                featureWriteFeatureData.beautifulFormat();
                WriteSheet sheet = EasyExcel.writerSheet(sheetIndex.getAndIncrement(), canonicalSheetName(key))
                        .head(MuseDarwinWriteFeatureData.class)
                        .build();
                excelWriter.write(data, sheet);
            });
        }
    }

    @Override
    public String getAnalyzeOption() {
        return "muse-darwin";
    }
}
