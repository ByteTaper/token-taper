CREATE TABLE ai_task (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenant(id),

  external_task_id TEXT,
  workflow TEXT,
  task_type TEXT,

  status TEXT NOT NULL,
  started_at TIMESTAMPTZ NOT NULL,
  finished_at TIMESTAMPTZ,

  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,

  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT ai_task_status_check
    CHECK (status IN ('started', 'finished', 'failed', 'cancelled')),

  CONSTRAINT ai_task_finished_at_check
    CHECK (
      (status = 'started' AND finished_at IS NULL)
      OR
      (status IN ('finished', 'failed', 'cancelled') AND finished_at IS NOT NULL)
    )
);
--;;
CREATE INDEX ai_task_tenant_id_idx
  ON ai_task (tenant_id);
--;;
CREATE INDEX ai_task_tenant_status_idx
  ON ai_task (tenant_id, status);
--;;
CREATE INDEX ai_task_tenant_workflow_idx
  ON ai_task (tenant_id, workflow);
--;;
CREATE INDEX ai_task_tenant_started_at_idx
  ON ai_task (tenant_id, started_at DESC);
--;;
CREATE UNIQUE INDEX ai_task_tenant_external_task_id_uidx
  ON ai_task (tenant_id, external_task_id)
  WHERE external_task_id IS NOT NULL;
