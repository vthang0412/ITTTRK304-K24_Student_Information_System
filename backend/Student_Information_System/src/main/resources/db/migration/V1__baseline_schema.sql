-- ============================================================
-- Baseline schema for the SIS database (MySQL 8.0).
-- Generated from the application entities and verified with Hibernate validate.
-- Applied automatically on a fresh production database only.
-- Existing databases are baselined at version 1 and are NOT modified.
-- ============================================================

SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `course_sections` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `capacity` int NOT NULL,
  `schedule` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `section_code` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('CANCELLED','CLOSED','COMPLETED','DRAFT','IN_PROGRESS','OPEN') COLLATE utf8mb4_unicode_ci NOT NULL,
  `course_id` bigint NOT NULL,
  `lecturer_id` bigint DEFAULT NULL,
  `semester_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKht6a0o3kdsvy71id7tqq22irq` (`section_code`),
  KEY `FK4xiaseygl0aodt5ahkbm8s2ds` (`course_id`),
  KEY `FKm5c164s1i40g2ravnd7fhson6` (`lecturer_id`),
  KEY `FK354it1v8line715kwgyd7gng3` (`semester_id`),
  CONSTRAINT `FK354it1v8line715kwgyd7gng3` FOREIGN KEY (`semester_id`) REFERENCES `semesters` (`id`),
  CONSTRAINT `FK4xiaseygl0aodt5ahkbm8s2ds` FOREIGN KEY (`course_id`) REFERENCES `courses` (`id`),
  CONSTRAINT `FKm5c164s1i40g2ravnd7fhson6` FOREIGN KEY (`lecturer_id`) REFERENCES `lecturers` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `courses` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `course_code` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `course_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `credits` int NOT NULL,
  `description` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` enum('ACTIVE','INACTIVE') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKp02ts69sh53ptd62m3c67v0` (`course_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `enrollments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `registered_at` datetime(6) DEFAULT NULL,
  `status` enum('ENROLLED','WITHDRAWN') COLLATE utf8mb4_unicode_ci NOT NULL,
  `withdrawn_at` datetime(6) DEFAULT NULL,
  `section_id` bigint NOT NULL,
  `student_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKj1fpm330vse7gvfghxpg2n5tp` (`student_id`,`section_id`),
  KEY `FKggjm8m0rveac7g51eswnhj93m` (`section_id`),
  CONSTRAINT `FK8kf1u1857xgo56xbfmnif2c51` FOREIGN KEY (`student_id`) REFERENCES `students` (`id`),
  CONSTRAINT `FKggjm8m0rveac7g51eswnhj93m` FOREIGN KEY (`section_id`) REFERENCES `course_sections` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `grade_audit_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `action` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `actor` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `new_value` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `old_value` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `enrollment_id` bigint DEFAULT NULL,
  `grade_component_id` bigint DEFAULT NULL,
  `gradebook_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKgjv2avo13ptu8qar6yj396pkh` (`enrollment_id`),
  KEY `FKd7bce8m6bfp0tayjgutsnynap` (`grade_component_id`),
  KEY `FKdhgm60ov6jc0ua7ujwsi0whd` (`gradebook_id`),
  CONSTRAINT `FKd7bce8m6bfp0tayjgutsnynap` FOREIGN KEY (`grade_component_id`) REFERENCES `grade_components` (`id`),
  CONSTRAINT `FKdhgm60ov6jc0ua7ujwsi0whd` FOREIGN KEY (`gradebook_id`) REFERENCES `gradebooks` (`id`),
  CONSTRAINT `FKgjv2avo13ptu8qar6yj396pkh` FOREIGN KEY (`enrollment_id`) REFERENCES `enrollments` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `grade_component_scores` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `score` decimal(4,2) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `updated_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `enrollment_id` bigint NOT NULL,
  `grade_component_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK7fbfft5lt53rp23hrrvheu62n` (`grade_component_id`,`enrollment_id`),
  KEY `FKaaac302o9q2wo6m3lxyogv6cd` (`enrollment_id`),
  CONSTRAINT `FKaaac302o9q2wo6m3lxyogv6cd` FOREIGN KEY (`enrollment_id`) REFERENCES `enrollments` (`id`),
  CONSTRAINT `FKkct9sh1mc986yyd1jgwl3p9ij` FOREIGN KEY (`grade_component_id`) REFERENCES `grade_components` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `grade_components` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `component_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `component_type` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `display_order` int NOT NULL,
  `max_score` decimal(4,2) NOT NULL,
  `is_required` bit(1) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `weight` decimal(5,2) NOT NULL,
  `gradebook_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKpux3elawu5nbyctofduthmted` (`gradebook_id`,`component_name`),
  CONSTRAINT `FKchgkcbplwfuv86d22rviyi4fx` FOREIGN KEY (`gradebook_id`) REFERENCES `gradebooks` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `gradebooks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `approved_at` datetime(6) DEFAULT NULL,
  `approved_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `locked_at` datetime(6) DEFAULT NULL,
  `locked_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `published_at` datetime(6) DEFAULT NULL,
  `published_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` enum('OPEN','LOCKED','APPROVED','PUBLISHED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `section_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK57tx6xagc2ugdvvs59agkvyps` (`section_id`),
  CONSTRAINT `FKf64ptoogne3ldv5kjwlgolh27` FOREIGN KEY (`section_id`) REFERENCES `course_sections` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `grades` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `component_score` decimal(4,2) DEFAULT NULL,
  `final_score` decimal(4,2) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `updated_by` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `enrollment_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK7i36gvau5sfuaqp2pvcclvqvo` (`enrollment_id`),
  CONSTRAINT `FK354vwjlgqmgi7tveernv2ylp6` FOREIGN KEY (`enrollment_id`) REFERENCES `enrollments` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `lecturers` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `department` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `full_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `lecturer_code` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('ACTIVE','INACTIVE') COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKk6vsb5jy1uq700gx4a6brvtpv` (`lecturer_code`),
  UNIQUE KEY `UK6m2kb7yyubmpyo8f349gbrq2y` (`user_id`),
  CONSTRAINT `FKp8mocspquan2i6fjx36qghvct` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `notifications` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `is_read` bit(1) NOT NULL,
  `target_role` enum('ADMIN','LECTURER','STUDENT') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `recipient_id` bigint DEFAULT NULL,
  `sender_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKqqnsjxlwleyjbxlmm213jaj3f` (`recipient_id`),
  KEY `FK13vcnq3ukas06ho1yrbc5lrb5` (`sender_id`),
  CONSTRAINT `FK13vcnq3ukas06ho1yrbc5lrb5` FOREIGN KEY (`sender_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKqqnsjxlwleyjbxlmm213jaj3f` FOREIGN KEY (`recipient_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `semesters` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `end_date` date NOT NULL,
  `registration_end` datetime(6) DEFAULT NULL,
  `registration_start` datetime(6) DEFAULT NULL,
  `semester_code` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `semester_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `start_date` date NOT NULL,
  `status` enum('COMPLETED','IN_PROGRESS','PLANNED','REGISTRATION_OPEN') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKqyiqe0v3vqi8r840gbbh75xrl` (`semester_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `students` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `department` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `full_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('ACTIVE','INACTIVE') COLLATE utf8mb4_unicode_ci NOT NULL,
  `student_code` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKcgcf3r5xk73o0etbduc1qxnol` (`student_code`),
  UNIQUE KEY `UKg4fwvutq09fjdlb4bb0byp7t` (`user_id`),
  CONSTRAINT `FKdt1cjx5ve5bdabmuuf3ibrwaq` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `avatar_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `full_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `password_hash` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `role` enum('ADMIN','LECTURER','STUDENT') COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('ACTIVE','INACTIVE') COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `username` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK6dotkott2kjsp8vw4d0m25fb7` (`email`),
  UNIQUE KEY `UKr43af9ap4edm43mmtq01oddj6` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET FOREIGN_KEY_CHECKS = 1;
