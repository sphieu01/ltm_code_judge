-- Migration 002: Drop legacy class_id from users and problems tables
-- Data is fully migrated to class_enrollments and class_problems.

BEGIN;

-- 1. Sync any remaining users.class_id into class_enrollments before dropping
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'users' AND column_name = 'class_id') THEN
    INSERT INTO class_enrollments (class_id, student_id)
    SELECT class_id, id FROM users
    WHERE UPPER(TRIM(role::text)) = 'STUDENT' AND class_id IS NOT NULL
    ON CONFLICT (class_id, student_id) DO NOTHING;

    ALTER TABLE users DROP COLUMN class_id CASCADE;
  END IF;
END $$;

-- 2. Sync any remaining problems.class_id into class_problems before dropping
DO $$ BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name = 'problems' AND column_name = 'class_id') THEN
    INSERT INTO class_problems (class_id, problem_id)
    SELECT class_id, id FROM problems
    WHERE class_id IS NOT NULL
    ON CONFLICT (class_id, problem_id) DO NOTHING;

    ALTER TABLE problems DROP COLUMN class_id CASCADE;
  END IF;
END $$;

COMMIT;
