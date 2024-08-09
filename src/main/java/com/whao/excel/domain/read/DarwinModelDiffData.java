package com.whao.excel.domain.read;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.util.Date;

@Data
public class DarwinModelDiffData {

    @ExcelProperty("traceid")
    private String traceId;

    @ExcelProperty("darwin_data")
    private String darwinData;

    @ExcelProperty("model_data")
    private String modelData;

    @ExcelProperty("same_time")
    private Date sameTime;
}
