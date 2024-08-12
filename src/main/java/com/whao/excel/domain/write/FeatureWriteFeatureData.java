package com.whao.excel.domain.write;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.NumberFormat;
import com.alibaba.excel.enums.CellDataTypeEnum;
import com.alibaba.excel.metadata.data.WriteCellData;
import com.alibaba.excel.write.metadata.style.WriteCellStyle;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class FeatureWriteFeatureData {

    @ExcelProperty("featureName")
    private String featureName;

    @ExcelProperty("traceId")
    private String traceId;

    @ExcelProperty("darwin的值")
    private String darwinValue;

    @ExcelProperty("model的值")
    private String modelValue;

    @ExcelProperty("hive时间")
    private Date time;

    @NumberFormat("#.##%")
    @ExcelProperty("一致率")
    private WriteCellData<BigDecimal> concordanceRate;

    public void beautifulFormat() {
        if (this.concordanceRate.getData().compareTo(BigDecimal.valueOf(0.95d)) <= 0) {
            this.concordanceRate.setType(CellDataTypeEnum.NUMBER);
            WriteCellStyle writeCellStyle = new WriteCellStyle();
            this.concordanceRate.setWriteCellStyle(writeCellStyle);
            writeCellStyle.setFillPatternType(FillPatternType.SOLID_FOREGROUND);
            writeCellStyle.setFillForegroundColor(IndexedColors.RED.getIndex());
        }
    }

    /**
     * 特征总结页面
     */
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class FeatureSummarizeSheet {

        @ExcelProperty("featureName")
        private String featureName;

        /**
         * 一致率
         */
        @NumberFormat("#.##%")
        @ExcelProperty("一致率")
        private BigDecimal concordanceRate;

    }
}
