CREATE TABLE ai_event (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  task_id UUID NOT NULL,
  span_id UUID,

  external_event_id TEXT,

  event_type TEXT NOT NULL,
  status TEXT NOT NULL,

  provider TEXT,
  model TEXT,
  tool_name TEXT,

  input_tokens BIGINT,
  output_tokens BIGINT,
  cached_tokens BIGINT,

  latency_ms BIGINT,
  retry_count INTEGER,
  cache_hit BOOLEAN,

  error_code TEXT,
  error_message TEXT,

  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,

  occurred_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

  CONSTRAINT ai_event_task_id_fk
    FOREIGN KEY (task_id)
    REFERENCES ai_task (id)
    ON DELETE CASCADE,

  CONSTRAINT ai_event_span_id_fk
    FOREIGN KEY (span_id)
    REFERENCES ai_span (id)
    ON DELETE SET NULL,

  CONSTRAINT ai_event_type_check
    CHECK (event_type IN ('llm_call', 'tool_call', 'retry', 'cache')),

  CONSTRAINT ai_event_status_check
    CHECK (status IN ('success', 'failure', 'timeout', 'cancelled', 'skipped')),

  CONSTRAINT ai_event_input_tokens_check
    CHECK (input_tokens IS NULL OR input_tokens >= 0),

  CONSTRAINT ai_event_output_tokens_check
    CHECK (output_tokens IS NULL OR output_tokens >= 0),

  CONSTRAINT ai_event_cached_tokens_check
    CHECK (cached_tokens IS NULL OR cached_tokens >= 0),

  CONSTRAINT ai_event_latency_ms_check
    CHECK (latency_ms IS NULL OR latency_ms >= 0),

  CONSTRAINT ai_event_retry_count_check
    CHECK (retry_count IS NULL OR retry_count >= 0)
);
--;;
CREATE INDEX ai_event_tenant_id_idx
  ON ai_event (tenant_id);
--;;
CREATE INDEX ai_event_task_id_idx
  ON ai_event (task_id);
--;;
CREATE INDEX ai_event_span_id_idx
  ON ai_event (span_id);
--;;
CREATE INDEX ai_event_task_occurred_at_idx
  ON ai_event (task_id, occurred_at ASC);
--;;
CREATE INDEX ai_event_span_occurred_at_idx
  ON ai_event (span_id, occurred_at ASC);
--;;
CREATE INDEX ai_event_tenant_event_type_idx
  ON ai_event (tenant_id, event_type);
--;;
CREATE INDEX ai_event_tenant_provider_model_idx
  ON ai_event (tenant_id, provider, model);
--;;
CREATE INDEX ai_event_tenant_occurred_at_idx
  ON ai_event (tenant_id, occurred_at DESC);
--;;
CREATE UNIQUE INDEX ai_event_tenant_external_event_id_uidx
  ON ai_event (tenant_id, external_event_id)
  WHERE external_event_id IS NOT NULL;
