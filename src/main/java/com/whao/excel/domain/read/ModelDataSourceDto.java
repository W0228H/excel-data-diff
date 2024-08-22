package com.whao.excel.domain.read;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class ModelDataSourceDto {

    @ExcelProperty("模型入参")
    private String modelKey;

    @ExcelProperty("数据源")
    private String datasource;
}
