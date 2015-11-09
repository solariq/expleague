create TABLE tbts.Rooms (
  id varchar(128) not null,
  owner varchar(128) not null,
  owner_state int(11),
  state int(11),
  worker varchar(128),

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
  active varchar(128) default null,

  CONSTRAINT Expert__foreign FOREIGN KEY (id) REFERENCES tbts.Users (id) ON DELETE CASCADE ON UPDATE CASCADE,
  PRIMARY KEY (id)
);

create TABLE tbts.Connections (
  user varchar(128) not null,
  node varchar(128),
  heartbeat timestamp,

  CONSTRAINT Connections__foreign FOREIGN KEY (user) REFERENCES tbts.Users (id) ON DELETE CASCADE ON UPDATE CASCADE,
  PRIMARY KEY (user)
);

ALTER TABLE tbts.Rooms ADD CONSTRAINT Room__owner FOREIGN KEY (owner) REFERENCES tbts.Clients (id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE tbts.Rooms ADD CONSTRAINT Room__expert FOREIGN KEY (worker) REFERENCES tbts.Experts (id) ON DELETE CASCADE ON UPDATE CASCADE;
ALTER TABLE tbts.Clients ADD CONSTRAINT Client__active_room FOREIGN KEY (active_room) REFERENCES tbts.Rooms (id) ON DELETE CASCADE ON UPDATE CASCADE;

SELECT id, node, heartbeat FROM tbts.Users LEFT JOIN tbts.Connections ON id = user;