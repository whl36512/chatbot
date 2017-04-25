drop database if exists vent;
create database vent; 
drop user if exists vent;
create user vent with password 'vx36552124';
-- alter database vent owner to vent;
-- GRANT ALL PRIVILEGES ON DATABASE vent to vent;
\c vent
grant all on all tables in schema public to vent;
CREATE EXTENSION pgcrypto;
CREATE EXTENSION  "uuid-ossp";


create table topic
(
	topic_id uuid not null default uuid_generate_v4()
	, create_ts timestamp with time zone not null default clock_timestamp()
	, constraint pk_topic PRIMARY KEY (topic_id)
)
;

create table link
(
	link_id uuid not null default uuid_generate_v4()
	, topic_id uuid null
	, title text not null
	, publish_ts timestamp with time zone not null
	, create_ts timestamp with time zone not null default clock_timestamp()
	, author text
	, url text not null
	, language text not null default 'english'
	, comment_cnt integer not null default 0
	, search_text tsvector
	, status char(1) not null default 'R'    -- Active,Review, Dead
	, constraint pk_link PRIMARY KEY (link_id)
	, constraint uk_link UNIQUE (url)
)
;

create table msg
(
	  msg_id uuid not null default uuid_generate_v4()
	, link_id uuid not null
	, parent_msg_id uuid
	, create_ts timestamp with time zone not null default clock_timestamp()
	, content text not null
	, flag_cnt integer not null default 0
	, status char(1) not null default 'A'    -- Active, Dead
	, ip_hash text    -- Active, Dead
	, constraint  pk_msg PRIMARY KEY (msg_id) 
)
;

create table ip_hash
(
	ip_hash text not null 
	, create_ts timestamp with time zone not null default clock_timestamp()
)
;

alter table link add FOREIGN KEY (topic_id) REFERENCES topic (topic_id);
alter table msg add FOREIGN KEY (link_id) REFERENCES link (link_id);
alter table msg add FOREIGN KEY (parent_msg_id) REFERENCES msg (msg_id);
create index ix_link_search_text on link using GIN (search_text);

grant all on public.topic to vent;
grant all on public.link to vent;
grant all on public.msg to vent;
grant all on public.ip_hash to vent;
