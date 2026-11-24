CREATE TABLE IF NOT EXISTS spider_chat_memory (
    memory_id VARCHAR(255) NOT NULL,
    message_order INTEGER NOT NULL,
    message_json CLOB NOT NULL,
    PRIMARY KEY (memory_id, message_order)
);
