-- MySQL's entrypoint scans only this directory. Keep all DDL before DML.
SOURCE /docker-entrypoint-initdb.d/ddl/01-account.sql;
SOURCE /docker-entrypoint-initdb.d/ddl/02-social.sql;
SOURCE /docker-entrypoint-initdb.d/ddl/03-message.sql;
SOURCE /docker-entrypoint-initdb.d/ddl/04-iam.sql;
SOURCE /docker-entrypoint-initdb.d/ddl/05-audit.sql;
SOURCE /docker-entrypoint-initdb.d/dml/06-iam-seed.sql;
