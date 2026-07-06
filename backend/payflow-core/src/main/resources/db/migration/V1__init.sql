-- Baseline migration: extensions relied on by every subsequent PayFlow migration.
CREATE EXTENSION IF NOT EXISTS pgcrypto;
