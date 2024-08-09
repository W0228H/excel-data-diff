package com.whao.excel.domain.read;

import com.alibaba.excel.annotation.ExcelIgnore;
import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class InputFeatureDataDto {

    @ExcelProperty("modelKey")
    private String modelKey;

    @ExcelProperty("code")
    private String code;

    @ExcelProperty("featureKey")
    private String featureKey;

    /**
     * 忽略这个字段
     */
    @ExcelIgnore
    private String defaultValue;
}
