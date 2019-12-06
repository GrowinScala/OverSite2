ALTER TABLE `oversitedb`.`attachments` ADD content_type VARCHAR(255);

UPDATE `oversitedb`.`attachments` SET content_type = 'text/plain' WHERE attachment_id = '9e8d51c6-e903-4760-84a7-6d67e6dd80b2';
UPDATE `oversitedb`.`attachments` SET content_type = 'application/pdf' WHERE attachment_id = '1e419d9b-e604-4060-b28e-3bca42d106b6';
UPDATE `oversitedb`.`attachments` SET content_type = 'image/png' WHERE attachment_id = 'a83db7d5-8ae1-48ef-a1eb-e6a788173b4a';
UPDATE `oversitedb`.`attachments` SET content_type = 'image/jpeg' WHERE attachment_id = '15709f05-5142-44b7-93b7-1c7d8b5d327c';
UPDATE `oversitedb`.`attachments` SET content_type = 'audio/mpeg' WHERE attachment_id = 'a6f95aa1-a662-4a50-a0cd-d5379375a6c2';
UPDATE `oversitedb`.`attachments` SET content_type = 'video/mp4' WHERE attachment_id = 'b8c313cc-90a1-4f2f-81c6-e61a64fb0b16';