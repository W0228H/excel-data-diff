package com.whao.excel.service.impl;

import com.whao.excel.domain.write.FeatureWriteFeatureData;
import com.whao.excel.service.AbstractExcelAnalyze;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * @author xiongwh
 * @date 2024/8/12 10:03 PM
 */
@Service
@Slf4j
public class MuseDarwinAnalyzeServiceImpl extends AbstractExcelAnalyze<MultipartFile, Map<String, List<FeatureWriteFeatureData>>> {

    @Override
    public Map<String, List<FeatureWriteFeatureData>> analyzeExcel(MultipartFile file) throws IOException {
        return null;
    }

    @Override
    public String getAnalyzeOption() {
        return "muse-darwin";
    }
}
