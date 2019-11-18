insert into addresses (address_id, address) values
	('02e196e2-631c-43a2-9e8d-cadf88a40ef6','beatriz@mail.com'),
	('56da0e3f-a8eb-4c90-a110-726410a44c4b','joao@mail.com'),
	('e29f98cf-01be-4ac9-ac46-66364efa57c3','valter@mail.com'),
	('ee4a4a6c-3bdc-423a-a610-185b10f6beef','pedrol@mail.com'),
	('68bfd7ed-bf34-4c56-99ff-8cf46b7f530d','pedroc@mail.com'),
	('55876dad-3155-4d0d-804c-0c1726961b63','rui@mail.com'),
	('455f800b-47eb-4643-afe6-ff6fa60e6522','margarida@mail.com'),
	('d8ba3989-26ab-45be-9ff6-cc4aca544316','ricardo@mail.com'),
	('e0288899-de22-46ad-bda7-1579861d44fc','ivo@mail.com'),
	('6af24197-9f1f-4aa3-9399-875bb9bccc41','joana@mail.com'),
	('6c11c217-73a1-405a-be14-2c77045260a7','daniel@mail.com');


insert into users (user_id, address_id, first_name, last_name) values
	('148a3b1b-8326-466d-8c27-1bd09b8378f3','02e196e2-631c-43a2-9e8d-cadf88a40ef6', 'Beatriz', 'Santos'),
	('adcd6348-658a-4866-93c5-7e6d32271d8d','56da0e3f-a8eb-4c90-a110-726410a44c4b', 'João', 'Simões'),
	('25689204-5a8e-453d-bfbc-4180ff0f97b9','e29f98cf-01be-4ac9-ac46-66364efa57c3', 'Valter', 'Fernandes'),
	('ef63108c-8128-4294-8346-bd9b5143ff22','ee4a4a6c-3bdc-423a-a610-185b10f6beef', 'Pedro', 'Luís'),
	('e598ee8e-b459-499f-94d1-d4f66d583264','68bfd7ed-bf34-4c56-99ff-8cf46b7f530d', 'Pedro', 'Correia'),
	('261c9094-6261-4704-bfd0-02821c235eff','55876dad-3155-4d0d-804c-0c1726961b63', 'Rui', 'Valente');


insert into chats (chat_id, subject) values
	('b87041c7-9044-41a0-99d7-666ce71bbe8d','Projeto Oversite2'),
	('83fa0c9a-1833-4a50-95ac-53e25a2d21bf','Laser Tag Quarta-feira'),
	('303c2b72-304e-4bac-84d7-385acb64a616','Vencimento'),
	('825ee397-f36e-4023-951e-89d6e43a8e7d','Location');


insert into emails (email_id, chat_id, body, date, sent) values
	('1f325a6a-d56c-400f-adf3-cdddf742f50f','b87041c7-9044-41a0-99d7-666ce71bbe8d', 'Olá Beatriz e João! Vamos começar o projeto.', '2019-06-17 10:00:00', 1),
	('a7402c9c-2eeb-46f5-adef-5a36d6fb0d0a','b87041c7-9044-41a0-99d7-666ce71bbe8d', 'Okay! Onde nos reunimos?', '2019-06-17 10:01:00', 1),
	('4e1bc286-39a8-48e6-94f1-d535821637ac','b87041c7-9044-41a0-99d7-666ce71bbe8d', 'Scrum room', '2019-06-17 10:02:00', 1),
	('5bcd148b-c4d6-4485-9d04-e49b26ebff9c','b87041c7-9044-41a0-99d7-666ce71bbe8d', 'Valter, tive um imprevisto. Chego às 10h30', '2019-06-17 10:03:00', 1),
	('b46a565a-12d3-4278-9716-fec2f6673a36','b87041c7-9044-41a0-99d7-666ce71bbe8d', 'Okay, não há problema.', '2019-06-17 10:04:00', 1),
	('5ff6f613-51a0-4cb9-9337-eb6952a03180','b87041c7-9044-41a0-99d7-666ce71bbe8d', 'Estou a chegar!', '2019-06-17 10:05:00', 0),

	('07181ad2-4b49-4bd8-98ee-2b559e97ffc4','83fa0c9a-1833-4a50-95ac-53e25a2d21bf', 'Vamos ao laser tag na quarta?', '2019-06-19 11:00:00', 1),
	('2640a600-7968-4725-8a1f-96a034d6b560','83fa0c9a-1833-4a50-95ac-53e25a2d21bf', 'Bora!', '2019-06-19 11:01:00', 1),
	('03a26256-9f7a-4de7-a4be-572f34a3e6a0','83fa0c9a-1833-4a50-95ac-53e25a2d21bf', 'Valter, não posso...', '2019-06-19 11:02:00', 1),
	('5e0033af-e887-4dec-8442-8cc56a861451','83fa0c9a-1833-4a50-95ac-53e25a2d21bf', 'A que horas?', '2019-06-19 11:03:00', 1),
	('ae553aef-90f7-4865-83ea-61060cbdea6d','83fa0c9a-1833-4a50-95ac-53e25a2d21bf', '18h00', '2019-06-19 11:04:00', 1),
	('65eeacc9-8f6f-4cc7-8f66-0f468637fcb1','83fa0c9a-1833-4a50-95ac-53e25a2d21bf', 'Também vou!', '2019-06-19 11:05:00', 0),
	('ec7841c0-58ad-4afc-803d-0fb8f6941221','83fa0c9a-1833-4a50-95ac-53e25a2d21bf', 'Talvez vá', '2019-06-19 11:06:00', 0),

	('75de343b-50f8-40a9-9fe5-4af1139e51a2','303c2b72-304e-4bac-84d7-385acb64a616', 'Olá Beatriz e João! Já receberam o vosso vencimento?', '2019-06-27 11:00:00', 1),
	('a4e4d8f5-ff60-40e1-a2d3-330088412f81','303c2b72-304e-4bac-84d7-385acb64a616', 'Sim!', '2019-06-27 11:01:00', 1),
	('d0de5388-8808-40d7-9c8b-38bd1059662d','303c2b72-304e-4bac-84d7-385acb64a616', 'Não...', '2019-06-27 11:02:00', 1),
	('3ab53906-5353-4a58-a102-c81128aa6ddb','303c2b72-304e-4bac-84d7-385acb64a616', 'Já vou resolver o assunto!', '2019-06-27 11:03:00', 1),
	('f203c270-5f37-4437-956a-3cf478f5f28f','303c2b72-304e-4bac-84d7-385acb64a616', 'Okay, obrigada!', '2019-06-27 11:04:00', 0),

	('42508cff-a4cf-47e4-9b7d-db91e010b87a','825ee397-f36e-4023-951e-89d6e43a8e7d', 'Where are you?', '2019-06-17 10:00:00', 1),
	('fe4ff891-144a-4f61-af35-6d4a5ec76314','825ee397-f36e-4023-951e-89d6e43a8e7d', 'Here', '2019-06-17 10:06:00', 0);


