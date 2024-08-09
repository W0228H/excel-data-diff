package com.whao.excel.listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.whao.excel.domain.read.DarwinMuseDataDto;

/**
 * @author xiongwh
 * @date 2024/8/9 12:23 AM
 */
public class DarwinMuseDiffListener implements ReadListener<DarwinMuseDataDto> {

    @Override
    public void invoke(DarwinMuseDataDto data, AnalysisContext context) {
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {

    }
}
