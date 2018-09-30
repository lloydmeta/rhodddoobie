DROP
TABLE IF EXISTS resources;

CREATE TABLE resources (
  id            BIGSERIAL PRIMARY KEY NOT NULL,
  deployment_id BIGINT                NOT NULL,
  kind          VARCHAR               NOT NULL,
  ref           VARCHAR               NOT NULL,
  name          TEXT                  NOT NULL,
  cpu_boost     BOOLEAN               NOT NULL,
  mem_limit     BOOLEAN               NOT NULL,
  CONSTRAINT fk_deployment_id FOREIGN KEY (deployment_id) REFERENCES deployments (id) ON DELETE CASCADE,
  CONSTRAINT unique_deployment_id_ref_kind UNIQUE (deployment_id, kind, ref),
  CONSTRAINT check_kind CHECK (kind IN ('Elasticsearch', 'Kibana', 'APM'))
);