insert into email_addresses (email_address_id, email_id, chat_id, address_id, participant_type) values
	('19631e5d-7a77-4839-b2bd-93403e2b405f','1f325a6a-d56c-400f-adf3-cdddf742f50f','b87041c7-9044-41a0-99d7-666ce71bbe8d','e29f98cf-01be-4ac9-ac46-66364efa57c3', 'from'),
	('98a27848-2636-4642-9ad0-83833cc5bed0','1f325a6a-d56c-400f-adf3-cdddf742f50f','b87041c7-9044-41a0-99d7-666ce71bbe8d','02e196e2-631c-43a2-9e8d-cadf88a40ef6', 'to'),
	('36a306a0-9083-4326-80be-8648529cf154','1f325a6a-d56c-400f-adf3-cdddf742f50f','b87041c7-9044-41a0-99d7-666ce71bbe8d','56da0e3f-a8eb-4c90-a110-726410a44c4b', 'to'),

	('13ad8518-d8c6-43dc-825e-01eb15ebc7ff','a7402c9c-2eeb-46f5-adef-5a36d6fb0d0a','b87041c7-9044-41a0-99d7-666ce71bbe8d','56da0e3f-a8eb-4c90-a110-726410a44c4b', 'from'),
	('9a08a5a1-0f12-411b-9b42-e9e60a4d85ff','a7402c9c-2eeb-46f5-adef-5a36d6fb0d0a','b87041c7-9044-41a0-99d7-666ce71bbe8d','02e196e2-631c-43a2-9e8d-cadf88a40ef6', 'to'),
	('3cf14295-792e-4c2d-894a-37416b642006','a7402c9c-2eeb-46f5-adef-5a36d6fb0d0a','b87041c7-9044-41a0-99d7-666ce71bbe8d','e29f98cf-01be-4ac9-ac46-66364efa57c3', 'to'),

	('9fde0596-d6f3-4170-9979-65d8cef6d692','4e1bc286-39a8-48e6-94f1-d535821637ac','b87041c7-9044-41a0-99d7-666ce71bbe8d','e29f98cf-01be-4ac9-ac46-66364efa57c3', 'from'),
	('01988f52-eaae-41e4-833f-9370b4e6e631','4e1bc286-39a8-48e6-94f1-d535821637ac','b87041c7-9044-41a0-99d7-666ce71bbe8d','02e196e2-631c-43a2-9e8d-cadf88a40ef6', 'to'),
	('5c12f47d-d7d4-41ad-98c2-c0e5c531c157','4e1bc286-39a8-48e6-94f1-d535821637ac','b87041c7-9044-41a0-99d7-666ce71bbe8d','56da0e3f-a8eb-4c90-a110-726410a44c4b', 'to'),

	('d62ac2f4-2af9-440b-89e0-478f50261d78','5bcd148b-c4d6-4485-9d04-e49b26ebff9c','b87041c7-9044-41a0-99d7-666ce71bbe8d','02e196e2-631c-43a2-9e8d-cadf88a40ef6', 'from'),
	('371c18e8-b231-4321-bfea-27ee9d13c1ab','5bcd148b-c4d6-4485-9d04-e49b26ebff9c','b87041c7-9044-41a0-99d7-666ce71bbe8d','e29f98cf-01be-4ac9-ac46-66364efa57c3', 'to'),

	('146704e9-f2f8-4dae-8ef5-e89b9e986116','b46a565a-12d3-4278-9716-fec2f6673a36','b87041c7-9044-41a0-99d7-666ce71bbe8d','e29f98cf-01be-4ac9-ac46-66364efa57c3', 'from'),
	('0b098be6-a06a-43d2-9e9e-40adb441db53','b46a565a-12d3-4278-9716-fec2f6673a36','b87041c7-9044-41a0-99d7-666ce71bbe8d','02e196e2-631c-43a2-9e8d-cadf88a40ef6', 'to'),

	('6c6d724a-7f69-4338-894f-4f6cb3ef5713','5ff6f613-51a0-4cb9-9337-eb6952a03180','b87041c7-9044-41a0-99d7-666ce71bbe8d','02e196e2-631c-43a2-9e8d-cadf88a40ef6', 'from'),
	('c73558bc-7d46-46eb-b5e6-cd49b4417623','5ff6f613-51a0-4cb9-9337-eb6952a03180','b87041c7-9044-41a0-99d7-666ce71bbe8d','e29f98cf-01be-4ac9-ac46-66364efa57c3', 'to'),

	('1081402f-a39b-4946-8c67-1b9137fe1a52','07181ad2-4b49-4bd8-98ee-2b559e97ffc4','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','e29f98cf-01be-4ac9-ac46-66364efa57c3', 'from'),
	('68fb25a1-4cf6-4f2e-97a5-74cfa1ee8650','07181ad2-4b49-4bd8-98ee-2b559e97ffc4','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','02e196e2-631c-43a2-9e8d-cadf88a40ef6', 'to'),
	('ad2ab8f3-d3a4-4351-8161-74d1b8a43512','07181ad2-4b49-4bd8-98ee-2b559e97ffc4','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','56da0e3f-a8eb-4c90-a110-726410a44c4b', 'to'),
	('88b5cd36-10e0-42c8-b78e-e81bc036228c','07181ad2-4b49-4bd8-98ee-2b559e97ffc4','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','ee4a4a6c-3bdc-423a-a610-185b10f6beef', 'to'),
	('03672c22-4e5b-4c90-bd1d-6dc56b4d55c9','07181ad2-4b49-4bd8-98ee-2b559e97ffc4','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','68bfd7ed-bf34-4c56-99ff-8cf46b7f530d', 'to'),
	('3c6839b3-e9a6-4311-9f78-cecebc938e1f','07181ad2-4b49-4bd8-98ee-2b559e97ffc4','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','55876dad-3155-4d0d-804c-0c1726961b63', 'to'),
	('8d9ab5c4-eba1-42e2-abc8-890d5309cce6','07181ad2-4b49-4bd8-98ee-2b559e97ffc4','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','455f800b-47eb-4643-afe6-ff6fa60e6522', 'to'),
	('175e8ffd-56e0-4d18-b4c8-7881a7d16dc1','07181ad2-4b49-4bd8-98ee-2b559e97ffc4','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','d8ba3989-26ab-45be-9ff6-cc4aca544316', 'to'),
	('fb48bb05-907d-4d07-983a-1c20dc62032b','07181ad2-4b49-4bd8-98ee-2b559e97ffc4','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','e0288899-de22-46ad-bda7-1579861d44fc', 'to'),
	('8cd9581a-d40a-4a58-8438-a5994be28e68','07181ad2-4b49-4bd8-98ee-2b559e97ffc4','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','6af24197-9f1f-4aa3-9399-875bb9bccc41', 'cc'),
	('40c4f3e3-0cb3-44b4-b296-b17d3dd744b6','07181ad2-4b49-4bd8-98ee-2b559e97ffc4','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','6c11c217-73a1-405a-be14-2c77045260a7', 'bcc'),

	('42317eef-2813-4b18-9cee-e61d6db846c9','2640a600-7968-4725-8a1f-96a034d6b560','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','455f800b-47eb-4643-afe6-ff6fa60e6522', 'from'),
	('9c22ace3-2132-4805-ac84-0e8b22a27b61','2640a600-7968-4725-8a1f-96a034d6b560','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','02e196e2-631c-43a2-9e8d-cadf88a40ef6', 'to'),
	('fc40a748-f46e-4688-8614-ba32190657b4','2640a600-7968-4725-8a1f-96a034d6b560','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','56da0e3f-a8eb-4c90-a110-726410a44c4b', 'to'),
	('5258560e-572a-4791-842f-d37fd32d557b','2640a600-7968-4725-8a1f-96a034d6b560','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','e29f98cf-01be-4ac9-ac46-66364efa57c3', 'to'),
	('425c093b-b83c-4818-88ff-29b96cd2af2b','2640a600-7968-4725-8a1f-96a034d6b560','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','ee4a4a6c-3bdc-423a-a610-185b10f6beef', 'to'),
	('b7880f35-1c11-4684-bb14-41248e288c74','2640a600-7968-4725-8a1f-96a034d6b560','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','68bfd7ed-bf34-4c56-99ff-8cf46b7f530d', 'to'),
	('4d927355-e32e-4f30-8f46-3c9dd37e4264','2640a600-7968-4725-8a1f-96a034d6b560','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','55876dad-3155-4d0d-804c-0c1726961b63', 'to'),
	('96994cb7-5306-4181-8505-7bd49662e3e7','2640a600-7968-4725-8a1f-96a034d6b560','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','d8ba3989-26ab-45be-9ff6-cc4aca544316', 'to'),
	('e270ecd2-84fa-4619-a82f-0628375b8377','2640a600-7968-4725-8a1f-96a034d6b560','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','e0288899-de22-46ad-bda7-1579861d44fc', 'to'),

	('d20b4d59-500c-4df0-84ee-afd30bec56bf','03a26256-9f7a-4de7-a4be-572f34a3e6a0','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','02e196e2-631c-43a2-9e8d-cadf88a40ef6', 'from'),
	('cf4251b9-895f-4bee-8f8d-be438c10ff3a','03a26256-9f7a-4de7-a4be-572f34a3e6a0','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','e29f98cf-01be-4ac9-ac46-66364efa57c3', 'to'),

	('71be38aa-3f04-4836-8930-723ec1a8510d','5e0033af-e887-4dec-8442-8cc56a861451','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','56da0e3f-a8eb-4c90-a110-726410a44c4b', 'from'),
	('62004785-8f36-4ce2-9878-fc644317fd64','5e0033af-e887-4dec-8442-8cc56a861451','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','02e196e2-631c-43a2-9e8d-cadf88a40ef6', 'to'),
	('f00846f9-c52a-4e39-b903-55fc09245936','5e0033af-e887-4dec-8442-8cc56a861451','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','e29f98cf-01be-4ac9-ac46-66364efa57c3', 'to'),
	('b61e19c1-c178-4197-9052-12c52d4e1bfe','5e0033af-e887-4dec-8442-8cc56a861451','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','ee4a4a6c-3bdc-423a-a610-185b10f6beef', 'to'),
	('dc377665-632f-4e67-a8f9-3176398b3c3e','5e0033af-e887-4dec-8442-8cc56a861451','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','68bfd7ed-bf34-4c56-99ff-8cf46b7f530d', 'to'),
	('d5951941-7bfb-419e-be29-9d31e9a9fc1b','5e0033af-e887-4dec-8442-8cc56a861451','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','55876dad-3155-4d0d-804c-0c1726961b63', 'to'),
	('7454e404-a661-4cf7-8f66-19be20fdd353','5e0033af-e887-4dec-8442-8cc56a861451','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','455f800b-47eb-4643-afe6-ff6fa60e6522', 'to'),
	('e951c241-60d9-4448-a570-7c58a9a3b755','5e0033af-e887-4dec-8442-8cc56a861451','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','d8ba3989-26ab-45be-9ff6-cc4aca544316', 'to'),
	('dcdff30c-5da6-48cc-8326-0ca8c43a2c1b','5e0033af-e887-4dec-8442-8cc56a861451','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','e0288899-de22-46ad-bda7-1579861d44fc', 'to'),

	('ad78d374-34fc-46e0-baf2-9b477cc721d0','ae553aef-90f7-4865-83ea-61060cbdea6d','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','e29f98cf-01be-4ac9-ac46-66364efa57c3', 'from'),
	('4ff38b08-3f5b-484a-995f-542337b106f8','ae553aef-90f7-4865-83ea-61060cbdea6d','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','02e196e2-631c-43a2-9e8d-cadf88a40ef6', 'to'),
	('cf47a604-0ed1-4491-b538-c148ad94a45d','ae553aef-90f7-4865-83ea-61060cbdea6d','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','56da0e3f-a8eb-4c90-a110-726410a44c4b', 'to'),
	('e97e9f34-dc58-479d-88b4-a20690f2314f','ae553aef-90f7-4865-83ea-61060cbdea6d','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','ee4a4a6c-3bdc-423a-a610-185b10f6beef', 'to'),
	('dd8b3868-792f-422a-8ecb-5aa826db5200','ae553aef-90f7-4865-83ea-61060cbdea6d','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','68bfd7ed-bf34-4c56-99ff-8cf46b7f530d', 'to'),
	('cb5ac665-7fd8-47e6-a2df-ded5c8a9f96c','ae553aef-90f7-4865-83ea-61060cbdea6d','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','55876dad-3155-4d0d-804c-0c1726961b63', 'to'),
	('ba264939-e96a-4366-8b3b-e18080720aef','ae553aef-90f7-4865-83ea-61060cbdea6d','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','455f800b-47eb-4643-afe6-ff6fa60e6522', 'to'),
	('c1761430-e179-4432-8ecc-b0e537b2bb02','ae553aef-90f7-4865-83ea-61060cbdea6d','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','d8ba3989-26ab-45be-9ff6-cc4aca544316', 'to'),
	('7e088fe7-cc05-432c-b023-d0468f946f6d','ae553aef-90f7-4865-83ea-61060cbdea6d','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','e0288899-de22-46ad-bda7-1579861d44fc', 'to'),

	('20bcdd86-b405-4730-b41a-207f7692686d','65eeacc9-8f6f-4cc7-8f66-0f468637fcb1','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','ee4a4a6c-3bdc-423a-a610-185b10f6beef', 'from'),
	('dd5970fd-2186-4e35-a0d0-5eb0e13451ac','65eeacc9-8f6f-4cc7-8f66-0f468637fcb1','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','02e196e2-631c-43a2-9e8d-cadf88a40ef6', 'to'),
	('99d59b67-b110-4e76-90bc-190c7b3d9615','65eeacc9-8f6f-4cc7-8f66-0f468637fcb1','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','56da0e3f-a8eb-4c90-a110-726410a44c4b', 'to'),
	('5975ddcd-b88a-40ee-9587-0f7564291c83','65eeacc9-8f6f-4cc7-8f66-0f468637fcb1','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','e29f98cf-01be-4ac9-ac46-66364efa57c3', 'to'),
	('89d95149-b592-4c71-856d-b12a15dfb38b','65eeacc9-8f6f-4cc7-8f66-0f468637fcb1','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','68bfd7ed-bf34-4c56-99ff-8cf46b7f530d', 'to'),
	('c97185e6-f2ab-49aa-afec-fb74021ab61f','65eeacc9-8f6f-4cc7-8f66-0f468637fcb1','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','55876dad-3155-4d0d-804c-0c1726961b63', 'to'),
	('adda47d7-740c-4c2d-bcf0-ea2c768bf4bf','65eeacc9-8f6f-4cc7-8f66-0f468637fcb1','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','455f800b-47eb-4643-afe6-ff6fa60e6522', 'to'),
	('1040a7c5-153d-4fd8-a466-d9f51362912e','65eeacc9-8f6f-4cc7-8f66-0f468637fcb1','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','d8ba3989-26ab-45be-9ff6-cc4aca544316', 'to'),
	('6fa83636-5b6a-4109-920f-b3e081d36ff1','65eeacc9-8f6f-4cc7-8f66-0f468637fcb1','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','e0288899-de22-46ad-bda7-1579861d44fc', 'to'),

	('f8ce897e-b889-4398-8c80-c9409c4bc802','ec7841c0-58ad-4afc-803d-0fb8f6941221','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','68bfd7ed-bf34-4c56-99ff-8cf46b7f530d', 'from'),
	('8ec2679a-3b0b-4b1b-b1e0-34c27621d47b','ec7841c0-58ad-4afc-803d-0fb8f6941221','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','e29f98cf-01be-4ac9-ac46-66364efa57c3', 'to'),

	('94c92e59-05ae-4b3e-a854-a7581db1c17b','75de343b-50f8-40a9-9fe5-4af1139e51a2','303c2b72-304e-4bac-84d7-385acb64a616','6af24197-9f1f-4aa3-9399-875bb9bccc41', 'from'),
	('c601ff67-d98c-441e-a9cd-190d8111252b','75de343b-50f8-40a9-9fe5-4af1139e51a2','303c2b72-304e-4bac-84d7-385acb64a616','02e196e2-631c-43a2-9e8d-cadf88a40ef6', 'to'),
	('a00fd52e-8945-4535-9cb1-db1bd50d48f2','75de343b-50f8-40a9-9fe5-4af1139e51a2','303c2b72-304e-4bac-84d7-385acb64a616','56da0e3f-a8eb-4c90-a110-726410a44c4b', 'to'),

	('259007f1-7c5d-4098-83b8-ae2f505a10bd','a4e4d8f5-ff60-40e1-a2d3-330088412f81','303c2b72-304e-4bac-84d7-385acb64a616','56da0e3f-a8eb-4c90-a110-726410a44c4b', 'from'),
	('2a024dcf-90d3-4100-86b3-3979d070a1d7','a4e4d8f5-ff60-40e1-a2d3-330088412f81','303c2b72-304e-4bac-84d7-385acb64a616','6af24197-9f1f-4aa3-9399-875bb9bccc41', 'to'),

	('175b6f0d-89b3-492c-a173-c75fbc0f59a8','d0de5388-8808-40d7-9c8b-38bd1059662d','303c2b72-304e-4bac-84d7-385acb64a616','02e196e2-631c-43a2-9e8d-cadf88a40ef6', 'from'),
	('7b99c404-d3da-476a-b547-a2dead6a1ae0','d0de5388-8808-40d7-9c8b-38bd1059662d','303c2b72-304e-4bac-84d7-385acb64a616','6af24197-9f1f-4aa3-9399-875bb9bccc41', 'to'),

	('bc004d4f-472f-4c5a-a93e-77168b818293','3ab53906-5353-4a58-a102-c81128aa6ddb','303c2b72-304e-4bac-84d7-385acb64a616','6af24197-9f1f-4aa3-9399-875bb9bccc41', 'from'),
	('0206778e-df24-40a6-b084-aa2c62268005','3ab53906-5353-4a58-a102-c81128aa6ddb','303c2b72-304e-4bac-84d7-385acb64a616','02e196e2-631c-43a2-9e8d-cadf88a40ef6', 'to'),

	('b2af5ae9-6dcb-4e58-8057-ae4bc8175822','f203c270-5f37-4437-956a-3cf478f5f28f','303c2b72-304e-4bac-84d7-385acb64a616','02e196e2-631c-43a2-9e8d-cadf88a40ef6', 'from'),
	('0ba9d3a6-4aec-4546-bee8-c856d57bcf6f','f203c270-5f37-4437-956a-3cf478f5f28f','303c2b72-304e-4bac-84d7-385acb64a616','6af24197-9f1f-4aa3-9399-875bb9bccc41', 'to'),

	('363c14e9-13cd-4cb0-83a5-360a00a70fda', '42508cff-a4cf-47e4-9b7d-db91e010b87a','825ee397-f36e-4023-951e-89d6e43a8e7d','56da0e3f-a8eb-4c90-a110-726410a44c4b', 'from'),
	('bab3c756-2656-4326-9aaa-66f86065099a', '42508cff-a4cf-47e4-9b7d-db91e010b87a','825ee397-f36e-4023-951e-89d6e43a8e7d','02e196e2-631c-43a2-9e8d-cadf88a40ef6', 'to'),
	('e58cc6ec-bb0d-46f9-9336-0952c75eb92e', '42508cff-a4cf-47e4-9b7d-db91e010b87a','825ee397-f36e-4023-951e-89d6e43a8e7d','ee4a4a6c-3bdc-423a-a610-185b10f6beef', 'bcc'),
	('0aab6f1c-154b-4966-b74e-153ce6d9a20a', '42508cff-a4cf-47e4-9b7d-db91e010b87a','825ee397-f36e-4023-951e-89d6e43a8e7d','68bfd7ed-bf34-4c56-99ff-8cf46b7f530d', 'cc'),

	('4df2297f-c63d-43b4-ac4d-57612faacf75', 'fe4ff891-144a-4f61-af35-6d4a5ec76314','825ee397-f36e-4023-951e-89d6e43a8e7d','02e196e2-631c-43a2-9e8d-cadf88a40ef6', 'from'),
	('d4476a39-1542-473a-9f63-881481614001', 'fe4ff891-144a-4f61-af35-6d4a5ec76314','825ee397-f36e-4023-951e-89d6e43a8e7d','56da0e3f-a8eb-4c90-a110-726410a44c4b', 'to');


