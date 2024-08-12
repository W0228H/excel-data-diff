package com.whao.excel.controller;

import com.whao.excel.factory.ExcelAnalyzeFactory;
import com.whao.excel.service.impl.ModelDarwinAnalyzeServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author xiongwh
 * @date 2024/8/10 12:25 PM
 */
@RestController
@RequestMapping("/excel")
@Slf4j
public class AnalyzeExcelController {

    @Autowired
    private ExcelAnalyzeFactory excelAnalyzeFactory;

    @PostMapping("/analyze")
    public String analyzeExcel(@RequestParam("datasource") MultipartFile dataSource, @RequestParam("analyzeOption") String analyzeOption) {
        try {
            excelAnalyzeFactory.getStrategy(analyzeOption).execute(dataSource);
        } catch (Exception e) {
            log.error("AnalyzeExcel failed", e);
            return "fail";
        }
        return "ok";
    }

}
