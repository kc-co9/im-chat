START TRANSACTION;

UPDATE `db_iam_oauth_client`
SET `redirect_uris` = JSON_ARRAY('http://localhost:18093/iam/callback'),
    `post_logout_redirect_uris` = JSON_ARRAY('http://localhost:18093/'),
    `update_time` = CURRENT_TIMESTAMP(3)
WHERE `oauth_client_id` = 'im-admin-client'
  AND `redirect_uris` = JSON_ARRAY('http://localhost:18091/iam/callback')
  AND `post_logout_redirect_uris` = JSON_ARRAY('http://localhost:18091/')
  AND `is_deleted` = 0;

UPDATE `db_iam_oauth_client`
SET `redirect_uris` = JSON_ARRAY('http://localhost:18091/iam/callback'),
    `post_logout_redirect_uris` = JSON_ARRAY('http://localhost:18091/'),
    `update_time` = CURRENT_TIMESTAMP(3)
WHERE `oauth_client_id` = 'im-audit-client'
  AND `redirect_uris` = JSON_ARRAY('http://localhost:18093/iam/callback')
  AND `post_logout_redirect_uris` = JSON_ARRAY('http://localhost:18093/')
  AND `is_deleted` = 0;

UPDATE `db_iam_oauth_client`
SET `redirect_uris` = JSON_ARRAY('http://localhost:18092/iam/callback'),
    `post_logout_redirect_uris` = JSON_ARRAY('http://localhost:18092/'),
    `update_time` = CURRENT_TIMESTAMP(3)
WHERE `oauth_client_id` = 'im-monitor-client'
  AND `redirect_uris` = JSON_ARRAY('http://localhost:18090/iam/callback')
  AND `post_logout_redirect_uris` = JSON_ARRAY('http://localhost:18090/')
  AND `is_deleted` = 0;

COMMIT;