insert into attachments (attachment_id, email_id, filename, path) values
	('9e8d51c6-e903-4760-84a7-6d67e6dd80b2','1f325a6a-d56c-400f-adf3-cdddf742f50f','akdnfdns.txt','C:\\Oversite\\UploadDir\\e649654b-5b48-41b4-ae94-d4580ee3a9c4'),
	('1e419d9b-e604-4060-b28e-3bca42d106b6','1f325a6a-d56c-400f-adf3-cdddf742f50f','afjfjdfn.pdf','C:\\Oversite\\UploadDir\\9c1612e4-793c-4d43-8499-c830cbb67ccb'),
	('a83db7d5-8ae1-48ef-a1eb-e6a788173b4a','1f325a6a-d56c-400f-adf3-cdddf742f50f','kjdsffkd.png','C:\\Oversite\\UploadDir\\6eb576fe-9b46-416f-9a5c-648be945b657'),
	('15709f05-5142-44b7-93b7-1c7d8b5d327c','07181ad2-4b49-4bd8-98ee-2b559e97ffc4','adfbjdse.jpg','C:\\Oversite\\UploadDir\\5881f579-ddfc-4fc3-982c-5a3687528c57'),
	('a6f95aa1-a662-4a50-a0cd-d5379375a6c2','a4e4d8f5-ff60-40e1-a2d3-330088412f81','tyghsnds.mp3','C:\\Oversite\\UploadDir\\fac0d3f3-ca6e-4191-8c9a-6455babfefeb'),
	('b8c313cc-90a1-4f2f-81c6-e61a64fb0b16','fe4ff891-144a-4f61-af35-6d4a5ec76314','nbvadaid.mp4','C:\\Oversite\\UploadDir\\aab2cbff-b4ef-4742-95df-adf9d344bb43');


