package com.whao.excel.domain.read;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class DatasourceTimeDto {

    @ExcelProperty("数据源")
    private String datasource;

    @ExcelProperty("分区时间")
    private String time;
}
