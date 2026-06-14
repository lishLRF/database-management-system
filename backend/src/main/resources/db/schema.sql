-- 数据库连接配置表
CREATE TABLE IF NOT EXISTS db_connections (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    db_type VARCHAR(20) NOT NULL,
    host VARCHAR(255),
    port INTEGER,
    database_name VARCHAR(200) NOT NULL,
    username VARCHAR(100),
    password_encrypted TEXT,
    is_active BOOLEAN DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_connections_user ON db_connections(user_id);

-- AI配置表
CREATE TABLE IF NOT EXISTS ai_configs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id VARCHAR(100) NOT NULL UNIQUE,
    api_provider VARCHAR(50) NOT NULL,
    api_key_encrypted TEXT NOT NULL,
    api_base_url VARCHAR(500),
    model_name VARCHAR(100),
    temperature REAL DEFAULT 0.7,
    max_tokens INTEGER DEFAULT 2000,
    project_background TEXT DEFAULT '',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- SQL执行历史表
CREATE TABLE IF NOT EXISTS sql_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id VARCHAR(100) NOT NULL,
    connection_id INTEGER NOT NULL,
    sql_text TEXT NOT NULL,
    sql_type VARCHAR(20),
    target_table VARCHAR(200),
    source VARCHAR(20) DEFAULT 'manual',
    execution_time INTEGER,
    rows_affected INTEGER,
    status VARCHAR(20),
    error_message TEXT,
    is_visible BOOLEAN DEFAULT 1,
    executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (connection_id) REFERENCES db_connections(id)
);
CREATE INDEX IF NOT EXISTS idx_history_user_time ON sql_history(user_id, executed_at DESC);
CREATE INDEX IF NOT EXISTS idx_history_visible ON sql_history(is_visible, executed_at DESC);
CREATE INDEX IF NOT EXISTS idx_history_type ON sql_history(sql_type);
CREATE INDEX IF NOT EXISTS idx_history_table ON sql_history(target_table);

-- SQL片段表
CREATE TABLE IF NOT EXISTS sql_snippets (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id VARCHAR(100) NOT NULL,
    title VARCHAR(200) NOT NULL,
    sql_content TEXT NOT NULL,
    description TEXT,
    tags VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_snippets_user ON sql_snippets(user_id);

-- AI对话历史表
CREATE TABLE IF NOT EXISTS ai_conversations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    conversation_id VARCHAR(100) NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    connection_id INTEGER,
    message_type VARCHAR(20),
    message_content TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (connection_id) REFERENCES db_connections(id)
);
CREATE INDEX IF NOT EXISTS idx_conversations_session ON ai_conversations(conversation_id, created_at);

-- Markdown上传记录表
CREATE TABLE IF NOT EXISTS markdown_uploads (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id VARCHAR(100) NOT NULL,
    connection_id INTEGER NOT NULL,
    table_name VARCHAR(200) NOT NULL,
    file_name VARCHAR(500) NOT NULL,
    extracted_rows INTEGER,
    inserted_rows INTEGER,
    status VARCHAR(20),
    error_message TEXT,
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (connection_id) REFERENCES db_connections(id)
);
CREATE INDEX IF NOT EXISTS idx_uploads_user ON markdown_uploads(user_id, uploaded_at DESC);

-- 向后兼容：已有表添加 project_background 列
ALTER TABLE ai_configs ADD COLUMN project_background TEXT DEFAULT '';

-- 向后兼容：sql_history 表添加新字段
ALTER TABLE sql_history ADD COLUMN sql_type VARCHAR(20);
ALTER TABLE sql_history ADD COLUMN target_table VARCHAR(200);
ALTER TABLE sql_history ADD COLUMN source VARCHAR(20) DEFAULT 'manual';
ALTER TABLE sql_history ADD COLUMN is_visible BOOLEAN DEFAULT 1;

-- ============================================================
--  Markdown 智能解析模块 元数据表
-- ============================================================

-- Markdown 文档表（上传文件 + AI摘要）
-- status: uploaded / summarized / chunked / classified / exported
CREATE TABLE IF NOT EXISTS md_documents (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id VARCHAR(100) NOT NULL,
    file_name VARCHAR(500) NOT NULL,
    file_size INTEGER NOT NULL,
    full_content TEXT NOT NULL,
    ai_summary TEXT,
    status VARCHAR(20) DEFAULT 'uploaded',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_md_docs_user ON md_documents(user_id, created_at DESC);

-- 分块表（AI 分块结果 + 人工调整）
-- chunk_type: structured_data / semantic_content
CREATE TABLE IF NOT EXISTS md_chunks (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    document_id INTEGER NOT NULL,
    chunk_index INTEGER NOT NULL,
    chunk_type VARCHAR(20) NOT NULL,
    chunk_title VARCHAR(500),
    content_start_pos INTEGER NOT NULL,
    content_end_pos INTEGER NOT NULL,
    chunk_content TEXT NOT NULL,
    classification_reason TEXT,
    human_modified BOOLEAN DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (document_id) REFERENCES md_documents(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_md_chunks_doc ON md_chunks(document_id, chunk_index);

-- 注意：md_documents/md_chunks 两表为 Markdown 智能解析模块专用表
-- 首次启动时会自动创建；后续如需加字段，在此处添加 ALTER TABLE 语句

-- 操作日志表（对话操作记录）
CREATE TABLE IF NOT EXISTS operation_logs (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id VARCHAR(100) NOT NULL,
    operation_type VARCHAR(50) NOT NULL,
    operation_details TEXT,
    connection_id INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (connection_id) REFERENCES db_connections(id)
);
CREATE INDEX IF NOT EXISTS idx_operation_logs_user ON operation_logs(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_operation_logs_type ON operation_logs(operation_type);
