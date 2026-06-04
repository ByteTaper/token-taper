CREATE TABLE tenant (
  id UUID PRIMARY KEY,
  name TEXT NOT NULL,
  slug TEXT NOT NULL UNIQUE,
  status TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_tenant_status ON tenant (status);
CREATE INDEX idx_tenant_created_at ON tenant (created_at);
