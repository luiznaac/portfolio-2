ALTER TABLE `import` ADD COLUMN destination_object VARCHAR(20) NOT NULL DEFAULT 'TRADES';
ALTER TABLE `import` ADD COLUMN destination_entity_id INT NULL;
ALTER TABLE `import` ADD COLUMN original_retained BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE `import_row` ADD COLUMN outcome VARCHAR(20) NULL;
CREATE TABLE IF NOT EXISTS `import_file` (id INT AUTO_INCREMENT PRIMARY KEY, import_id INT NOT NULL, content LONGBLOB NOT NULL, CONSTRAINT fk_import_file_import_id__id FOREIGN KEY (import_id) REFERENCES `import`(id) ON DELETE RESTRICT ON UPDATE RESTRICT);
ALTER TABLE `import_file` ADD CONSTRAINT import_file_import_id_unique UNIQUE (import_id);
