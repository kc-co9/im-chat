package com.co.kc.imchat.loadtest.cleanup;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LoadTestBatchManifestTest {

    @Test
    void parsesTheNodeManifestContractIntoImmutableRecords() {
        LoadTestBatchManifest manifest = LoadTestBatchManifest.parse(validJson());

        assertEquals("20260914-t3b1", manifest.batchId());
        assertEquals(2, manifest.users().size());
        assertEquals("9223372036854775807", LoadTestBatchManifest.parse(
                validJson().replace("\"101\"", "\"9223372036854775807\"")).users().getFirst().userId());
        assertThrows(UnsupportedOperationException.class, () -> manifest.users().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> manifest.groups().getFirst().memberUserIds().add("103"));
    }

    @Test
    void rejectsDuplicateUnknownNoncanonicalAndOutOfRangeFields() {
        assertInvalid(validJson().replaceFirst("\"version\": 1,", "\"version\": 1, \"version\": 1,"));
        assertInvalid(validJson().replaceFirst("\"prefix\":", "\"unexpected\": true, \"prefix\":"));
        assertInvalid(validJson().replace("\"101\"", "\"0101\""));
        assertInvalid(validJson().replace("\"101\"", "\"9223372036854775808\""));
    }

    @Test
    void rejectsTamperedOwnershipAndCrossReferences() {
        assertInvalid(validJson().replace("loadtest-20260914-t3b1-user-0001", "loadtest-other-user-0001"));
        assertInvalid(validJson().replace("\"account\": \"im_chat_account\"",
                "\"account\": \"other_account\""));
        assertInvalid(validJson().replaceFirst("\"friendUserId\": \"102\"", "\"friendUserId\": \"999\""));
        assertInvalid(validJson().replaceFirst("\"peerUserId\": \"102\"", "\"peerUserId\": \"999\""));
        assertInvalid(validJson().replaceFirst("\"userId\": \"102\", \"peerUserId\": \"101\"",
                "\"userId\": \"102\", \"peerUserId\": \"102\""));
        assertInvalid(validJson().replace("PRIVATE_MESSAGE_SEND:102:302:401",
                "PRIVATE_MESSAGE_SEND:101:302:401"));
        assertInvalid(validJson().replace("GROUP_MESSAGE_SEND:102:312:402",
                "GROUP_MESSAGE_SEND:102:312:401"));
    }

    private static void assertInvalid(String json) {
        assertThrows(IllegalArgumentException.class, () -> LoadTestBatchManifest.parse(json));
    }

    @Test
    void groupCreationSystemMessageIsOwnedOnlyByItsExactBatchGroupAndOwner() {
        String systemMessage = validJson().replace("loadtest-20260914-t3b1:group:0001", "system:group_created:201");
        assertEquals("system:group_created:201", LoadTestBatchManifest.parse(systemMessage)
                .messages().get(1).correlationToken());
        assertInvalid(systemMessage.replace("system:group_created:201", "system:group_created:999"));
        assertInvalid(systemMessage.replace("\"senderUserId\": \"101\", \"groupId\": \"201\"",
                "\"senderUserId\": \"102\", \"groupId\": \"201\""));
    }

    static String validJson() {
        return """
                {
                  "version": 1,
                  "batchId": "20260914-t3b1",
                  "prefix": "loadtest-20260914-t3b1",
                  "schemas": {
                    "account": "im_chat_account",
                    "social": "im_chat_social",
                    "message": "im_chat_message"
                  },
                  "users": [
                    {"userId": "101", "email": "loadtest-20260914-t3b1-user-0001@example.test", "cohortId": "cohort-0001"},
                    {"userId": "102", "email": "loadtest-20260914-t3b1-user-0002@example.test", "cohortId": "cohort-0001"}
                  ],
                  "cohorts": [
                    {"cohortId": "cohort-0001", "userIds": ["101", "102"], "groupId": "201"}
                  ],
                  "groups": [
                    {"groupId": "201", "name": "loadtest-20260914-t3b1-group-0001", "ownerUserId": "101", "memberUserIds": ["101", "102"]}
                  ],
                  "relations": [
                    {"userId": "101", "friendUserId": "102"},
                    {"userId": "102", "friendUserId": "101"}
                  ],
                  "privateChats": [
                    {"chatId": "301", "userId": "101", "peerUserId": "102"},
                    {"chatId": "302", "userId": "102", "peerUserId": "101"}
                  ],
                  "groupChats": [
                    {"chatId": "311", "groupId": "201", "userId": "101"},
                    {"chatId": "312", "groupId": "201", "userId": "102"}
                  ],
                  "messages": [
                    {"messageId": "401", "type": "private", "senderUserId": "101", "correlationToken": "loadtest-20260914-t3b1:private:0001"},
                    {"messageId": "402", "type": "group", "senderUserId": "101", "groupId": "201", "correlationToken": "loadtest-20260914-t3b1:group:0001"}
                  ],
                  "receiptIds": [
                    "PRIVATE_MESSAGE_SEND:102:302:401",
                    "GROUP_MESSAGE_SEND:102:312:402"
                  ]
                }
                """;
    }
}
