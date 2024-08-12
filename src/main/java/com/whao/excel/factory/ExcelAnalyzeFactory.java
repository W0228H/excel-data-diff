package com.whao.excel.factory;

import com.whao.excel.service.AbstractExcelAnalyze;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author xiongwh
 * @date 2024/8/12 10:31 PM
 */
@Component
public class ExcelAnalyzeFactory {

    private final Map<String, AbstractExcelAnalyze<MultipartFile, ?>> strategyMap;

    @Autowired
    public ExcelAnalyzeFactory(List<AbstractExcelAnalyze<MultipartFile, ?>> strategies) {
        strategyMap = strategies.stream().collect(Collectors.toMap(AbstractExcelAnalyze::getAnalyzeOption, Function.identity()));
    }

    public AbstractExcelAnalyze<MultipartFile, ?> getStrategy(String analyzeOption) {
        return strategyMap.get(analyzeOption);
    }
}
