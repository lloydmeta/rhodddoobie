DROP
TABLE IF EXISTS deployments;

CREATE TABLE deployments (
  id   BIGSERIAL PRIMARY KEY NOT NULL,
  name TEXT                  NOT NULL
);
