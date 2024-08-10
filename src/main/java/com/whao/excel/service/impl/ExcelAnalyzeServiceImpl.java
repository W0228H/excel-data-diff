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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    @Override
    public Map<String, List<FeatureWriteFeatureData>> analyzeExcel(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return Collections.emptyMap();
        }

        List<DarwinModelDiffData> darwinModelDiffData = new ArrayList<>();

        EasyExcel.read(file.getInputStream(), DarwinModelDiffData.class, new ReadListener<DarwinModelDiffData>() {
                    @Override
                    public void invoke(DarwinModelDiffData data, AnalysisContext context) {
                        data.rebuildData();
                        darwinModelDiffData.add(data);
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

    private String getOrDefaultString(JSONObject json, String key) {
        return Optional.ofNullable(json.get(key)).map(Object::toString).orElse("null");
    }

    @Override
    public void outputExcel(Map<String, List<FeatureWriteFeatureData>> featureMapData) {
        try (ExcelWriter excelWriter = EasyExcel.write(outputPath, FeatureWriteFeatureData.class).build()) {
            int sheetIndex = 0;
            for (Map.Entry<String, List<FeatureWriteFeatureData>> entry : featureMapData.entrySet()) {
                String key = entry.getKey();
                List<FeatureWriteFeatureData> data = entry.getValue();
                // 创建 WriteSheet 实例并写入数据
                WriteSheet sheet = EasyExcel.writerSheet(sheetIndex++, canonicalSheetName(key)).build();
                excelWriter.write(data, sheet);
            }
        }
    }
}