insert into user_chats (user_chat_id, user_id, chat_id, inbox, sent, draft, trash) values
	('39ec4eed-e3cd-4088-b053-38726b6643ad','148a3b1b-8326-466d-8c27-1bd09b8378f3','b87041c7-9044-41a0-99d7-666ce71bbe8d', 1, 1, 1, 0),
	('38ff25ec-16e3-4865-b55d-d31c7c3cbbf1','adcd6348-658a-4866-93c5-7e6d32271d8d','b87041c7-9044-41a0-99d7-666ce71bbe8d', 1, 1, 0, 0),
	('c04038f4-bd3b-4299-9a3d-b155d32bba3d','25689204-5a8e-453d-bfbc-4180ff0f97b9','b87041c7-9044-41a0-99d7-666ce71bbe8d', 1, 1, 0, 0),
	('25a88bd6-9df7-4454-97de-24903157f994','ef63108c-8128-4294-8346-bd9b5143ff22','b87041c7-9044-41a0-99d7-666ce71bbe8d', 1, 0, 0, 0),
	('3149d1e9-49f5-430a-bac5-455b657eca22','e598ee8e-b459-499f-94d1-d4f66d583264','b87041c7-9044-41a0-99d7-666ce71bbe8d', 1, 0, 0, 0),
	('879c2b2f-05da-4a53-87b1-4f16c7d3b58a','261c9094-6261-4704-bfd0-02821c235eff','b87041c7-9044-41a0-99d7-666ce71bbe8d', 1, 0, 0, 0),

	('806e3f0e-c237-4e0b-8d0b-f8fd08cd6654','148a3b1b-8326-466d-8c27-1bd09b8378f3','83fa0c9a-1833-4a50-95ac-53e25a2d21bf', 1, 1, 0, 0),
	('a68589ca-9873-4c24-93b9-deb2c28e4e6c','adcd6348-658a-4866-93c5-7e6d32271d8d','83fa0c9a-1833-4a50-95ac-53e25a2d21bf', 1, 0, 1, 0),
	('d19cc4ff-5f1b-4e2f-9bc0-d0526721d7ae','25689204-5a8e-453d-bfbc-4180ff0f97b9','83fa0c9a-1833-4a50-95ac-53e25a2d21bf', 1, 1, 0, 0),
	('e4306fcd-9b10-4a07-81af-34ce09ebc857','ef63108c-8128-4294-8346-bd9b5143ff22','83fa0c9a-1833-4a50-95ac-53e25a2d21bf', 1, 1, 1, 0),
	('62dd7bd9-5462-48bb-b3e8-f0a692df3f5e','e598ee8e-b459-499f-94d1-d4f66d583264','83fa0c9a-1833-4a50-95ac-53e25a2d21bf', 1, 1, 1, 0),
	('8f553002-de44-42d5-b0de-a8b6c3fcb621','261c9094-6261-4704-bfd0-02821c235eff','83fa0c9a-1833-4a50-95ac-53e25a2d21bf', 1, 0, 0, 0),

	('d67a3383-a838-414f-924d-ffc1b3448986','148a3b1b-8326-466d-8c27-1bd09b8378f3','303c2b72-304e-4bac-84d7-385acb64a616', 1, 1, 1, 0),
	('b881f291-6d57-46bf-9eb2-4d2fe6f9476a','adcd6348-658a-4866-93c5-7e6d32271d8d','303c2b72-304e-4bac-84d7-385acb64a616', 1, 1, 0, 0),
	('68ccfa80-0226-4ff6-8b76-33aa60cd4aa0','25689204-5a8e-453d-bfbc-4180ff0f97b9','303c2b72-304e-4bac-84d7-385acb64a616', 1, 0, 0, 0),
	('ec4344bb-d68a-4f0b-b407-eb4a7a83eb5d','ef63108c-8128-4294-8346-bd9b5143ff22','303c2b72-304e-4bac-84d7-385acb64a616', 1, 0, 0, 0),

	('853c4fce-845d-4ac5-b3be-6c6dbafa1989','148a3b1b-8326-466d-8c27-1bd09b8378f3','825ee397-f36e-4023-951e-89d6e43a8e7d', 1, 0, 1, 0),
	('a437d9b6-143f-4e17-9ac5-266fb80f425c','adcd6348-658a-4866-93c5-7e6d32271d8d','825ee397-f36e-4023-951e-89d6e43a8e7d', 0, 1, 0, 0),
	('4918c199-a788-44ca-ae6b-58f2123240c1','25689204-5a8e-453d-bfbc-4180ff0f97b9','825ee397-f36e-4023-951e-89d6e43a8e7d', 1, 0, 0, 0),
	('0090bc5e-a259-43c1-bc31-0b48cb3e0393','ef63108c-8128-4294-8346-bd9b5143ff22','825ee397-f36e-4023-951e-89d6e43a8e7d', 1, 0, 0, 0),
	('3c04e7d0-11e2-4281-b06a-1aae6bde4113','e598ee8e-b459-499f-94d1-d4f66d583264','825ee397-f36e-4023-951e-89d6e43a8e7d', 1, 0, 0, 0),
	('d0f9b6bc-1544-4565-b592-0d53bf51dd5d','261c9094-6261-4704-bfd0-02821c235eff','825ee397-f36e-4023-951e-89d6e43a8e7d', 1, 0, 0, 0);


