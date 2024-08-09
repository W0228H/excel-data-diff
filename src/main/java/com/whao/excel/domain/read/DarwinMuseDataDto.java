package com.whao.excel.domain.read;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.util.Date;

/**
 * @author xiongwh
 * @date 2024/8/9 12:21 AM
 */
@Data
public class DarwinMuseDataDto {

    @ExcelProperty("trace_id")
    private String traceId;

    @ExcelProperty("darwin_data")
    private String darwinData;

    @ExcelProperty("muse_data")
    private String museData;

    @ExcelProperty("darwin_time")
    private Date darwinDate;

    @ExcelProperty("muse_time")
    private Date museDate;
}
