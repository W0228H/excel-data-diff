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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * @author xiongwh
 * @date 2024/8/12 10:03 PM
 */
@Service
@Slf4j
public class MuseDarwinAnalyzeServiceImpl extends AbstractExcelAnalyze<MultipartFile, Map<String, List<MuseDarwinWriteFeatureData>>> {

    @Value("${output.path.summarize}")
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

        log.info("darwinMuseDiffData.size:{}", darwinMuseDiffData.size());

        return darwinMuseDiffData.stream().flatMap(rowData -> {
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
                    }).filter(data -> !Objects.equals(data.getDarwinValue(), "empty") && !Objects.equals(data.getMuseValue(), "empty"));
        }).collect(Collectors.groupingBy(MuseDarwinWriteFeatureData::getModelName));
    }

    private BigDecimal analyzeConcordanceRatio(List<MuseDarwinWriteFeatureData> featureWriteFeatureData) {
        long concordanceCounts = featureWriteFeatureData.stream()
                .filter(datum -> Objects.equals(datum.getDarwinValue(), datum.getMuseValue()))
                .count();

        return BigDecimal.valueOf(concordanceCounts)
                .divide(BigDecimal.valueOf(featureWriteFeatureData.size()), 15, RoundingMode.HALF_UP);
    }

    private String getOrDefaultString(JSONObject json, String key) {
        return Optional.ofNullable(json.get(key)).map(Object::toString).orElse("empty");
    }

    @Override
    public void outputExcel(Map<String, List<MuseDarwinWriteFeatureData>> featureMapData) {
        log.info("start write excel...");
        String path = System.getProperty("user.home") + "/Desktop/" + outputPath;
        String allExcelPath = System.getProperty("user.home") + "/Desktop/特征一致性详情/";

        // 计算每一个特征的一致率并且按照特征名称进行字典排序
        List<FeatureSummarizeSheet> summarizeList = consensusRateCalculation(featureMapData);

        try (ExcelWriter summarizeWriter = EasyExcel.write(path).build()) {
            WriteSheet summarizeSheet = EasyExcel.writerSheet(0, "特征一致率总结")
                    .head(FeatureSummarizeSheet.class)
                    .build();
            summarizeWriter.write(summarizeList, summarizeSheet);
        }

        for (Map.Entry<String, List<MuseDarwinWriteFeatureData>> entry : featureMapData.entrySet()) {
            String modelKey = entry.getKey();
            List<MuseDarwinWriteFeatureData> data = entry.getValue();
            try (ExcelWriter excelWriter = EasyExcel.write(allExcelPath + modelKey + ".xlsx").build()) {
                BigDecimal rate = CONCORDANCE_RATIO_SUMMARIZE.get(modelKey);
                MuseDarwinWriteFeatureData museDarwinWriteFeatureData = data.get(0);
                museDarwinWriteFeatureData.setConcordanceRate(new WriteCellData<>(rate));
                museDarwinWriteFeatureData.beautifulFormat();
                WriteSheet sheet = EasyExcel.writerSheet(0, modelKey)
                        .head(MuseDarwinWriteFeatureData.class)
                        .build();
                excelWriter.write(data, sheet);
            }
        }

        log.info("write success !!!");
    }

    @Override
    public String getAnalyzeOption() {
        return "muse-darwin";
    }

    /**
     * 一致率计算
     *
     * @param featureMapData data
     */
    private List<FeatureSummarizeSheet> consensusRateCalculation(Map<String, List<MuseDarwinWriteFeatureData>> featureMapData) {
        return featureMapData.entrySet().stream()
                .map(entry -> {
                    BigDecimal bigDecimal = analyzeConcordanceRatio(entry.getValue());
                    CONCORDANCE_RATIO_SUMMARIZE.put(entry.getKey(), bigDecimal);
                    return new FeatureSummarizeSheet(entry.getKey(), bigDecimal);
                })
                .sorted(Comparator.comparing(FeatureSummarizeSheet::getFeatureName))
                .collect(Collectors.toList());
    }
}
