alter table app_users
    add column reminder_emails_enabled boolean not null default true;
