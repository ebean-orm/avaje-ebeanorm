-- apply changes
create table migtest_e_ref (
  id                            integer generated by default as identity not null,
  name                          varchar(255) not null,
  constraint pk_migtest_e_ref primary key (id)
);
alter table migtest_e_ref add constraint uq_migtest_e_ref_name unique  (name);

alter table migtest_ckey_detail drop constraint fk_migtest_ckey_detail_parent;
alter table migtest_e_basic drop constraint ck_migtest_e_basic_status;
alter table migtest_e_basic alter column status drop default;
alter table migtest_e_basic alter column status set null;
alter table migtest_e_basic add constraint ck_migtest_e_basic_status check ( status in ('N','A','I'));
alter table migtest_e_basic drop constraint uq_migtest_e_basic_description;
alter table migtest_e_basic alter column some_date drop default;
alter table migtest_e_basic alter column some_date set null;

update migtest_e_basic set user_id = 23 where user_id is null;
alter table migtest_e_basic drop constraint fk_migtest_e_basic_user_id;
alter table migtest_e_basic alter column user_id set default 23;
alter table migtest_e_basic alter column user_id set not null;
alter table migtest_e_basic add column old_boolean boolean not null default false;
alter table migtest_e_basic add column old_boolean2 boolean;
alter table migtest_e_basic add column eref_id integer;

alter table migtest_e_basic drop constraint uq_migtest_e_basic_status_indextest1;
alter table migtest_e_basic drop constraint uq_migtest_e_basic_name;
alter table migtest_e_basic drop constraint uq_migtest_e_basic_indextest4;
alter table migtest_e_basic drop constraint uq_migtest_e_basic_indextest5;
-- NOT YET IMPLEMENTED: alter table migtest_e_basic add constraint uq_migtest_e_basic_indextest2 unique  (indextest2);
-- NOT YET IMPLEMENTED: alter table migtest_e_basic add constraint uq_migtest_e_basic_indextest6 unique  (indextest6);
comment on column migtest_e_history.test_string is '';
comment on table migtest_e_history is '';
alter table migtest_e_history2 alter column test_string drop default;
alter table migtest_e_history2 alter column test_string set null;
create index ix_migtest_e_basic_indextest1 on migtest_e_basic (indextest1);
create index ix_migtest_e_basic_indextest5 on migtest_e_basic (indextest5);
drop index ix_migtest_e_basic_indextest3;
drop index ix_migtest_e_basic_indextest6;
alter table migtest_e_basic add constraint fk_migtest_e_basic_eref_id foreign key (eref_id) references migtest_e_ref (id) on delete restrict on update restrict;
create index ix_migtest_e_basic_eref_id on migtest_e_basic (eref_id);

