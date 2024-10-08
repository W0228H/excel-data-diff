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
    @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    private Date museTime;

    @ExcelProperty("darwin_time")
    @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    private Date darwinTime;

    public DarwinMuseDiffData rebuildData() {
        this.darwinData = dataProcess(darwinData);
        this.museData = dataProcess(museData);
        return this;
    }

    private String dataProcess(String data) {
        return data.replaceAll("\"_", "\",").replaceAll("_\"", ",\"");
    }
}
