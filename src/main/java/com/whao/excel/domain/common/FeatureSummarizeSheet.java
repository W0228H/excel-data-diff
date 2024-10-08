package com.whao.excel.domain.common;

/**
 * @author xiongwh
 * @date 2024/8/12 9:39 PM
 */

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.NumberFormat;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.alibaba.excel.enums.CellDataTypeEnum;
import com.alibaba.excel.metadata.data.WriteCellData;
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

    @ColumnWidth(55)
    @ExcelProperty("darwin名称")
    private String darwinName;

    @ColumnWidth(55)
    @ExcelProperty("模型名称")
    private String modelName;

    /**
     * 一致率
     */
    @ColumnWidth(9)
    @NumberFormat("#.###%")
    @ExcelProperty("一致率")
    private BigDecimal concordanceRate;

    /**
     * muse错误率
     */
    @ColumnWidth(13)
    @NumberFormat("0.0000%")
    @ExcelProperty("muse错误率")
    private WriteCellData<BigDecimal> museFailRate;

    /**
     * muse错误个数
     */
    @ColumnWidth(9)
    @ExcelProperty("muse错误个数")
    private Long museFailCount;

    /**
     * 请求个数
     */
    @ColumnWidth(9)
    @ExcelProperty("请求个数")
    private Integer queryCount;

    public void beautyFormat() {
        this.museFailRate.setType(CellDataTypeEnum.NUMBER);
    }

}
