-- Migration V4: Allow maxScore and scores up to 100.00 by expanding precision to DECIMAL(5,2)
ALTER TABLE grade_components MODIFY COLUMN max_score DECIMAL(5,2) NOT NULL;
ALTER TABLE grade_component_scores MODIFY COLUMN score DECIMAL(5,2) NOT NULL;
ALTER TABLE grades MODIFY COLUMN component_score DECIMAL(5,2) NULL;
ALTER TABLE grades MODIFY COLUMN final_score DECIMAL(5,2) NULL;

