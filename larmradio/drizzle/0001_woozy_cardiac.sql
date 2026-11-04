CREATE TABLE `recordings` (
	`id` int AUTO_INCREMENT NOT NULL,
	`title` varchar(160) NOT NULL,
	`originalFileName` varchar(255) NOT NULL,
	`storageKey` varchar(512) NOT NULL,
	`contentType` varchar(64) NOT NULL,
	`sizeBytes` int NOT NULL,
	`durationSeconds` int NOT NULL,
	`status` enum('uploading','ready','published') NOT NULL DEFAULT 'uploading',
	`createdBy` int NOT NULL,
	`createdAt` timestamp NOT NULL DEFAULT (now()),
	`publishedAt` timestamp,
	CONSTRAINT `recordings_id` PRIMARY KEY(`id`),
	CONSTRAINT `recordings_storageKey_unique` UNIQUE(`storageKey`)
);
--> statement-breakpoint
CREATE TABLE `site_admin_assignments` (
	`id` int NOT NULL,
	`userId` int NOT NULL,
	`createdAt` timestamp NOT NULL DEFAULT (now()),
	CONSTRAINT `site_admin_assignments_id` PRIMARY KEY(`id`),
	CONSTRAINT `site_admin_assignments_userId_unique` UNIQUE(`userId`)
);
--> statement-breakpoint
ALTER TABLE `recordings` ADD CONSTRAINT `recordings_createdBy_users_id_fk` FOREIGN KEY (`createdBy`) REFERENCES `users`(`id`) ON DELETE no action ON UPDATE no action;--> statement-breakpoint
ALTER TABLE `site_admin_assignments` ADD CONSTRAINT `site_admin_assignments_userId_users_id_fk` FOREIGN KEY (`userId`) REFERENCES `users`(`id`) ON DELETE no action ON UPDATE no action;