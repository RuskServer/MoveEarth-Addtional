package com.ruskserver.moveearth_addtional.analytics.query.export;

import java.util.List;

/** 生テーブルの行を 1 行ずつ受け取る書き出し先。全行をメモリに載せずに済むようにする。 */
public interface ExportRowSink {
    /** 最初に 1 回だけ、SQL の列見出しのまま呼ばれる。 */
    void columns(List<String> columns) throws Exception;

    void row(Object[] values) throws Exception;
}
