package com.whao.excel.domain.read;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.DateTimeFormat;
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
    @DateTimeFormat("MM/dd/yyyy HH:mm")
    private Date sameTime;

    public DarwinModelDiffData rebuildData() {
        this.darwinData = dataProcess(darwinData);
        this.modelData = dataProcess(modelData);
        return this;
    }

    private String dataProcess(String data) {
        return data.replaceAll("\"_", "\",").replaceAll("_\"", ",\"");
    }
}
