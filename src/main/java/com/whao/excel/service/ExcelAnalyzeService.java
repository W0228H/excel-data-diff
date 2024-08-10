package com.whao.excel.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * @author xiongwh
 * @date 2024/8/10 12:55 PM
 */
public interface ExcelAnalyzeService<Req extends MultipartFile, Res> {

    void preProcess();

    Res analyzeExcel(Req file) throws IOException;

    void outputExcel(Res res);

}
