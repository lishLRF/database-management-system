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
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- SQL执行历史表
CREATE TABLE IF NOT EXISTS sql_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id VARCHAR(100) NOT NULL,
    connection_id INTEGER NOT NULL,
    sql_text TEXT NOT NULL,
    execution_time INTEGER,
    rows_affected INTEGER,
    status VARCHAR(20),
    error_message TEXT,
    executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (connection_id) REFERENCES db_connections(id)
);
CREATE INDEX IF NOT EXISTS idx_history_user_time ON sql_history(user_id, executed_at DESC);

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
