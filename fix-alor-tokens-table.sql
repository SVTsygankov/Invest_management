-- Скрипт для исправления таблицы alor_user_tokens
-- Выполните этот SQL в вашей базе данных перед повторным запуском приложения

-- Вариант 1: Удалить constraint, если он существует (рекомендуется)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 
        FROM information_schema.table_constraints 
        WHERE constraint_name = 'fk_alor_tokens_user' 
        AND table_name = 'alor_user_tokens'
    ) THEN
        ALTER TABLE alor_user_tokens DROP CONSTRAINT fk_alor_tokens_user;
        RAISE NOTICE 'Constraint fk_alor_tokens_user удален';
    ELSE
        RAISE NOTICE 'Constraint fk_alor_tokens_user не найден';
    END IF;
END $$;

-- Вариант 2: Если нужно полностью пересоздать таблицу (осторожно - удалит данные!)
-- DROP TABLE IF EXISTS alor_user_tokens CASCADE;

