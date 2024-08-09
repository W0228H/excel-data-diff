package com.whao.excel.demo;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.whao.excel.domain.read.DarwinModelDiffData;
import com.whao.excel.domain.read.DarwinMuseDataDto;
import com.whao.excel.domain.read.InputFeatureDataDto;
import com.whao.excel.domain.write.FeatureWriteFeatureData;
import com.whao.excel.listener.DarwinModelDiffListener;
import com.whao.excel.listener.DarwinMuseDiffListener;
import com.whao.excel.listener.InputFeatureListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class Demo {

    private static final String path1 = "/Users/didi/Desktop/获取darwin和muse关于特征的具体对比数据.csv";

    /**
     * 特征映射
     * /Users/didi/Desktop/inputfeature.csv
     * /Users/whao/Desktop/inputfeature.csv
     */
    private static final String path2 = "/Users/whao/Desktop/inputfeature.csv";

    private static final String path3 = "/Users/whao/Desktop/test_darwin_model.xlsx";

    public static DarwinModelDiffListener analyzeDarwinModelDiffData(String path) {
        DarwinModelDiffListener darwinModelDiffListener = new DarwinModelDiffListener();
        EasyExcel.read(path, DarwinModelDiffData.class, darwinModelDiffListener)
                .sheet().doRead();
        return darwinModelDiffListener;
    }

    public static void analyzeDarwinMuseDataDto(String path) {
        EasyExcel.read(path, DarwinMuseDataDto.class, new DarwinMuseDiffListener())
                .sheet().doRead();
    }

    public static InputFeatureListener analyzeInputFeatureData(String path) {
        InputFeatureListener inputFeatureListener = new InputFeatureListener();
        EasyExcel.read(path, InputFeatureDataDto.class, inputFeatureListener)
                .sheet().doRead();
        return inputFeatureListener;
    }

    public static void analyzeAndCompare() {
        InputFeatureListener inputFeatureListener = analyzeInputFeatureData(path2);
        // 映射 k: model name, v: feature name
        Map<String, String> featureNameMap = inputFeatureListener.getFeatureNameMap();
        DarwinModelDiffListener darwinModelDiffListener = analyzeDarwinModelDiffData(path3);
        List<DarwinModelDiffData> data = darwinModelDiffListener.getDataList();
        readData(data, featureNameMap);
    }

    public static void readData(List<DarwinModelDiffData> data,  Map<String, String> featureNameMap) {
        // 一口气存储所有数据
        Map<String, List<FeatureWriteFeatureData>> allFeaturesMapData = new HashMap<>();

        data.forEach(rowData -> {
            String traceId = rowData.getTraceId();
            String modelData = rowData.getModelData();
            String darwinData = rowData.getDarwinData();
            Date sameTime = rowData.getSameTime();

            JSONObject modelDataJson = JSON.parseObject(modelData);
            JSONObject darwinDataJson = JSON.parseObject(darwinData);

            for (String darwinKey : darwinDataJson.keySet()) {
                FeatureWriteFeatureData featureWriteFeatureData = new FeatureWriteFeatureData();
                featureWriteFeatureData.setFeatureName(darwinKey);
                featureWriteFeatureData.setTraceId(traceId);
                featureWriteFeatureData.setTime(sameTime);
                // darwin的hive中对应的特征值
                Object darwinValue = darwinDataJson.get(darwinKey);
                // 模型hive中对应的特征值
                Object modelValue = modelDataJson.get(featureNameMap.getOrDefault(darwinKey, ""));
                featureWriteFeatureData.setDarwinValue(darwinValue == null ? "null" : darwinValue.toString());
                featureWriteFeatureData.setModelValue(modelValue == null ? "null" : modelValue.toString());

                List<FeatureWriteFeatureData> allFeaturesData = allFeaturesMapData.getOrDefault(darwinKey, new ArrayList<>());
                allFeaturesData.add(featureWriteFeatureData);
                allFeaturesMapData.put(darwinKey, allFeaturesData);
            }
        });

        log.info("allFeaturesMapData:{}", allFeaturesMapData);
        writeData(allFeaturesMapData);
    }

    /**
     * 写数据
     */
    public static void writeData(Map<String, List<FeatureWriteFeatureData>> allFeaturesMapData) {
        String endPointPath = "/Users/whao/Desktop/result.xlsx";
        try (ExcelWriter excelWriter = EasyExcel.write(endPointPath, FeatureWriteFeatureData.class).build()) {
            int count = 0;
            for (String key : allFeaturesMapData.keySet()) {
                // 规范化 sheet 名称
                String sheetName = key.length() > 31 ? key.substring(0, 31) : key.replaceAll("[\\[\\]/\\\\?*]", "_");

                // 打印 sheet 名称和数据条数，用于调试
                System.out.println("Creating sheet: " + sheetName + " with " + allFeaturesMapData.get(key).size() + " records.");

                WriteSheet sheet = EasyExcel.writerSheet(count, sheetName).build();
                excelWriter.write(allFeaturesMapData.get(key), sheet);
                count++;
            }
        }
    }


    public static void main(String[] args) {
        analyzeAndCompare();
    }
}
