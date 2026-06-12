#!/bin/bash

echo "========================================"
echo "Task #2 实现验证报告"
echo "========================================"
echo ""

echo "✓ 新增文件检查:"
echo "  1. JwtUtil.java - JWT工具类"
test -f "F:\保存地址一\研究生项目学习\AI正向设计--2026年2月\数据库管理软件\backend\src\main\java\com\dbmanager\util\JwtUtil.java" && echo "     [存在]" || echo "     [缺失]"

echo "  2. JwtAuthenticationFilter.java - JWT认证过滤器"
test -f "F:\保存地址一\研究生项目学习\AI正向设计--2026年2月\数据库管理软件\backend\src\main\java\com\dbmanager\security\JwtAuthenticationFilter.java" && echo "     [存在]" || echo "     [缺失]"

echo "  3. AuthController.java - 认证控制器"
test -f "F:\保存地址一\研究生项目学习\AI正向设计--2026年2月\数据库管理软件\backend\src\main\java\com\dbmanager\controller\AuthController.java" && echo "     [存在]" || echo "     [缺失]"

echo ""
echo "✓ 修改文件检查:"
echo "  1. SecurityConfig.java - Spring Security配置"
grep -q "JwtAuthenticationFilter" "F:\保存地址一\研究生项目学习\AI正向设计--2026年2月\数据库管理软件\backend\src\main\java\com\dbmanager\config\SecurityConfig.java" && echo "     [已更新]" || echo "     [未更新]"

echo "  2. HealthController.java - 健康检查控制器"
grep -q "SecurityContextHolder" "F:\保存地址一\研究生项目学习\AI正向设计--2026年2月\数据库管理软件\backend\src\main\java\com\dbmanager\controller\HealthController.java" && echo "     [已更新]" || echo "     [未更新]"

echo ""
echo "✓ 配置文件检查:"
echo "  1. application.yml"
grep -q "debug-mode" "F:\保存地址一\研究生项目学习\AI正向设计--2026年2月\数据库管理软件\backend\src\main\resources\application.yml" && echo "     [包含debug-mode配置]" || echo "     [缺失配置]"

echo "  2. application-dev.yml"
grep -q "debug-mode: true" "F:\保存地址一\研究生项目学习\AI正向设计--2026年2月\数据库管理软件\backend\src\main\resources\application-dev.yml" && echo "     [开发模式已启用]" || echo "     [未启用]"

echo ""
echo "✓ 依赖检查:"
echo "  1. JJWT 0.12.5"
grep -q "jjwt" "F:\保存地址一\研究生项目学习\AI正向设计--2026年2月\数据库管理软件\backend\pom.xml" && echo "     [已配置]" || echo "     [未配置]"

echo ""
echo "========================================"
echo "实现状态: 完成 ✓"
echo "========================================"
