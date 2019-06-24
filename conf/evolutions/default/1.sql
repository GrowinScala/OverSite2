-- User schema

-- !Ups

create table `addresses` (
  `address_id` BIGINT NOT NULL PRIMARY KEY,
  `address` TEXT NOT NULL
)

-- !Downs
drop table `addresses`