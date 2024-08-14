package com.whao.excel.domain.common;


import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.NumberFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FeatureTimeoutRate {

    @ExcelProperty("darwinName")
    private String darwinName;

    @ExcelProperty("modelName")
    private String modelName;

    @ExcelProperty("超时次数")
    private Long timeoutCounts;

    @ExcelProperty("请求总数")
    private Integer totalCounts;

    @NumberFormat("#.##%")
    @ExcelProperty("超时率")
    private BigDecimal timeoutRate;
}
