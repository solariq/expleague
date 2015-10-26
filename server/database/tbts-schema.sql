create TABLE tbts.Rooms (
  id varchar(128) not null,
  owner varchar(128) not null,
  owner_state int(11),
  state int(11),
  active_expert varchar(128),
  storage_uri varchar(256),

  PRIMARY KEY (id)
);

create TABLE tbts.Users (
  id varchar(128) not null,

  PRIMARY KEY (id)
);

create TABLE tbts.Clients (
  id varchar(128) not null,
  state int default 0,
  active_room varchar(128) default NULL,

  CONSTRAINT Client__foreign FOREIGN KEY (id) REFERENCES tbts.Users (id) ON DELETE CASCADE ON UPDATE CASCADE,
  PRIMARY KEY (id)
);

create TABLE tbts.Experts (
  id varchar(128) not null,
  state int default 0,

  CONSTRAINT Expert__foreign FOREIGN KEY (id) REFERENCES tbts.Users (id) ON DELETE CASCADE ON UPDATE CASCADE ON INSERT,
  PRIMARY KEY (id)
);

ALTER TABLE tbts.Rooms ADD CONSTRAINT Room__owner FOREIGN KEY (owner) REFERENCES tbts.Clients (id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE tbts.Rooms ADD CONSTRAINT Room__expert FOREIGN KEY (active_expert) REFERENCES tbts.Experts (id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE tbts.Clients ADD CONSTRAINT Client__active_room FOREIGN KEY (active_room) REFERENCES tbts.Rooms (id) ON DELETE CASCADE ON UPDATE CASCADE;

