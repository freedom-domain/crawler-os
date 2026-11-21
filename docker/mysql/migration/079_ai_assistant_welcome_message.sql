SET @add_ai_assistant_welcome_message_sql = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'ai_assistant'
       AND column_name = 'welcome_message') = 0,
    'ALTER TABLE ai_assistant ADD COLUMN welcome_message VARCHAR(256) NOT NULL DEFAULT '''' AFTER name',
    'SELECT 1'
);
PREPARE add_ai_assistant_welcome_message_stmt FROM @add_ai_assistant_welcome_message_sql;
EXECUTE add_ai_assistant_welcome_message_stmt;
DEALLOCATE PREPARE add_ai_assistant_welcome_message_stmt;
