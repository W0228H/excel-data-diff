package com.whao.excel.domain.write;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.util.Date;

@Data
public class FeatureWriteFeatureData {

    @ExcelProperty("featureName")
    private String featureName;

    @ExcelProperty("traceId")
    private String traceId;

    @ExcelProperty("darwin的值")
    private String darwinValue;

    @ExcelProperty("model的值")
    private String modelValue;

    @ExcelProperty("hive时间")
    private Date time;

    /**
     * 特征总结页面
     */
    @Data
    public static class FeatureSummarizeSheet {

        @ExcelProperty("featureName")
        private String featureName;

        /**
         * 一致率
         */
        @ExcelProperty("concordanceRate")
        private Double concordanceRate;

    }
}
