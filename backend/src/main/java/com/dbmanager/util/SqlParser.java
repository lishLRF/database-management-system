package com.dbmanager.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL 解析工具类
 * 用于从 SQL 语句中提取类型和目标表名
 */
public class SqlParser {

    private static final Pattern SELECT_PATTERN = Pattern.compile(
        "^\\s*SELECT\\s+", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern INSERT_PATTERN = Pattern.compile(
        "^\\s*INSERT\\s+INTO\\s+[\"']?([\\w]+)[\"']?", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern UPDATE_PATTERN = Pattern.compile(
        "^\\s*UPDATE\\s+[\"']?([\\w]+)[\"']?", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern DELETE_PATTERN = Pattern.compile(
        "^\\s*DELETE\\s+FROM\\s+[\"']?([\\w]+)[\"']?", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern DDL_PATTERN = Pattern.compile(
        "^\\s*(CREATE|DROP|ALTER|TRUNCATE)\\s+", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern FROM_PATTERN = Pattern.compile(
        "\\s+FROM\\s+[\"']?([\\w]+)[\"']?", Pattern.CASE_INSENSITIVE);

    /**
     * 解析 SQL 类型
     * @return SELECT/INSERT/UPDATE/DELETE/DDL/OTHER
     */
    public static String parseSqlType(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return "OTHER";
        }

        String trimmed = sql.trim();
        if (SELECT_PATTERN.matcher(trimmed).find()) return "SELECT";
        if (INSERT_PATTERN.matcher(trimmed).find()) return "INSERT";
        if (UPDATE_PATTERN.matcher(trimmed).find()) return "UPDATE";
        if (DELETE_PATTERN.matcher(trimmed).find()) return "DELETE";
        if (DDL_PATTERN.matcher(trimmed).find()) return "DDL";
        return "OTHER";
    }

    /**
     * 解析目标表名
     * @return 表名，如果无法解析返回 null
     */
    public static String parseTargetTable(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return null;
        }

        String trimmed = sql.trim();

        // INSERT INTO table_name
        Matcher insertMatcher = INSERT_PATTERN.matcher(trimmed);
        if (insertMatcher.find()) {
            return insertMatcher.group(1);
        }

        // UPDATE table_name
        Matcher updateMatcher = UPDATE_PATTERN.matcher(trimmed);
        if (updateMatcher.find()) {
            return updateMatcher.group(1);
        }

        // DELETE FROM table_name
        Matcher deleteMatcher = DELETE_PATTERN.matcher(trimmed);
        if (deleteMatcher.find()) {
            return deleteMatcher.group(1);
        }

        // SELECT ... FROM table_name
        Matcher fromMatcher = FROM_PATTERN.matcher(trimmed);
        if (fromMatcher.find()) {
            return fromMatcher.group(1);
        }

        return null;
    }

    /**
     * 解析结果对象
     */
    public static class ParseResult {
        private String sqlType;
        private String targetTable;

        public ParseResult(String sqlType, String targetTable) {
            this.sqlType = sqlType;
            this.targetTable = targetTable;
        }

        public String getSqlType() { return sqlType; }
        public String getTargetTable() { return targetTable; }
    }

    /**
     * 一次性解析 SQL 类型和目标表
     */
    public static ParseResult parse(String sql) {
        return new ParseResult(parseSqlType(sql), parseTargetTable(sql));
    }
}
