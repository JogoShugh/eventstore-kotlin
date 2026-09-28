-- Event store schema, ported from Oskar Dudycz's "Build Your Own Event Store" workshop.
-- Stream versions are 0-based: a new stream starts at -1, so its first event is version 0.

CREATE TABLE IF NOT EXISTS streams(
    id             UUID                      NOT NULL    PRIMARY KEY,
    type           TEXT                      NOT NULL,
    version        BIGINT                    NOT NULL
);

CREATE TABLE IF NOT EXISTS events(
    id             UUID                      NOT NULL    PRIMARY KEY,
    data           JSONB                     NOT NULL,
    stream_id      UUID                      NOT NULL,
    type           TEXT                      NOT NULL,
    version        BIGINT                    NOT NULL,
    created        timestamp with time zone  NOT NULL    default (now()),
    FOREIGN KEY(stream_id) REFERENCES streams(id),
    CONSTRAINT events_stream_and_version UNIQUE(stream_id, version)
);

CREATE OR REPLACE FUNCTION append_event(
    id uuid,
    data jsonb,
    type text,
    stream_id uuid,
    stream_type text,
    expected_stream_version bigint default null
) RETURNS boolean
    LANGUAGE plpgsql
    AS $$
    DECLARE
        stream_version bigint;
    BEGIN
        -- get stream version
        SELECT
            version INTO stream_version
        FROM streams as s
        WHERE
            s.id = stream_id FOR UPDATE;

        -- if stream doesn't exist - create new one with version -1
        IF stream_version IS NULL THEN
            stream_version := -1;

            INSERT INTO streams
                (id, type, version)
            VALUES
                (stream_id, stream_type, stream_version);
        END IF;

        -- check optimistic concurrency
        IF expected_stream_version IS NOT NULL AND stream_version != expected_stream_version THEN
            RETURN FALSE;
        END IF;

        -- increment event_version
        stream_version := stream_version + 1;

        -- append event
        INSERT INTO events
            (id, data, stream_id, type, version)
        VALUES
            (id, data::jsonb, stream_id, type, stream_version);

        -- update stream version
        UPDATE streams as s
            SET version = stream_version
        WHERE
            s.id = stream_id;

        RETURN TRUE;
    END;
    $$;
