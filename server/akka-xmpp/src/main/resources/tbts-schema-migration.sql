alter table Orders ADD COLUMN payment VARCHAR(128) default null;
ALTER TABLE Orders ADD COLUMN activation_timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE Participants ADD COLUMN timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE Tags ADD COLUMN icon VARCHAR(255);

SELECT `order`, score, room, SUM(duration) FROM (SELECT start.`order` as `order`, MIN(TO_SECONDS(finish.`timestamp`) - TO_SECONDS(start.`timestamp`)) as duration, finish.status FROM OrderStatusHistory as finish JOIN (SELECT id, `order`, `timestamp`, status as `from` FROM OrderStatusHistory WHERE status=1) AS start ON start.`order` = finish.`order` WHERE TO_SECONDS(finish.`timestamp`) - TO_SECONDS(start.`timestamp`) > 0 AND finish.status=3 GROUP BY start.id) AS durations JOIN Orders ON durations.`order` = Orders.id GROUP BY `order`;
