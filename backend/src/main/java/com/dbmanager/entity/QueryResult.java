package com.dbmanager.entity;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class QueryResult {
    private List<String> columns;
    private List<Map<String, Object>> rows;
    private Integer total;
    private Long executionTime;
}
