/* Single line comment */
CREATE TABLE "User"
(
    userID BIGSERIAL PRIMARY KEY NOT NULL,
    token VARCHAR

);

CREATE TABLE "Link"
(
    linkID BIGSERIAL PRIMARY KEY NOT NULL,
    userId BIGSERIAL REFERENCES "User" (userID),
    folderId BIGSERIAL REFERENCES "Folder" (folderID),
    url TEXT,
    code TEXT
);

CREATE TABLE "Click"
(
    clickID BIGSERIAL PRIMARY KEY NOT NULL,
    linkID BIGSERIAL REFERENCES "Link" (linkID),
    date DATE,
    referer TEXT,
    remote_ip INET
);

CREATE TABLE "FolderLink"
(
    folderID BIGSERIAL PRIMARY KEY NOT NULL REFERENCES "Folder" (folderID),
    linkID BIGSERIAL REFERENCES "Link" (linkID)
);

CREATE TABLE "Folder"
(
    folderID BIGSERIAL PRIMARY KEY NOT NULL,
    userId BIGSERIAL REFERENCES "User" (userID),
    title VARCHAR
);

-- ALTER TABLE "User" ADD FOREIGN KEY (userID) REFERENCES "Link" (id);
-- ALTER TABLE "Folder" ADD FOREIGN KEY (id) REFERENCES "Link" (id);
-- ALTER TABLE "Link" ADD FOREIGN KEY (id) REFERENCES "Click" (id);
