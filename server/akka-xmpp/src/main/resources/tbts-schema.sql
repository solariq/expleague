# CREATE USER 'tigase'@'localhost' IDENTIFIED BY 'tg30239';
CREATE DATABASE IF NOT EXISTS expleague CHARACTER SET 'utf8';
GRANT ALL ON expleague.* TO 'tigase'@'localhost';

USE expleague;

CREATE TABLE IF NOT EXISTS expleague.Users (
  id varchar(64) not null,
  country VARCHAR(64) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT NULL,
  city VARCHAR(64) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT NULL,
  name VARCHAR(64) CHARACTER SET utf8 COLLATE utf8_general_ci DEFAULT NULL,
  sex INTEGER(8) DEFAULT 0,
  age INTEGER(8) DEFAULT 0,
  created TIMESTAMP DEFAULT CURRENT_TIMESTAMP(),
  avatar VARCHAR(256) DEFAULT NULL,

  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS expleague.Devices (
  id VARCHAR(64),
  user VARCHAR(64),
  token VARCHAR(256),
  passwd VARCHAR(128),
  platform VARCHAR(32),
  expert BOOLEAN DEFAULT FALSE,

  PRIMARY KEY (id),
  CONSTRAINT Devices_Users_id_fk FOREIGN KEY (user) REFERENCES Users (id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS expleague.Tags (
  id INTEGER(16) AUTO_INCREMENT,
  tag VARCHAR(64) CHARACTER SET utf8 COLLATE utf8_general_ci,

  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS expleague.Specializations (
  owner VARCHAR(64),
  tag INTEGER(16),
  score FLOAT(16),

  CONSTRAINT Specializations_Users_id_fk FOREIGN KEY (owner) REFERENCES Users (id) ON DELETE CASCADE,
  CONSTRAINT Specializations_Tags_id_fk FOREIGN KEY (tag) REFERENCES Tags (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS expleague.Orders (
  id INTEGER(32) AUTO_INCREMENT,
  room VARCHAR(64) NOT NULL,
  offer MEDIUMTEXT CHARACTER SET utf8 COLLATE utf8_general_ci,
  eta TIMESTAMP NOT NULL,
  status INTEGER(8),
  score FLOAT(16) DEFAULT -1.0,

  PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS expleague.Topics (
  `order` INTEGER(32) NOT NULL,
  tag INTEGER(16) NOT NULL,

  CONSTRAINT Topics_Orders_id_fk FOREIGN KEY (`order`) REFERENCES Orders (id) ON DELETE CASCADE,
  CONSTRAINT Topics_Tags_id_fk FOREIGN KEY (tag) REFERENCES Tags (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS expleague.Participants (
  id INTEGER(32) AUTO_INCREMENT,
  `order` INTEGER(32) NOT NULL,
  partisipant VARCHAR(64) NOT NULL,
  role INT(8) DEFAULT 0,
#   score FLOAT(16) DEFAULT -1.0,

  PRIMARY KEY (id),
  CONSTRAINT Participants_Users_id_fk FOREIGN KEY (partisipant) REFERENCES Users (id) ON DELETE CASCADE,
  CONSTRAINT Participants_Rooms_id_fk FOREIGN KEY (`order`) REFERENCES Orders (id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS expleague.Applications (
  email VARCHAR(128) NOT NULL,
  referer VARCHAR(64),

  CONSTRAINT Applications_Users_id_fk FOREIGN KEY (referer) REFERENCES Users (id) ON DELETE SET NULL
);
