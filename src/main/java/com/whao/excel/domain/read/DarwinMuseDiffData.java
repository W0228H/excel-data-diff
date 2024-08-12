package com.whao.excel.domain.read;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.DateTimeFormat;
import lombok.Data;

import java.util.Date;

/**
 * @author xiongwh
 * @date 2024/8/12 9:37 PM
 */
@Data
public class DarwinMuseDiffData {

    @ExcelProperty("trace_id")
    private String traceId;

    @ExcelProperty("muse_data")
    private String museData;

    @ExcelProperty("darwin_data")
    private String darwinData;

    @ExcelProperty("muse_time")
    @DateTimeFormat("MM/dd/yyyy HH:mm")
    private Date museTime;

    @ExcelProperty("darwin_time")
    @DateTimeFormat("MM/dd/yyyy HH:mm")
    private Date darwinTime;
}
