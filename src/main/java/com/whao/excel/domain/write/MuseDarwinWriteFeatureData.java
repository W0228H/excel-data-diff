package com.whao.excel.domain.write;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.NumberFormat;
import com.alibaba.excel.metadata.data.WriteCellData;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * @author xiongwh
 * @date 2024/8/12 10:05 PM
 */
@Data
public class MuseDarwinWriteFeatureData {

    @ExcelProperty("featureName")
    private String featureName;

    @ExcelProperty("traceId")
    private String traceId;

    @ExcelProperty("darwin的值")
    private String darwinValue;

    @ExcelProperty("muse的值")
    private String museValue;

    @ExcelProperty("darwin时间")
    private Date darwinTime;

    @ExcelProperty("muse时间")
    private Date museTime;

    @NumberFormat("#.##%")
    @ExcelProperty("一致率")
    private WriteCellData<BigDecimal> concordanceRate;
}
