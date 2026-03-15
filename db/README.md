Database SQL scripts for ScholarGrid

Run these scripts manually in the order below if the tables do not already exist.

1) 001_study_analytics.sql
2) 002_ai_resource_aux_tables.sql
3) 003_resource_votes_progress_comments.sql

Example:
psql "$DATABASE_URL" -f db/001_study_analytics.sql
