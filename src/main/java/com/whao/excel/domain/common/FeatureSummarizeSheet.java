package com.whao.excel.domain.common;

/**
 * @author xiongwh
 * @date 2024/8/12 9:39 PM
 */

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.NumberFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 特征总结页面
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FeatureSummarizeSheet {

    @ExcelProperty("featureName")
    private String featureName;

    /**
     * 一致率
     */
    @NumberFormat("#.##%")
    @ExcelProperty("一致率")
    private BigDecimal concordanceRate;

}
