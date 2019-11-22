-- Migrationscripts for ebean unittest
-- drop dependencies
drop view if exists migtest_e_history2_with_history;
drop view if exists migtest_e_history3_with_history;
drop view if exists migtest_e_history4_with_history;

-- apply changes
create table migtest_e_ref (
  id                            serial not null,
  name                          varchar(127) not null,
  constraint uq_migtest_e_ref_name unique (name),
  constraint pk_migtest_e_ref primary key (id)
);

alter table if exists migtest_ckey_detail drop constraint if exists fk_migtest_ckey_detail_parent;
alter table if exists migtest_fk_cascade drop constraint if exists fk_migtest_fk_cascade_one_id;
alter table migtest_fk_cascade add constraint fk_migtest_fk_cascade_one_id foreign key (one_id) references migtest_fk_cascade_one (id) on delete cascade on update cascade;
alter table if exists migtest_fk_none drop constraint if exists fk_migtest_fk_none_one_id;
alter table if exists migtest_fk_none_via_join drop constraint if exists fk_migtest_fk_none_via_join_one_id;
alter table if exists migtest_fk_set_null drop constraint if exists fk_migtest_fk_set_null_one_id;
alter table migtest_fk_set_null add constraint fk_migtest_fk_set_null_one_id foreign key (one_id) references migtest_fk_one (id) on delete set null on update set null;
alter table migtest_e_basic drop constraint uq_migtest_e_basic_status_indextest1;
alter table migtest_e_basic drop constraint uq_migtest_e_basic_name;
alter table migtest_e_basic drop constraint uq_migtest_e_basic_indextest4;
alter table migtest_e_basic drop constraint uq_migtest_e_basic_indextest5;
alter table migtest_e_basic drop constraint if exists ck_migtest_e_basic_status;
alter table migtest_e_basic alter column status drop default;
alter table migtest_e_basic alter column status drop not null;
alter table migtest_e_basic add constraint ck_migtest_e_basic_status check ( status in ('N','A','I'));

update migtest_e_basic set status2 = 'N' where status2 is null;
alter table migtest_e_basic drop constraint if exists ck_migtest_e_basic_status2;
alter table migtest_e_basic alter column status2 type varchar(1);
alter table migtest_e_basic alter column status2 set default 'N';
alter table migtest_e_basic alter column status2 set not null;
alter table migtest_e_basic add constraint ck_migtest_e_basic_status2 check ( status2 in ('N','A','I'));
alter table migtest_e_basic drop constraint uq_migtest_e_basic_description;

update migtest_e_basic set user_id = 23 where user_id is null;
alter table if exists migtest_e_basic drop constraint if exists fk_migtest_e_basic_user_id;
alter table migtest_e_basic alter column user_id set default 23;
alter table migtest_e_basic alter column user_id set not null;
alter table migtest_e_basic add column old_boolean boolean default false not null;
alter table migtest_e_basic add column old_boolean2 boolean;
alter table migtest_e_basic add column eref_id integer;

alter table migtest_e_basic add constraint uq_migtest_e_basic_indextest2 unique  (indextest2);
alter table migtest_e_basic add constraint uq_migtest_e_basic_indextest6 unique  (indextest6);
alter table migtest_e_enum drop constraint if exists ck_migtest_e_enum_test_status;
alter table migtest_e_enum add constraint ck_migtest_e_enum_test_status check ( test_status in ('N','A','I'));
comment on column migtest_e_history.test_string is '';
comment on table migtest_e_history is '';
alter table migtest_e_history2 alter column test_string drop default;
alter table migtest_e_history2 alter column test_string drop not null;
alter table migtest_e_history2 add column obsolete_string1 varchar(255);
alter table migtest_e_history2 add column obsolete_string2 varchar(255);
alter table migtest_e_history2_history add column obsolete_string1 varchar(255);
alter table migtest_e_history2_history add column obsolete_string2 varchar(255);

alter table migtest_e_history4 alter column test_number type integer;
alter table migtest_e_history4_history alter column test_number type integer;
alter table migtest_e_history6 alter column test_number1 drop default;
alter table migtest_e_history6 alter column test_number1 drop not null;

