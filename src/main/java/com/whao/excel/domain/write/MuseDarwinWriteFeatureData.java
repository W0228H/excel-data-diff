package com.whao.excel.domain.write;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.NumberFormat;
import com.alibaba.excel.enums.CellDataTypeEnum;
import com.alibaba.excel.metadata.data.WriteCellData;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import lombok.Data;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;

import java.math.BigDecimal;
import java.util.Date;

/**
 * @author xiongwh
 * @date 2024/8/12 10:05 PM
 */
@Data
public class MuseDarwinWriteFeatureData {

    @ExcelProperty("darwinName")
    private String darwinName;

    @ExcelProperty("modelName")
    private String modelName;

    @ExcelProperty("traceId")
    private String traceId;

    @ExcelProperty("darwin的值")
    private String darwinValue;

    @ExcelProperty("muse的值")
    private String museValue;

    @ExcelProperty("diff")
    private boolean diff;

    @ExcelProperty("darwin时间")
    private Date darwinTime;

    @ExcelProperty("muse时间")
    private Date museTime;

    @NumberFormat("#.##%")
    @ExcelProperty("一致率")
    private WriteCellData<BigDecimal> concordanceRate;

    public void beautifulFormat() {
        if (this.concordanceRate.getNumberValue().compareTo(BigDecimal.valueOf(0.95d)) <= 0) {
            this.concordanceRate.setType(CellDataTypeEnum.NUMBER);
            WriteCellStyle writeCellStyle = new WriteCellStyle();
            this.concordanceRate.setWriteCellStyle(writeCellStyle);
            writeCellStyle.setFillPatternType(FillPatternType.SOLID_FOREGROUND);
            writeCellStyle.setFillForegroundColor(IndexedColors.RED.getIndex());
        }
    }
}
