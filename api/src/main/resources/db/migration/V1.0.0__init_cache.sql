create table topic (
    id serial primary key,
    name text not null
);

CREATE TABLE offset_cache
(
    topicId serial not null references topic(id),
    recordKey bigint,
    recordPartition smallint not null,
    recordOffset bigint not null,
    createdAt timestamp with time zone not null,
    unique (topicId, recordPartition, recordOffset)
);

create table last_cached_offset(
    topicId serial references topic(id),
    partition smallint not null,
    lastOffset bigint not null
);

create index offset_cache_key on offset_cache(recordKey);
create index offset_cache_created_at on offset_cache(createdAt);