insert into oversights (oversight_id, chat_id, overseer_id, oversee_id) values
	('b4c87d59-7f96-471b-b992-19f256540ed1','b87041c7-9044-41a0-99d7-666ce71bbe8d','ef63108c-8128-4294-8346-bd9b5143ff22','25689204-5a8e-453d-bfbc-4180ff0f97b9'),
	('c70e5b54-653c-48cd-bb36-5ea85b130859','b87041c7-9044-41a0-99d7-666ce71bbe8d','e598ee8e-b459-499f-94d1-d4f66d583264','25689204-5a8e-453d-bfbc-4180ff0f97b9'),
	('bfbc536c-fc6c-44c8-b34f-e6e8bed78a0d','b87041c7-9044-41a0-99d7-666ce71bbe8d','261c9094-6261-4704-bfd0-02821c235eff','25689204-5a8e-453d-bfbc-4180ff0f97b9'),

	('f383d990-ab03-4eba-83b2-2f5a3e847339','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','adcd6348-658a-4866-93c5-7e6d32271d8d','148a3b1b-8326-466d-8c27-1bd09b8378f3'),
	('ab83f5a5-804f-4def-924e-3fac9e6f8698','83fa0c9a-1833-4a50-95ac-53e25a2d21bf','ef63108c-8128-4294-8346-bd9b5143ff22','148a3b1b-8326-466d-8c27-1bd09b8378f3'),

	('71e42cee-7402-4cab-84a7-b6202a9da3be','303c2b72-304e-4bac-84d7-385acb64a616','25689204-5a8e-453d-bfbc-4180ff0f97b9','148a3b1b-8326-466d-8c27-1bd09b8378f3'),
	('200ed11e-dce5-49de-9b88-2a5a1b8a67cb','303c2b72-304e-4bac-84d7-385acb64a616','25689204-5a8e-453d-bfbc-4180ff0f97b9','adcd6348-658a-4866-93c5-7e6d32271d8d'),
	('67d48292-6e93-43c5-ab30-b150873bd7da','303c2b72-304e-4bac-84d7-385acb64a616','ef63108c-8128-4294-8346-bd9b5143ff22','148a3b1b-8326-466d-8c27-1bd09b8378f3'),

	('3c8d875e-fccc-4afe-9e05-401bee445adb','825ee397-f36e-4023-951e-89d6e43a8e7d','25689204-5a8e-453d-bfbc-4180ff0f97b9','148a3b1b-8326-466d-8c27-1bd09b8378f3'),
	('2ce4b40c-614f-4a42-8b6f-a75c056712e7','825ee397-f36e-4023-951e-89d6e43a8e7d','261c9094-6261-4704-bfd0-02821c235eff','ef63108c-8128-4294-8346-bd9b5143ff22');