-- NOTE: table has @History - special migration may be necessary
update migtest_e_history6 set test_number2 = 7 where test_number2 is null;
alter table migtest_e_history6 alter column test_number2 set default 7;
alter table migtest_e_history6 alter column test_number2 set not null;
alter table migtest_e_index1 alter column string1 type varchar(10);
alter table migtest_e_index1 alter column string2 type varchar(10);
alter table migtest_e_index2 alter column string1 type varchar(10);
alter table migtest_e_index2 alter column string2 type varchar(10);
alter table migtest_e_index3 alter column string1 type varchar(10);
alter table migtest_e_index3 alter column string2 type varchar(10);
alter table migtest_e_index4 alter column string1 type varchar(10);
alter table migtest_e_index4 alter column string2 type varchar(10);
alter table migtest_e_index5 alter column string1 type varchar(10);
alter table migtest_e_index5 alter column string2 type varchar(10);
alter table migtest_e_index6 alter column string1 type varchar(10);
alter table migtest_e_index6 alter column string2 type varchar(10);
alter table migtest_e_index6 add constraint uq_migtest_e_index6_string1 unique  (string1);
create index ix_migtest_e_basic_indextest1 on migtest_e_basic (indextest1);
create index ix_migtest_e_basic_indextest5 on migtest_e_basic (indextest5);
create index ix_migtest_e_index5 on migtest_e_index5 (string1,string2);
create index ix_migtest_e_index6_string2 on migtest_e_index6 (string2);
drop index if exists ix_migtest_e_basic_indextest3;
drop index if exists ix_migtest_e_basic_indextest6;
create index ix_migtest_e_basic_eref_id on migtest_e_basic (eref_id);
alter table migtest_e_basic add constraint fk_migtest_e_basic_eref_id foreign key (eref_id) references migtest_e_ref (id) on delete restrict on update restrict;

create view migtest_e_history2_with_history as select * from migtest_e_history2 union all select * from migtest_e_history2_history;

create view migtest_e_history3_with_history as select * from migtest_e_history3 union all select * from migtest_e_history3_history;

create view migtest_e_history4_with_history as select * from migtest_e_history4 union all select * from migtest_e_history4_history;

-- changes: [add obsolete_string1, add obsolete_string2]
create or replace function migtest_e_history2_history_version() returns trigger as $$
declare
  lowerTs timestamptz;
  upperTs timestamptz;
begin
  lowerTs = lower(OLD.sys_period);
  upperTs = greatest(lowerTs + '1 microsecond',current_timestamp);
  if (TG_OP = 'UPDATE') then
    insert into migtest_e_history2_history (sys_period,id, test_string, obsolete_string2, test_string2, test_string3, new_column) values (tstzrange(lowerTs,upperTs), OLD.id, OLD.test_string, OLD.obsolete_string2, OLD.test_string2, OLD.test_string3, OLD.new_column);
    NEW.sys_period = tstzrange(upperTs,null);
    return new;
  elsif (TG_OP = 'DELETE') then
    insert into migtest_e_history2_history (sys_period,id, test_string, obsolete_string2, test_string2, test_string3, new_column) values (tstzrange(lowerTs,upperTs), OLD.id, OLD.test_string, OLD.obsolete_string2, OLD.test_string2, OLD.test_string3, OLD.new_column);
    return old;
  end if;
end;
$$ LANGUAGE plpgsql;

-- changes: [include test_string]
create or replace function migtest_e_history3_history_version() returns trigger as $$
declare
  lowerTs timestamptz;
  upperTs timestamptz;
begin
  lowerTs = lower(OLD.sys_period);
  upperTs = greatest(lowerTs + '1 microsecond',current_timestamp);
  if (TG_OP = 'UPDATE') then
    insert into migtest_e_history3_history (sys_period,id, test_string) values (tstzrange(lowerTs,upperTs), OLD.id, OLD.test_string);
    NEW.sys_period = tstzrange(upperTs,null);
    return new;
  elsif (TG_OP = 'DELETE') then
    insert into migtest_e_history3_history (sys_period,id, test_string) values (tstzrange(lowerTs,upperTs), OLD.id, OLD.test_string);
    return old;
  end if;
end;
$$ LANGUAGE plpgsql;

-- changes: [alter test_number]
create or replace function migtest_e_history4_history_version() returns trigger as $$
declare
  lowerTs timestamptz;
  upperTs timestamptz;
begin
  lowerTs = lower(OLD.sys_period);
  upperTs = greatest(lowerTs + '1 microsecond',current_timestamp);
  if (TG_OP = 'UPDATE') then
    insert into migtest_e_history4_history (sys_period,id, test_number) values (tstzrange(lowerTs,upperTs), OLD.id, OLD.test_number);
    NEW.sys_period = tstzrange(upperTs,null);
    return new;
  elsif (TG_OP = 'DELETE') then
    insert into migtest_e_history4_history (sys_period,id, test_number) values (tstzrange(lowerTs,upperTs), OLD.id, OLD.test_number);
    return old;
  end if;
end;
$$ LANGUAGE plpgsql;

