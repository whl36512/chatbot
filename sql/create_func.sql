\c vent

create or replace function msg_order_by_score(linkId text, topicId text)
  returns table (msg_id uuid,  create_ts timestamp with time zone, content text, level integer, title text)
as
$body$
with RECURSIVE tree AS
(
        SELECT m.*
                , ARRAY[create_ts] AS timearray
        FROM m
        UNION ALL
        SELECT
                c.*
                , p.level+1
                , p.timearray || c.create_ts timearray
        FROM msg As c     -- c for child
        JOIN tree AS p ON (c.parent_msg_id = p.msg_id and c.status!='D')   -- p for parent
)
, a as
(
        select
                nullif(linkId,  '')::uuid link_id
                , nullif(topicId, '')::uuid topic_id
)
, m as
(
        SELECT  m.*
                , 1 as level
        FROM a, msg m
        WHERE a.topic_id is null
        and a.link_id is not null
        and  m.link_id  =a.link_id
        and m.parent_msg_id IS NULL
        and m.status!='D'
        union all
        select  m.*
                , 1
        from a , link l, msg m
        where a.topic_id is not null
        and a.link_id is null
        and l.topic_id=a.topic_id
        and m.link_id=l.link_id
        and m.parent_msg_id IS NULL
        and m.status !='D'
)
SELECT
        t.msg_id
--        , t.parent_msg_id
--        , t.link_id
        , t.create_ts
        , t.content
        , t.level
        , case t.level when 1 then l.title else null end  title -- get title only for level 1 to reduce  size of retrieved data
FROM tree t
join link l on (l.link_id=t.link_id)  -- join link table to get title
ORDER BY  t.timearray || '3000-01-01'::timestamp with time zone  desc
;
$body$
language sql;

create or replace function save_msg(linkId text, msgId text, contentIn text, ipHash text)
  returns table (cnt bigint )
as
$body$
	with a as (
  		select
  		nullif(linkId,'')::uuid as link_id
  		, nullif(msgId,'')::uuid as parent_msg_id
  		, contentIn::text as content
		, ipHash ip_hash
		)
	, b as (
  		insert into msg (link_id, parent_msg_id, content, ip_hash)
  		select p.link_id, a.parent_msg_id, a.content, a.ip_hash
 	 	from a join msg p on p.msg_id=a.parent_msg_id
  		where a.parent_msg_id is not null
  		and a.link_id is null
  		union all
  		select l.link_id, null, a.content, a.ip_hash
  		from a, link l
  		where a.link_id is not null
  		and a.parent_msg_id is null
		and l.link_id=a.link_id
  		returning *
		)
	, c as (
		update link l
		set comment_cnt= comment_cnt+1
		from b
		where l.link_id=b.link_id
		returning b.*
	)
	select count(1) cnt 
	from  c
	;
$body$
language sql;

create or replace function link_order_by_score(linkId text, topicId text)
  returns table  (
		link_id uuid
		, topic_id uuid 
		, title text
		, publish_ts timestamp with time zone
		, author text
		, url text 
		, comment_cnt integer 
)
as
$body$
with a as
(
        select
                nullif($1,'') ::uuid link_id
                , nullif($2,'') ::uuid topic_id
)
, b as (
        select l.*
        from a
        join link l on (l.link_id=a.link_id)
        where a.topic_id is null
        and a.link_id is not null
	and l.status='A'
        union all
        select l.*
        from a
        join link l on (l.topic_id=a.topic_id)
        where a.link_id is null
        and a.topic_id is not null
	and l.status='A'
)
select 
	b.link_id
	, b.topic_id 
	, b.title 
	, b.publish_ts 
	, b.author
	, b.url
	, b.comment_cnt 
from b
order by extract(epoch from (now() -b.create_ts))/(b.comment_cnt+1) -- lower score means more recent, more comments
;
$body$
language sql;