insert into tokens (token_id, token) values
	('1598e0a3-0c22-47b3-9a98-b48fb81f5f31','eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOiIxNDhhM2IxYi04MzI2LTQ2NmQtOGMyNy0xYmQwOWI4Mzc4ZjMiLCJleHBpcmF0aW9uRGF0ZSI6IjIwMjAtMDktMzBUMTA6MzM6MjguMzk5In0.CGmtQY93zvcF1BJOoiuVUmxaKGsZRKRBJ9NZoJ0F8vI'),
	('6329b2f5-44c1-49ab-a2c0-e7f61dbb7f24','eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOiJhZGNkNjM0OC02NThhLTQ4NjYtOTNjNS03ZTZkMzIyNzFkOGQiLCJleHBpcmF0aW9uRGF0ZSI6IjIwMjAtMDktMzBUMTA6MzU6NDUuMDk3In0.tdiGY2VGTSeIeKavyf6otmkenzBbhyB2yzLmq9-jsvg'),
	('a90fdd6e-28bf-49f7-91ac-a3efeccf0e85','eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOiIyNTY4OTIwNC01YThlLTQ1M2QtYmZiYy00MTgwZmYwZjk3YjkiLCJleHBpcmF0aW9uRGF0ZSI6IjIwMjAtMDktMzBUMTA6Mzc6MzAuMTU1In0.vfRw1y8AhkiWpwkaVzEEGK9T01D-hrP655jWN5nE_Yk'),
	('21c6daa6-f169-46c3-99df-a8fd7921fb1e','eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOiJlZjYzMTA4Yy04MTI4LTQyOTQtODM0Ni1iZDliNTE0M2ZmMjIiLCJleHBpcmF0aW9uRGF0ZSI6IjIwMjAtMDktMzBUMTA6Mzg6MTEuMjQyIn0.OD7qBckRPboZH2PMhMtHA2htQOY7lbyvERtytKrhy8Y'),
	('a8623ebf-f6e8-4b48-90cf-7f844fbec498','eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOiJlNTk4ZWU4ZS1iNDU5LTQ5OWYtOTRkMS1kNGY2NmQ1ODMyNjQiLCJleHBpcmF0aW9uRGF0ZSI6IjIwMjAtMDktMzBUMTA6Mzk6MDUuODM5In0.UYPNny0hs8Mcpj916oxuyMJqiSBcWg72l_FSdCbeDps'),
	('0a462b44-628a-45db-91cb-e90f82bad451','eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOiIyNjFjOTA5NC02MjYxLTQ3MDQtYmZkMC0wMjgyMWMyMzVlZmYiLCJleHBpcmF0aW9uRGF0ZSI6IjIwMjAtMDktMzBUMTA6NDA6MjEuNDE3In0.RlxEMhuwmiZdTLq61gKaQ1Kh6y7mmP1HOPG0-MlI9Ws');

