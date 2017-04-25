\c vent



insert into topic (create_ts) values ('2016-12-11');
insert into topic (create_ts) values ('2016-12-12');
insert into topic (create_ts) values ('2016-12-13');

select * from topic;
insert into link (topic_id, title, publish_ts, author, url)
select topic_id
	, '‘A bunch of crybabies’: Trump camp derides Clinton campaign decision to join recount effort' 
	, '2016-11-27T01:26-500'
	, 'Amy B Wang; Matt Zapotosky'
	, 'https://www.washingtonpost.com/news/the-fix/wp/2016/11/27/trump-calls-recount-efforts-sad-declares-nothing-will-change/'
from topic
where create_ts='2016-12-11'
;

insert into link (topic_id, title, publish_ts, author, url)
select topic_id
	, 'How a Putin Fan Overseas Pushed Pro-Trump Propaganda to Americans'
	, '2016-12-17'
	, 'MIKE McINTIRE'
	, 'http://www.nytimes.com/2016/12/17/world/europe/russia-propaganda-elections.html'
from topic
where create_ts='2016-12-11'
;
insert into link (topic_id, title, publish_ts, author, url)
select topic_id
	, 'Wonder and Worry, as a Syrian Child Transforms'
	, '2016-12-17'
	, 'CATRIN EINHORN and JODI KANTOR'
	, 'http://www.nytimes.com/2016/12/17/world/americas/syrian-refugees-canada.html?hp&action=click&pgtype=Homepage&clickSource=image&module=photo-spot-region&region=top-news&WT.nav=top-news'
from topic
where create_ts='2016-12-12'
;
select * from link;

insert into msg (link_id, content)
select link_id, 'Trump won'
from link
;

insert into msg (link_id, parent_msg_id, content)
select link_id, msg_id, 'Trump won, you lost. Too bad'
from msg 
;

select * from msg
;

update link l set comment_cnt = ( select count(*) from msg m where m.link_id=l.link_id)
;

update link l set search_text = to_tsvector(language::regconfig, coalesce(title,'') || ' ' || coalesce(author,''))
		, status='A'
;
