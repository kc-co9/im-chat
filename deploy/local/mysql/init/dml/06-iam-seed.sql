USE `im_chat_iam`;

INSERT INTO `db_iam_app` (`app_id`, `app_key`, `name`, `status`)
VALUES (10001, 'imIam', 'IM Chat IAM', 1),
       (10002, 'imAdmin', 'IM Chat Admin', 1),
       (10003, 'imAudit', 'IM Chat Audit', 1),
       (10004, 'imMonitor', 'IM Chat Monitor', 1)
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `status` = VALUES(`status`),
    `is_deleted` = 0;

INSERT INTO `db_iam_oauth_client`
(`oauth_client_id`, `app_id`, `audience_app_id`, `name`, `client_secret_hash`,
 `grant_types`, `scopes`, `redirect_uris`, `post_logout_redirect_uris`, `status`)
VALUES
('im-admin-client', 10002, 10002, 'IM Admin web client',
 '$2y$12$19hJ/3uaCG3mYLKNVNhju.ZKwUGm9RuEzQ7VEDIKDgo44.R.ty1WK',
 'AUTHORIZATION_CODE REFRESH_TOKEN', 'openid profile',
 JSON_ARRAY('http://localhost:18042/iam/callback'), JSON_ARRAY('http://localhost:18042/'), 1),
('im-audit-client', 10003, 10003, 'IM Audit web',
 '$2y$12$m3cMzzeC7kO/mCLjZ1O9SOkWTWxpRcv/SwREGTviJsWxCNgXgJD2u',
 'AUTHORIZATION_CODE REFRESH_TOKEN', 'openid profile',
 JSON_ARRAY('http://localhost:18041/iam/callback'), JSON_ARRAY('http://localhost:18041/'), 1),
('im-monitor-client', 10004, 10004, 'IM Monitor web',
 '$2y$12$w3MtSxIKLlrVofYgezXwruymY.5vd8JovhYrdDX7.xzXONbvP84Cq',
 'AUTHORIZATION_CODE REFRESH_TOKEN', 'openid profile',
 JSON_ARRAY('http://localhost:18043/iam/callback'), JSON_ARRAY('http://localhost:18043/'), 1),
('im-admin-catalog', 10002, 10001, 'IM Admin permission catalog',
 '$2y$12$BJBBfEFzKjspJ2Qhfiv.qeE6gp.1H2A.RrhEdqkmnFuTe9Hac6aVq',
 'CLIENT_CREDENTIALS', 'iam.catalog.write', JSON_ARRAY(), JSON_ARRAY(), 1),
('im-audit-catalog', 10003, 10001, 'IM Audit permission catalog',
 '$2y$12$HYUcspEQtNWSRVkAJDSn9ugOq/wyoVU05XMLmNpJ9o1FoSCeo4vK2',
 'CLIENT_CREDENTIALS', 'iam.catalog.write', JSON_ARRAY(), JSON_ARRAY(), 1),
('im-monitor-catalog', 10004, 10001, 'IM Monitor permission catalog',
 '$2y$12$Holi4ZcD2jVi1Zd7zAkOn.pSZWU014SgP3j8OXYyvSIhwfzfjscVy',
 'CLIENT_CREDENTIALS', 'iam.catalog.write', JSON_ARRAY(), JSON_ARRAY(), 1)
ON DUPLICATE KEY UPDATE
    `app_id` = VALUES(`app_id`),
    `audience_app_id` = VALUES(`audience_app_id`),
    `name` = VALUES(`name`),
    `client_secret_hash` = VALUES(`client_secret_hash`),
    `grant_types` = VALUES(`grant_types`),
    `scopes` = VALUES(`scopes`),
    `redirect_uris` = VALUES(`redirect_uris`),
    `post_logout_redirect_uris` = VALUES(`post_logout_redirect_uris`),
    `status` = VALUES(`status`),
    `is_deleted` = 0;