insert into passwords (password_id, user_id, password, token_id) values
	('0540cf4e-2230-4576-82f8-8f9e76aad624','148a3b1b-8326-466d-8c27-1bd09b8378f3','$2a$10$vo0wirI9EV1N57COPokxzukI7CNK2Hnk9vMqeBQPE.MI5kaWxG.Wm','1598e0a3-0c22-47b3-9a98-b48fb81f5f31'),
	('f94afa4a-1149-43a4-94fd-d9f20a600396','adcd6348-658a-4866-93c5-7e6d32271d8d','$2a$10$vo0wirI9EV1N57COPokxzukI7CNK2Hnk9vMqeBQPE.MI5kaWxG.Wm','6329b2f5-44c1-49ab-a2c0-e7f61dbb7f24'),
	('1c93b5a2-5a58-4f39-be9b-f1525932af26','25689204-5a8e-453d-bfbc-4180ff0f97b9','$2a$10$vo0wirI9EV1N57COPokxzukI7CNK2Hnk9vMqeBQPE.MI5kaWxG.Wm','a90fdd6e-28bf-49f7-91ac-a3efeccf0e85'),
	('c44a7309-a7e1-475b-8344-04286320e5c0','ef63108c-8128-4294-8346-bd9b5143ff22','$2a$10$vo0wirI9EV1N57COPokxzukI7CNK2Hnk9vMqeBQPE.MI5kaWxG.Wm','21c6daa6-f169-46c3-99df-a8fd7921fb1e'),
	('75e6febd-7c68-44a3-b5dc-8ca4b268c473','e598ee8e-b459-499f-94d1-d4f66d583264','$2a$10$vo0wirI9EV1N57COPokxzukI7CNK2Hnk9vMqeBQPE.MI5kaWxG.Wm','a8623ebf-f6e8-4b48-90cf-7f844fbec498'),
	('fd6685fc-b63d-4ada-b61e-9b9bec6736d4','261c9094-6261-4704-bfd0-02821c235eff','$2a$10$vo0wirI9EV1N57COPokxzukI7CNK2Hnk9vMqeBQPE.MI5kaWxG.Wm','0a462b44-628a-45db-91cb-e90f82bad451');




