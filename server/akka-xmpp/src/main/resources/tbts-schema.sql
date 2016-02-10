CREATE USER 'tigase'@'localhost' IDENTIFIED BY 'tg30239';
CREATE DATABASE IF NOT EXISTS tbts CHARACTER SET 'utf8';
GRANT ALL ON tbts.* TO 'tigase'@'localhost';

CREATE TABLE tbts.Users (
  id varchar(128) not null,
  passwd VARCHAR(128),
  country VARCHAR(64) DEFAULT NULL,
  city VARCHAR(64) DEFAULT NULL,
  realName VARCHAR(64) DEFAULT NULL,
  avatarUrl VARCHAR(256) DEFAULT NULL,
  PRIMARY KEY (id)
);