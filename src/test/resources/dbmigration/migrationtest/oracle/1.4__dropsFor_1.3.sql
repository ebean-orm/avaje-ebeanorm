-- apply changes
alter table migtest_e_basic drop column new_string_field;

alter table migtest_e_basic drop column new_boolean_field;

alter table migtest_e_basic drop column new_boolean_field2;

alter table migtest_e_basic drop column progress;

alter table migtest_e_basic drop column new_integer;

alter table migtest_e_history2 drop column test_string2;

alter table migtest_e_history2 drop column test_string3;

alter table migtest_e_softdelete drop column deleted;

drop table migtest_e_user cascade constraints purge;
drop sequence migtest_e_user_seq;
