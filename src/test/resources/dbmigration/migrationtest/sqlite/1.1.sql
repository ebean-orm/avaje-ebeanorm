-- apply changes
create table migtest_e_user (
  id                            integer not null,
  constraint pk_migtest_e_user primary key (id)
);

create table migtest_mtm_child_migtest_mtm_master (
  migtest_mtm_child_id          integer not null,
  migtest_mtm_master_id         integer not null,
  constraint pk_migtest_mtm_child_migtest_mtm_master primary key (migtest_mtm_child_id,migtest_mtm_master_id),
  foreign key (migtest_mtm_child_id) references migtest_mtm_child (id) on delete restrict on update restrict,
  foreign key (migtest_mtm_master_id) references migtest_mtm_master (id) on delete restrict on update restrict
);

create table migtest_mtm_master_migtest_mtm_child (
  migtest_mtm_master_id         integer not null,
  migtest_mtm_child_id          integer not null,
  constraint pk_migtest_mtm_master_migtest_mtm_child primary key (migtest_mtm_master_id,migtest_mtm_child_id),
  foreign key (migtest_mtm_master_id) references migtest_mtm_master (id) on delete restrict on update restrict,
  foreign key (migtest_mtm_child_id) references migtest_mtm_child (id) on delete restrict on update restrict
);

alter table migtest_ckey_detail add column one_key integer;
alter table migtest_ckey_detail add column two_key varchar(255);

alter table migtest_ckey_parent add column assoc_id integer;


update migtest_e_basic set status = 'A' where status is null;
alter table migtest_e_basic drop constraint ck_migtest_e_basic_status;
alter table migtest_e_basic alter column status set default 'A';
alter table migtest_e_basic alter column status set not null;
alter table migtest_e_basic add constraint ck_migtest_e_basic_status check ( status in ('N','A','I','?'));

-- rename all collisions;
alter table migtest_e_basic add constraint uq_migtest_e_basic_description unique  (description);

update migtest_e_basic set some_date = '2000-01-01T00:00:00' where some_date is null;
alter table migtest_e_basic alter column some_date set default '2000-01-01T00:00:00';
alter table migtest_e_basic alter column some_date set not null;

insert into migtest_e_user (id) select distinct user_id from migtest_e_basic;
alter table migtest_e_basic alter column user_id set null;
alter table migtest_e_basic add column new_string_field varchar(255) not null default 'foo''bar';
alter table migtest_e_basic add column new_boolean_field int default 0 not null;
update migtest_e_basic set new_boolean_field = old_boolean;

alter table migtest_e_basic add column new_boolean_field2 int default 0 not null;
alter table migtest_e_basic add column progress integer not null default 0;
alter table migtest_e_basic add constraint ck_migtest_e_basic_progress check ( progress in (0,1,2));
alter table migtest_e_basic add column new_integer integer not null default 42;

alter table migtest_e_basic drop constraint uq_migtest_e_basic_indextest2;
alter table migtest_e_basic drop constraint uq_migtest_e_basic_indextest6;
alter table migtest_e_basic add constraint uq_migtest_e_basic_status_indextest1 unique  (status,indextest1);
alter table migtest_e_basic add constraint uq_migtest_e_basic_name unique  (name);
alter table migtest_e_basic add constraint uq_migtest_e_basic_indextest4 unique  (indextest4);
alter table migtest_e_basic add constraint uq_migtest_e_basic_indextest5 unique  (indextest5);
alter table migtest_e_history alter column test_string integer;

update migtest_e_history2 set test_string = 'unknown' where test_string is null;
alter table migtest_e_history2 alter column test_string set default 'unknown';
alter table migtest_e_history2 alter column test_string set not null;
alter table migtest_e_history2 add column test_string2 varchar(255);
alter table migtest_e_history2 add column test_string3 varchar(255) not null default 'unknown';
alter table migtest_e_history2_history add column test_string2 varchar(255);
alter table migtest_e_history2_history add column test_string3 varchar(255);

alter table migtest_e_softdelete add column deleted int default 0 not null;

alter table migtest_oto_child add column master_id integer;

create index ix_migtest_e_basic_indextest3 on migtest_e_basic (indextest3);
create index ix_migtest_e_basic_indextest6 on migtest_e_basic (indextest6);
drop index if exists ix_migtest_e_basic_indextest1;
drop index if exists ix_migtest_e_basic_indextest5;
create index ix_migtest_ckey_parent_assoc_id on migtest_ckey_parent (assoc_id);


