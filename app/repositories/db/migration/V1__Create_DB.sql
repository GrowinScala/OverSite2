-- MySQL Workbench Forward Engineering

SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0;
SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0;
SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION';


-- -----------------------------------------------------
-- Table `oversitedb`.`user`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `oversitedb`.`user` ;

CREATE TABLE IF NOT EXISTS `oversitedb`.`user` (
  `username` VARCHAR(16) NOT NULL,
  `email` VARCHAR(255) NULL,
  `password` VARCHAR(32) NOT NULL,
  `create_time` TIMESTAMP NULL DEFAULT CURRENT_TIMESTAMP);


-- -----------------------------------------------------
-- Table `oversitedb`.`addresses`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `oversitedb`.`addresses` ;

CREATE TABLE IF NOT EXISTS `oversitedb`.`addresses` (
  `address_id` CHAR(36) NOT NULL,
  `address` VARCHAR(45) NOT NULL,
  PRIMARY KEY (`address_id`),
  UNIQUE INDEX `address_UNIQUE` (`address` ASC))
ENGINE = InnoDB;


-- -----------------------------------------------------
-- Table `oversitedb`.`users`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `oversitedb`.`users` ;

CREATE TABLE IF NOT EXISTS `oversitedb`.`users` (
  `user_id` CHAR(36) NOT NULL,
  `address_id` CHAR(36) NOT NULL,
  `first_name` VARCHAR(45) NULL,
  `last_name` VARCHAR(45) NULL,
  PRIMARY KEY (`user_id`),
  INDEX `fk_users_addresses1_idx` (`address_id` ASC),
  CONSTRAINT `fk_users_addresses1`
    FOREIGN KEY (`address_id`)
    REFERENCES `oversitedb`.`addresses` (`address_id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
ENGINE = InnoDB;


-- -----------------------------------------------------
-- Table `oversitedb`.`chats`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `oversitedb`.`chats` ;

CREATE TABLE IF NOT EXISTS `oversitedb`.`chats` (
  `chat_id` CHAR(36) NOT NULL,
  `subject` VARCHAR(45) NULL,
  PRIMARY KEY (`chat_id`))
ENGINE = InnoDB;


-- -----------------------------------------------------
-- Table `oversitedb`.`emails`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `oversitedb`.`emails` ;

CREATE TABLE IF NOT EXISTS `oversitedb`.`emails` (
  `email_id` CHAR(36) NOT NULL,
  `chat_id` CHAR(36) NOT NULL,
  `body` VARCHAR(255) NULL,
  `date` TIMESTAMP(3) NOT NULL,
  `sent` TINYINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`email_id`),
  INDEX `fk_emails_chats1_idx` (`chat_id` ASC),
  CONSTRAINT `fk_emails_chats1`
    FOREIGN KEY (`chat_id`)
    REFERENCES `oversitedb`.`chats` (`chat_id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
ENGINE = InnoDB;


-- -----------------------------------------------------
-- Table `oversitedb`.`email_addresses`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `oversitedb`.`email_addresses` ;

CREATE TABLE IF NOT EXISTS `oversitedb`.`email_addresses` (
  `email_address_id` CHAR(36) NOT NULL,
  `email_id` CHAR(36) NOT NULL,
  `chat_id` CHAR(36) NOT NULL,
  `address_id` CHAR(36) NOT NULL,
  `participant_type` ENUM('from', 'to', 'cc', 'bcc') NOT NULL,
  PRIMARY KEY (`email_address_id`),
  INDEX `fk_email_addresses_chats1_idx` (`chat_id` ASC),
  INDEX `fk_email_addresses_emails1_idx` (`email_id` ASC),
  INDEX `fk_email_addresses_addresses1_idx` (`address_id` ASC),
  CONSTRAINT `fk_email_addresses_chats1`
    FOREIGN KEY (`chat_id`)
    REFERENCES `oversitedb`.`chats` (`chat_id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION,
  CONSTRAINT `fk_email_addresses_emails1`
    FOREIGN KEY (`email_id`)
    REFERENCES `oversitedb`.`emails` (`email_id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION,
  CONSTRAINT `fk_email_addresses_addresses1`
    FOREIGN KEY (`address_id`)
    REFERENCES `oversitedb`.`addresses` (`address_id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
ENGINE = InnoDB;


-- -----------------------------------------------------
-- Table `oversitedb`.`user_chats`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `oversitedb`.`user_chats` ;

CREATE TABLE IF NOT EXISTS `oversitedb`.`user_chats` (
  `user_chat_id` CHAR(36) NOT NULL,
  `user_id` CHAR(36) NOT NULL,
  `chat_id` CHAR(36) NOT NULL,
  `inbox` TINYINT NOT NULL,
  `sent` TINYINT NOT NULL,
  `draft` TINYINT NOT NULL,
  `trash` TINYINT NOT NULL,
  PRIMARY KEY (`user_chat_id`),
  INDEX `fk_user_chats_users1_idx` (`user_id` ASC),
  INDEX `fk_user_chats_chats1_idx` (`chat_id` ASC),
  CONSTRAINT `fk_user_chats_users1`
    FOREIGN KEY (`user_id`)
    REFERENCES `oversitedb`.`users` (`user_id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION,
  CONSTRAINT `fk_user_chats_chats1`
    FOREIGN KEY (`chat_id`)
    REFERENCES `oversitedb`.`chats` (`chat_id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
ENGINE = InnoDB;


-- -----------------------------------------------------
-- Table `oversitedb`.`oversights`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `oversitedb`.`oversights` ;

CREATE TABLE IF NOT EXISTS `oversitedb`.`oversights` (
  `oversight_id` CHAR(36) NOT NULL,
  `chat_id` CHAR(36) NOT NULL,
  `overseer_id` CHAR(36) NOT NULL,
  `oversee_id` CHAR(36) NOT NULL,
  PRIMARY KEY (`oversight_id`),
  INDEX `fk_oversights_users1_idx` (`overseer_id` ASC),
  INDEX `fk_oversights_users2_idx` (`oversee_id` ASC),
  INDEX `fk_oversights_chats1_idx` (`chat_id` ASC),
  CONSTRAINT `fk_oversights_users1`
    FOREIGN KEY (`overseer_id`)
    REFERENCES `oversitedb`.`users` (`user_id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION,
  CONSTRAINT `fk_oversights_users2`
    FOREIGN KEY (`oversee_id`)
    REFERENCES `oversitedb`.`users` (`user_id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION,
  CONSTRAINT `fk_oversights_chats1`
    FOREIGN KEY (`chat_id`)
    REFERENCES `oversitedb`.`chats` (`chat_id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
ENGINE = InnoDB;


-- -----------------------------------------------------
-- Table `oversitedb`.`attachments`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `oversitedb`.`attachments` ;

CREATE TABLE IF NOT EXISTS `oversitedb`.`attachments` (
  `attachment_id` CHAR(36) NOT NULL,
  `email_id` CHAR(36) NOT NULL,
  PRIMARY KEY (`attachment_id`),
  UNIQUE INDEX `attachment_id_UNIQUE` (`attachment_id` ASC),
  INDEX `fk_attachments_emails1_idx` (`email_id` ASC),
  CONSTRAINT `fk_attachments_emails1`
    FOREIGN KEY (`email_id`)
    REFERENCES `oversitedb`.`emails` (`email_id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
ENGINE = InnoDB;


-- -----------------------------------------------------
-- Table `oversitedb`.`tokens`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `oversitedb`.`tokens` ;

CREATE TABLE IF NOT EXISTS `oversitedb`.`tokens` (
  `token_id` CHAR(36) NOT NULL,
  `token` VARCHAR(400) NOT NULL,
  PRIMARY KEY (`token_id`))
ENGINE = InnoDB;


-- -----------------------------------------------------
-- Table `oversitedb`.`passwords`
-- -----------------------------------------------------
DROP TABLE IF EXISTS `oversitedb`.`passwords` ;

CREATE TABLE IF NOT EXISTS `oversitedb`.`passwords` (
  `password_id` CHAR(36) NOT NULL,
  `user_id` CHAR(36) NOT NULL,
  `password` VARCHAR(60) NOT NULL,
  `token_id` CHAR(36) NOT NULL,
  PRIMARY KEY (`password_id`),
  INDEX `fk_passwords_users1_idx` (`user_id` ASC),
  INDEX `fk_passwords_tokens1_idx` (`token_id` ASC),
  CONSTRAINT `fk_passwords_users1`
    FOREIGN KEY (`user_id`)
    REFERENCES `oversitedb`.`users` (`user_id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION,
  CONSTRAINT `fk_passwords_tokens1`
    FOREIGN KEY (`token_id`)
    REFERENCES `oversitedb`.`tokens` (`token_id`)
    ON DELETE NO ACTION
    ON UPDATE NO ACTION)
ENGINE = InnoDB;


SET SQL_MODE=@OLD_SQL_MODE;
SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS;
SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS;
