package com.whao.excel.listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.whao.excel.domain.read.DarwinModelDiffData;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class DarwinModelDiffListener implements ReadListener<DarwinModelDiffData> {

    private static List<DarwinModelDiffData> datas = new ArrayList<>();

    @Override
    public void invoke(DarwinModelDiffData darwinModelDiffData, AnalysisContext analysisContext) {
        darwinModelDiffData.setDarwinData(darwinModelDiffData.getDarwinData().replaceAll("\"_", ",").replaceAll("_\"", ",\""));
        darwinModelDiffData.setModelData(darwinModelDiffData.getModelData().replaceAll("\"_", ",").replaceAll("_\"", ",\""));
        datas.add(darwinModelDiffData);
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {
        log.info("darwin model diff read complete!!");
    }

    public List<DarwinModelDiffData> getDataList() {
        return datas;
    }
}
