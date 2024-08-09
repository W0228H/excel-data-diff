package com.whao.excel.demo;

import com.alibaba.excel.EasyExcel;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.whao.excel.domain.read.DarwinModelDiffData;
import com.whao.excel.domain.read.DarwinMuseDataDto;
import com.whao.excel.domain.read.InputFeatureDataDto;
import com.whao.excel.listener.DarwinModelDiffListener;
import com.whao.excel.listener.DarwinMuseDiffListener;
import com.whao.excel.listener.InputFeatureListener;

import java.util.Date;
import java.util.List;
import java.util.Map;

public class Demo {

    private static final String path1 = "/Users/didi/Desktop/获取darwin和muse关于特征的具体对比数据.csv";

    /**
     * 特征映射
     */
    private static final String path2 = "/Users/didi/Desktop/inputfeature.csv";

    private static final String path3 = "/Users/didi/Desktop/darwin对比model数据.csv";

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
        data.forEach(rowData -> {
            String traceId = rowData.getTraceId();
            String modelData = rowData.getModelData();
            String darwinData = rowData.getDarwinData();
            Date sameTime = rowData.getSameTime();

            JSONObject modelDataJson = JSON.parseObject(modelData);
            JSONObject darwinDataJson = JSON.parseObject(darwinData);

            for (String darwinKey : darwinDataJson.keySet()) {
                // darwin的hive中对应的特征值
                Object darwinValue = darwinDataJson.get(darwinKey);

                // 模型hive中对应的特征值
                Object modelValue = modelDataJson.get(featureNameMap.getOrDefault(darwinKey, ""));

                // 写入到新的excel表中

            }

        });
    }

    /**
     * 写数据
     */
    public static void writeData() {

    }



    public static void main(String[] args) {

    }
}
