/* Default table structure */
CREATE TABLE "Users"
(
    user_id BIGSERIAL PRIMARY KEY NOT NULL,
    token VARCHAR

);

CREATE TABLE "Folders"
(
    folder_id BIGSERIAL PRIMARY KEY NOT NULL,
    user_id BIGSERIAL REFERENCES "Users" (user_id),
    title VARCHAR
);

CREATE TABLE "Links"
(
    link_id BIGSERIAL PRIMARY KEY NOT NULL,
    user_id BIGSERIAL REFERENCES "Users" (user_id),
    url TEXT,
    code TEXT,
    is_user_link BOOL
);

CREATE TABLE "Clicks"
(
    click_id BIGSERIAL PRIMARY KEY NOT NULL,
    link_id BIGSERIAL REFERENCES "Links" (link_id),
    date DATE,
    referer TEXT,
    remote_ip INET
);

CREATE TABLE "FolderLinks"
(
    folder_id BIGSERIAL PRIMARY KEY NOT NULL REFERENCES "Folders" (folder_id),
    link_id BIGSERIAL REFERENCES "Links" (link_id)
);



-- ALTER TABLE "User" ADD FOREIGN KEY (userID) REFERENCES "Link" (id);
-- ALTER TABLE "Folder" ADD FOREIGN KEY (id) REFERENCES "Link" (id);
-- ALTER TABLE "Link" ADD FOREIGN KEY (id) REFERENCES "Click" (id);