create or replace function save_link(
	topic_id text
	, title text
	, publish_ts text
	, author text
	, url text
	, language text
)
returns table  ( result text)
as
$body$
-- insert topic table if no input topic_id
-- check if url is already in the database. if not, then
-- insert into link table
	with a as (
       		select
       		--  nullif('83bc69e5-725b-44ed-ad5f-a8e24d9df1c6', '')::uuid topic_id
       		-- , 'a crying baby3'::text title
       		-- , now() publish_ts
       		-- , 'whl'::text author
       		-- , 'http://beegrove.com/3'::text url
       		nullif(nullif($1 ,''),'null') ::uuid topic_id
       		, $2 ::text title
       		, $3 ::timestamp with time zone publish_ts
       		, $4 ::text author
       		, $5 ::text url
       		, case nullif(nullif($6 ,''),'null') when null then 'english' else $6 end ::text lang
        )
	, b as (
        	select count(1) cnt
        	from a join link l on l.url=a.url
        )
	, c as (
        	insert into topic (create_ts)
        	select now()
        	from b, a
        	where b.cnt =0           -- no insert if url exists
        	and a.topic_id is null   -- no insert if input topic_id exists
        	returning topic_id
        )
	, d as (
        	select a.topic_id
        	from a, topic t, b
        	where b.cnt =0                  -- no return if url exists
        	and t.topic_id =a.topic_id      -- return if input topic_id exists in the database
        	)
	, e as (
        	select * from c -- a newly created topic_id or
        	union all
        	select * from d -- an existing topic_id
        	)
	, f as (
        	-- finally create new link
        	insert into link (topic_id, title, publish_ts, author, url, search_text, language)
        	select e.topic_id, a.title, a.publish_ts, a.author, a.url
			,to_tsvector(a.lang::regconfig, coalesce(a.title,'') || ' ' || coalesce(a.author,'')) 
			, a.lang
        	from e,a  -- if e has no rows, it means input topic_id does not match the database. So no insert
        	returning *   -- return nothing if url exists or input topic_id doesn't match database
        	)
        , g as  (
                select extract(epoch from (clock_timestamp()-create_ts))  elapsed  -- row from newly inserted or
                        , f.title
                from f
                union
                select extract(epoch from (clock_timestamp()- l.create_ts))  -- existing row
                        , l.title
                from a, link  l
                where l.url=a.url
                )
        select case when elapsed > 1 then 'The URL already exists in our database' else 'Saved: ' ||  title end result
        from g


	;
$body$
language sql;


create or replace function all_link( searchQuery text)
returns table  ( 
                link_id uuid
                , topic_id uuid
                , title text
                , publish_ts timestamp with time zone
                , author text
                , url text
                , comment_cnt integer
		, total bigint
		, rk_topic bigint
		, rk_link bigint
)
as
$body$
	with z as (
		select nullif(nullif(searchQuery,''),'null') search_query
		limit 1 -- a hint to planner
	)
	, y as (
		select distinct(topic_id)
		from z join
		link l on (z.search_query is not null and l.search_text @@ plainto_tsquery(z.search_query))
		where l.status='A'
	)
	, a as (
  		select t.*, sum(l.comment_cnt) total
  		from z
		left join topic t on (z.search_query is null)   --  force join order
		left join link l on (l.topic_id=t.topic_id and  l.status='A')   --  force join order
  		group by t.topic_id
		union all
  		select t.*, sum(l.comment_cnt) total
  		from y 
		left join topic t on (t.topic_id=y.topic_id) 	-- force join order
		left join link l on (l.topic_id=t.topic_id and  l.status='A' ) 	-- force join order
  		group by t.topic_id
	)
	select 
                l.link_id
                , l.topic_id
                , l.title
                , l.publish_ts
                , l.author
                , l.url
                , l.comment_cnt
		, a.total
		, dense_rank () over (order by extract(epoch from (now() -a.create_ts))/(a.total+1)) rk_topic
		, rank () over ( partition by a.topic_id order by extract(epoch from (now() -l.create_ts))/(l.comment_cnt+1) ) rk_link
	from a join link l on (l.topic_id = a.topic_id and  l.status='A')
	order by rk_topic, rk_link
	--order by  array[extract(epoch from (now() -a.create_ts))/(a.total+1), extract(epoch from (now() -l.create_ts))/(l.comment_cnt+1)] 
	-- lower score means more recent, more comments
	;
$body$
language sql;

create or replace function update_link (
	  link_id text
	, topic_id text
	, title text
	, publish_ts text
 	, create_ts  text
	, author text
	, url text
	, language text
	, comment_cnt text
	, search_text text
	, status text
)
returns table  ( 
	  link_id uuid
	, topic_id uuid
	, title text
	, publish_ts timestamp with time zone
 	, create_ts  timestamp with time zone
	, author text
	, url text
	, language text
	, comment_cnt integer
	, search_text tsvector
	, status character
)
as
$body$
	with a as 
	(
		select 
	  		  $1 ::uuid 			link_id 
			, $2 ::uuid  			topic_id 
			, $3 ::text 			title 
			, $4 ::timestamp with time zone  publish_ts
 			, $5 ::timestamp with time zone  create_ts
			, $6 ::text 			author
			, $7 ::text 			url 
			, $8 ::text 			lang
			, $9 ::integer 			comment_cnt 
			, $10 ::tsvector 		search_text
			, $11 ::text 			status 
		limit 1
	)
	update link l 
	set           
			(
        			  topic_id 
        			, title 
        			, publish_ts 
        			, create_ts 
        			, author 
        			, url 
        			, language 
        			, comment_cnt 
        			, search_text
        			, status 
			) =	(
				  a.topic_id 
				, a.title
				, a.publish_ts
 				, a.create_ts
				, a.author
				, a.url 
				, a.lang
				, a.comment_cnt 
				, to_tsvector(a.lang::regconfig, coalesce(a.title,'') || ' ' || coalesce(a.author,'')) 
				, a.status
			)
	from a
	where l.link_id = a.link_id
	returning l.*
	;
$body$
language sql;

