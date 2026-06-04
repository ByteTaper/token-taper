CREATE TABLE api_key (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenant(id),
  key_hash TEXT NOT NULL,
  name TEXT NOT NULL,
  status TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  revoked_at TIMESTAMPTZ
);

CREATE INDEX idx_api_key_tenant_id ON api_key (tenant_id);
CREATE INDEX idx_api_key_status ON api_key (status);
CREATE UNIQUE INDEX idx_api_key_key_hash ON api_key (key_hash);
