#!/bin/bash

# 验证脚本：检查数据库初始化实现

echo "=== 数据库初始化实现验证 ==="
echo ""

# 1. 检查DDL脚本
echo "1. 检查DDL脚本文件..."
if [ -f "src/main/resources/db/schema.sql" ]; then
    echo "   ✓ schema.sql 文件存在"
    table_count=$(grep -c "CREATE TABLE IF NOT EXISTS" src/main/resources/db/schema.sql)
    echo "   ✓ 包含 ${table_count} 张表定义"

    # 列出所有表
    echo "   表清单:"
    grep "CREATE TABLE IF NOT EXISTS" src/main/resources/db/schema.sql | sed 's/CREATE TABLE IF NOT EXISTS /     - /' | sed 's/ (//'
else
    echo "   ✗ schema.sql 文件不存在"
fi

echo ""

# 2. 检查DatabaseInitializer类
echo "2. 检查DatabaseInitializer类..."
if [ -f "src/main/java/com/dbmanager/config/DatabaseInitializer.java" ]; then
    echo "   ✓ DatabaseInitializer.java 文件存在"

    if grep -q "@PostConstruct" src/main/java/com/dbmanager/config/DatabaseInitializer.java; then
        echo "   ✓ 包含 @PostConstruct 注解"
    fi

    if grep -q "@Component" src/main/java/com/dbmanager/config/DatabaseInitializer.java; then
        echo "   ✓ 包含 @Component 注解"
    fi

    if grep -q "ensureDataDirectoryExists" src/main/java/com/dbmanager/config/DatabaseInitializer.java; then
        echo "   ✓ 包含数据目录创建逻辑"
    fi

    if grep -q "executeSchemaScript" src/main/java/com/dbmanager/config/DatabaseInitializer.java; then
        echo "   ✓ 包含DDL脚本执行逻辑"
    fi
else
    echo "   ✗ DatabaseInitializer.java 文件不存在"
fi

echo ""

# 3. 检查application.yml配置
echo "3. 检查application.yml配置..."
if [ -f "src/main/resources/application.yml" ]; then
    echo "   ✓ application.yml 文件存在"

    if grep -q "jdbc:sqlite:./data/metadata.db" src/main/resources/application.yml; then
        echo "   ✓ SQLite数据源配置正确"
    fi

    if grep -q "org.sqlite.JDBC" src/main/resources/application.yml; then
        echo "   ✓ SQLite驱动类配置正确"
    fi
else
    echo "   ✗ application.yml 文件不存在"
fi

echo ""

# 4. 检查pom.xml依赖
echo "4. 检查pom.xml依赖..."
if [ -f "pom.xml" ]; then
    echo "   ✓ pom.xml 文件存在"

    if grep -q "sqlite-jdbc" pom.xml; then
        echo "   ✓ SQLite JDBC驱动依赖已配置"
    fi

    if grep -q "spring-boot-starter-jdbc" pom.xml; then
        echo "   ✓ Spring JDBC依赖已配置"
    fi
else
    echo "   ✗ pom.xml 文件不存在"
fi

echo ""
echo "=== 验证完成 ==="
echo ""
echo "启动应用后，将自动执行以下操作："
echo "  1. 创建 ./data 目录（如果不存在）"
echo "  2. 创建 ./data/metadata.db 数据库文件"
echo "  3. 执行 schema.sql 中的DDL语句"
echo "  4. 创建6张元数据表及索引"
