CREATE TABLE ai_span (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  task_id UUID NOT NULL,
  parent_span_id UUID,

  external_span_id TEXT,

  span_type TEXT NOT NULL,
  name TEXT,

  status TEXT NOT NULL,
  started_at TIMESTAMPTZ NOT NULL,
  finished_at TIMESTAMPTZ,

  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,

  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT ai_span_task_id_fk
    FOREIGN KEY (task_id)
    REFERENCES ai_task (id)
    ON DELETE CASCADE,

  CONSTRAINT ai_span_parent_span_id_fk
    FOREIGN KEY (parent_span_id)
    REFERENCES ai_span (id)
    ON DELETE CASCADE,

  CONSTRAINT ai_span_status_check
    CHECK (status IN ('started', 'finished', 'failed', 'cancelled')),

  CONSTRAINT ai_span_type_check
    CHECK (span_type IN ('workflow', 'agent_step', 'llm_call', 'tool_call', 'retry', 'cache', 'custom')),

  CONSTRAINT ai_span_finished_at_check
    CHECK (
      (status = 'started' AND finished_at IS NULL)
      OR
      (status IN ('finished', 'failed', 'cancelled') AND finished_at IS NOT NULL)
    )
);
--;;
CREATE INDEX ai_span_tenant_id_idx
  ON ai_span (tenant_id);
--;;
CREATE INDEX ai_span_task_id_idx
  ON ai_span (task_id);
--;;
CREATE INDEX ai_span_task_started_at_idx
  ON ai_span (task_id, started_at ASC);
--;;
CREATE INDEX ai_span_parent_span_id_idx
  ON ai_span (parent_span_id);
--;;
CREATE INDEX ai_span_tenant_task_idx
  ON ai_span (tenant_id, task_id);
--;;
CREATE INDEX ai_span_tenant_type_idx
  ON ai_span (tenant_id, span_type);
--;;
CREATE UNIQUE INDEX ai_span_tenant_external_span_id_uidx
  ON ai_span (tenant_id, external_span_id)
  WHERE external_span_id IS NOT NULL;
