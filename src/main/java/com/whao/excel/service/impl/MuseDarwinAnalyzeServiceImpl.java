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
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
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

    /**
     * 一致率总结
     */
    private static final Map<String, BigDecimal> CONCORDANCE_RATIO_SUMMARIZE = new ConcurrentHashMap<>();

    /**
     * 超时率总结
     */
    private static final Map<String, Triple<Long, Integer, BigDecimal>> MUSE_FAIL_RATIO = new ConcurrentHashMap<>();

    /**
     * 时间分区内个数
     */
    private static final Map<String, Pair<Long, BigDecimal>> TIME_RANGE_RATIO = new ConcurrentHashMap<>();

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
                        if (Objects.equals(darwinData, "") && Objects.equals(museData, "")) {
                            // 过滤掉
                            return null;
                        }
                        museDarwinWriteFeatureData.setDarwinValue(darwinData);
                        museDarwinWriteFeatureData.setMuseValue(museData);
                        try {
                            museDarwinWriteFeatureData.setDiff(new BigDecimal(darwinData).compareTo(new BigDecimal(museData)) == 0);
                        } catch (Exception e) {
                            museDarwinWriteFeatureData.setDiff(false);
                        }
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

        String concordanceRateOutputPath = COMMON_PATH + concordanceRatePath;

        // 计算每一个特征的一致率、超时个数以及超时率并且按照特征名称进行字典排序
        List<FeatureSummarizeSheet> summarizeList = analyzeFeatureRatio(featureMapData);

        // 只输出不一致的数据
        Map<String, List<MuseDarwinWriteFeatureData>> diffData = extraProcess(featureMapData);

        try (ExcelWriter summarizeWriter = EasyExcel.write(concordanceRateOutputPath).build()) {
            WriteSheet summarizeSheet = EasyExcel.writerSheet(0, "特征总结")
                    .head(FeatureSummarizeSheet.class)
                    .build();
            summarizeWriter.write(summarizeList, summarizeSheet);
        }

        for (Map.Entry<String, List<MuseDarwinWriteFeatureData>> entry : diffData.entrySet()) {
            String modelKey = entry.getKey();
            List<MuseDarwinWriteFeatureData> data = entry.getValue();
            try (ExcelWriter excelWriter = EasyExcel.write(COMMON_PATH + modelKey + ".xlsx").build()) {
                BigDecimal rate = CONCORDANCE_RATIO_SUMMARIZE.get(modelKey);
                Triple<Long, Integer, BigDecimal> timeoutRate = MUSE_FAIL_RATIO.get(modelKey);
                if (rate == null || timeoutRate == null) {
                    continue;
                }
                MuseDarwinWriteFeatureData museDarwinWriteFeatureData = data.get(0);
                museDarwinWriteFeatureData.setConcordanceRate(new WriteCellData<>(rate));
                museDarwinWriteFeatureData.setMuseFailRate(new WriteCellData<>(timeoutRate.getRight()));
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
                                .filter(data -> !data.isDiff())
                                .collect(Collectors.toList())))
                .filter(entry -> !entry.getValue().isEmpty())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private BigDecimal analyzeConcordanceRatio(List<MuseDarwinWriteFeatureData> featureWriteFeatureData) {
        long concordanceCounts = featureWriteFeatureData.stream()
                .filter(MuseDarwinWriteFeatureData::isDiff)
                .count();

        return BigDecimal.valueOf(concordanceCounts)
                .divide(BigDecimal.valueOf(featureWriteFeatureData.size()), 15, RoundingMode.HALF_UP);
    }

    private Triple<Long, Integer, BigDecimal> analyzeMuseFailRatio(List<MuseDarwinWriteFeatureData> featureWriteFeatureData) {
        int size = featureWriteFeatureData.size();
        long count = featureWriteFeatureData.stream()
                .filter(datum -> !Objects.equals(datum.getDarwinValue(), "") && !Objects.equals(datum.getMuseValue(), ""))
                .filter(datum -> new BigDecimal(datum.getDarwinValue()).compareTo(new BigDecimal("-9999")) != 0
                        && new BigDecimal(datum.getMuseValue()).compareTo(new BigDecimal("-9999")) == 0)
                .count();

        return Triple.of(count, size, BigDecimal.valueOf(count)
                .divide(BigDecimal.valueOf(size), 15, RoundingMode.HALF_UP));
    }

    private Pair<Long, BigDecimal> analyzeSubZoneRatio(List<MuseDarwinWriteFeatureData> featureWriteFeatureData) {
        long count = featureWriteFeatureData.stream()
                .filter(datum -> Objects.nonNull(MODEL_DATASOURCE_MAP.get(datum.getModelName())))
                .filter(datum -> {
                    String timeRange = DATASOURCE_TIME_MAP.get(MODEL_DATASOURCE_MAP.get(datum.getModelName()));
                    try {
                        return isInTimeRange(datum.getMuseTime(), timeRange);
                    } catch (ParseException e) {
                        throw new RuntimeException(e);
                    }
                }).count();
        return Pair.of(count, BigDecimal.valueOf(count)
                .divide(BigDecimal.valueOf(featureWriteFeatureData.size()), 15, RoundingMode.HALF_UP));
    }

    private String getOrDefaultString(JSONObject json, String key) {
        return Optional.ofNullable(json.get(key)).map(Object::toString).orElse("");
    }

    private boolean isInTimeRange(Date museTime, String time) throws ParseException {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-M-d HH:mm");
        Date startTime = sdf.parse(time);
        Date endTime = new Date(startTime.getTime() + TimeUnit.HOURS.toMillis(1));
        return !museTime.before(startTime) && !museTime.after(endTime);
    }

    /**
     * 分析特征的一致率和超时率
     *
     * @param featureMapData data
     * @return List<FeatureSummarizeSheet>
     */
    private List<FeatureSummarizeSheet> analyzeFeatureRatio(Map<String, List<MuseDarwinWriteFeatureData>> featureMapData) {
        List<FeatureSummarizeSheet> featureSummarizes = new ArrayList<>();
        for (Map.Entry<String, List<MuseDarwinWriteFeatureData>> entry : featureMapData.entrySet()) {
            String modelName = entry.getKey();
            List<MuseDarwinWriteFeatureData> data = entry.getValue();
            BigDecimal concordanceRate = analyzeConcordanceRatio(data);
            Triple<Long, Integer, BigDecimal> timeoutRate = analyzeMuseFailRatio(data);
            Pair<Long, BigDecimal> timeRangeRate = analyzeSubZoneRatio(data);
            CONCORDANCE_RATIO_SUMMARIZE.put(modelName, concordanceRate);
            MUSE_FAIL_RATIO.put(modelName, timeoutRate);
            TIME_RANGE_RATIO.put(modelName, timeRangeRate);
        }

        for (Map.Entry<String, String> entry : FEATURE_NAME_MAP.entrySet()) {
            FeatureSummarizeSheet featureSummarize = new FeatureSummarizeSheet();
            String modelKey = entry.getKey();
            featureSummarize.setModelName(modelKey);
            featureSummarize.setDarwinName(entry.getValue());
            featureSummarize.setConcordanceRate(CONCORDANCE_RATIO_SUMMARIZE.get(modelKey));
            Optional.ofNullable(MUSE_FAIL_RATIO.get(modelKey)).ifPresent(rateTriple -> {
                featureSummarize.setMuseFailCount(rateTriple.getLeft());
                featureSummarize.setMuseFailRate(rateTriple.getRight());
                featureSummarize.setQueryCount(rateTriple.getMiddle());
            });
            Optional.ofNullable(TIME_RANGE_RATIO.get(modelKey)).ifPresent(ratePair -> {
                featureSummarize.setMuseTimeRangeCount(ratePair.getKey());
                featureSummarize.setMuseTimeRangeErrRate(ratePair.getValue());
            });
            featureSummarizes.add(featureSummarize);
        }
        featureSummarizes.sort(Comparator.comparing(FeatureSummarizeSheet::getModelName));
        return featureSummarizes;
    }

    @Override
    public String getAnalyzeOption() {
        return "muse-darwin";
    }
}
