alter table Orders ADD COLUMN payment VARCHAR(128) default null;
ALTER TABLE Orders ADD COLUMN activation_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE Participants ADD COLUMN timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE Tags ADD COLUMN icon VARCHAR(255);

SELECT `order`, score, room, SUM(duration) FROM (SELECT start.`order` as `order`, MIN(TO_SECONDS(finish.`timestamp`) - TO_SECONDS(start.`timestamp`)) as duration, finish.status FROM OrderStatusHistory as finish JOIN (SELECT id, `order`, `timestamp`, status as `from` FROM OrderStatusHistory WHERE status=1) AS start ON start.`order` = finish.`order` WHERE TO_SECONDS(finish.`timestamp`) - TO_SECONDS(start.`timestamp`) > 0 AND finish.status=3 GROUP BY start.id) AS durations JOIN Orders ON durations.`order` = Orders.id GROUP BY `order`;

/* 1.05.2017 */

alter table Orders MODIFY COLUMN activation_timestamp DATETIME DEFAULT NULL;
UPDATE Orders SET activation_timestamp = NULL WHERE CAST(activation_timestamp as char(20)) = '0000-00-00 00:00:00';
alter table OrderStatusHistory drop foreign key OrderStatusHistory_Order_id_fk;
alter table Participants drop foreign key Participants_Rooms_id_fk;
alter table Topics drop foreign key Topics_Orders_id_fk;
alter table Orders MODIFY COLUMN id VARCHAR(64);
alter table Topics MODIFY COLUMN `order` VARCHAR(64) NOT NULL;
alter table Participants MODIFY COLUMN `order` VARCHAR(64) NOT NULL;
alter table OrderStatusHistory MODIFY COLUMN `order` VARCHAR(64) NOT NULL;
alter table OrderStatusHistory add CONSTRAINT OrderStatusHistory_Order_id_fk FOREIGN KEY (`order`) REFERENCES Orders (id) ON DELETE CASCADE;
alter table Participants add CONSTRAINT Participants_Orders_id_fk FOREIGN KEY (`order`) REFERENCES Orders (id) ON DELETE CASCADE;
alter table Topics add CONSTRAINT Topics_Orders_id_fk FOREIGN KEY (`order`) REFERENCES Orders (id) ON DELETE CASCADE;
