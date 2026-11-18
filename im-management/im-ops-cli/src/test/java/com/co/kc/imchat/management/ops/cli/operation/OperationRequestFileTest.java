package com.co.kc.imchat.management.ops.cli.operation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

class OperationRequestFileTest {

    @TempDir
    Path directory;

    @Test
    void unknownCredentialFieldCannotEnterRestoredRequest() throws Exception {
        Path path = directory.resolve("unknown-field.json");
        Files.writeString(path, """
                {"environment":"https://ops.example.test","operationId":"operation-42",\
                "expectedEpoch":17,"brokerId":"broker-c","action":"remove","membershipEndpoint":"broker-c:8802","accessToken":"synthetic-not-a-token"}
                """);
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));

        IOException failure = assertThrows(IOException.class, () -> OperationRequestFile.read(path));

        assertTrue(failure.getMessage().contains("Unrecognized field"), failure.getMessage());
    }

    @Test
    void canonicalAncestorMustStillBeAnUnlinkedDirectoryWhenChecked() throws Exception {
        Path parent = Files.createDirectory(directory.toRealPath().resolve("changed-parent"));
        Path path = parent.resolve("operation.json");
        OperationRequestFile.write(path, new OperationRequestFile.Request(
                "https://ops.example.test", "operation-42", 17, "broker-c", "remove", "broker-c:8802", "maintenance"));
        try (MockedStatic<Files> files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)).thenReturn(false);

            IOException failure = assertThrows(IOException.class, () -> OperationRequestFile.read(path));

            assertEquals("Operation request ancestor must be a directory without symlinks", failure.getMessage());
            files.verify(() -> Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS), never());
        }
    }

    @Test
    void unsafeAncestorIsRejectedBeforeInspectingUntrustedDescendant() throws Exception {
        Path ancestor = Files.createDirectory(directory.toRealPath().resolve("unsafe-ancestor"));
        Path parent = Files.createDirectory(ancestor.resolve("descendant"));
        Path path = parent.resolve("operation.json");
        OperationRequestFile.write(path, new OperationRequestFile.Request(
                "https://ops.example.test", "operation-42", 17, "broker-c", "remove", "broker-c:8802", "maintenance"));
        Files.setPosixFilePermissions(ancestor, PosixFilePermissions.fromString("rwxrwx---"));
        try (MockedStatic<Files> files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            assertThrows(IOException.class, () -> OperationRequestFile.read(path));

            files.verify(() -> Files.getOwner(parent, LinkOption.NOFOLLOW_LINKS), never());
            files.verify(() -> Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS), never());
        }
    }

    @Test
    void writableAncestorCannotHideBehindPrivateParent() throws Exception {
        Path ancestor = Files.createDirectory(directory.resolve("shared-ancestor"));
        Path parent = Files.createDirectory(ancestor.resolve("private-parent"));
        Path path = parent.resolve("operation.json");
        OperationRequestFile.write(path, new OperationRequestFile.Request(
                "https://ops.example.test", "operation-42", 17, "broker-c", "remove", "broker-c:8802", "maintenance"));
        Files.setPosixFilePermissions(ancestor, PosixFilePermissions.fromString("rwxrwx---"));

        IOException failure = assertThrows(IOException.class, () -> OperationRequestFile.read(path));

        assertEquals("Operation request directory must not be writable by other users", failure.getMessage());
    }

    @Test
    void foreignOwnedParentCannotReplaceCheckedRequest() throws Exception {
        Path parent = Files.createDirectory(directory.toRealPath().resolve("foreign-parent"));
        Path path = parent.resolve("operation.json");
        OperationRequestFile.write(path, new OperationRequestFile.Request(
                "https://ops.example.test", "operation-42", 17, "broker-c", "remove", "broker-c:8802", "maintenance"));
        UserPrincipal foreignOwner = () -> "another-user";
        try (MockedStatic<Files> files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.getOwner(parent, LinkOption.NOFOLLOW_LINKS)).thenReturn(foreignOwner);

            IOException failure = assertThrows(IOException.class, () -> OperationRequestFile.read(path));

            assertEquals("Operation request directory must belong to the current user or root", failure.getMessage());
        }
    }

    @Test
    void writableParentCannotReceiveNewRequest() throws Exception {
        Path parent = Files.createDirectory(directory.resolve("unsafe-write-parent"));
        Files.setPosixFilePermissions(parent, PosixFilePermissions.fromString("rwxrwx---"));
        Path path = parent.resolve("operation.json");
        OperationRequestFile.Request request = new OperationRequestFile.Request(
                "https://ops.example.test", "operation-42", 17, "broker-c", "remove", "broker-c:8802", "maintenance");

        IOException failure = assertThrows(IOException.class, () -> OperationRequestFile.write(path, request));

        assertEquals("Operation request directory must not be writable by other users", failure.getMessage());
        assertTrue(Files.notExists(path));
    }

    @Test
    void writableParentCannotSupplyRestoredRequest() throws Exception {
        Path parent = Files.createDirectory(directory.resolve("shared-parent"));
        Path path = parent.resolve("operation.json");
        OperationRequestFile.write(path, new OperationRequestFile.Request(
                "https://ops.example.test", "operation-42", 17, "broker-c", "remove", "broker-c:8802", "maintenance"));
        Files.setPosixFilePermissions(parent, PosixFilePermissions.fromString("rwxrwx---"));

        IOException failure = assertThrows(IOException.class, () -> OperationRequestFile.read(path));

        assertEquals("Operation request directory must not be writable by other users", failure.getMessage());
    }

    @Test
    void requestOwnedByAnotherUserIsRejected() throws Exception {
        Path path = directory.toRealPath().resolve("foreign-owner.json");
        OperationRequestFile.write(path, new OperationRequestFile.Request(
                "https://ops.example.test", "operation-42", 17, "broker-c", "remove", "broker-c:8802", "maintenance"));
        UserPrincipal foreignOwner = () -> "another-user";
        try (MockedStatic<Files> files = mockStatic(Files.class, CALLS_REAL_METHODS)) {
            files.when(() -> Files.getOwner(path, LinkOption.NOFOLLOW_LINKS))
                    .thenReturn(foreignOwner);

            IOException failure = assertThrows(IOException.class, () -> OperationRequestFile.read(path));

            assertEquals("Operation request must belong to the current user", failure.getMessage());
        }
    }

    @Test
    void confirmationRecordPreservesOriginalEndpointAndReason() throws Exception {
        Path path = directory.resolve("confirmation.json");
        OperationRequestFile.Request request = new OperationRequestFile.Request(
                "https://ops.example.test", "operation-42", 17, "broker-c", "complete-learner-sync",
                "broker-c.example.test:8802", "maintenance");

        OperationRequestFile.write(path, request);

        assertEquals(request, OperationRequestFile.read(path));
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL", "''", "'   '"}, nullValues = "NULL")
    void confirmationRequiresOriginalEndpoint(String endpoint) {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new OperationRequestFile.Request(
                        "https://ops.example.test", "operation-42", 17, "broker-c", "complete-learner-sync",
                        endpoint, "maintenance"));

        assertEquals("Membership operation requires a membership endpoint", failure.getMessage());
    }

    @Test
    void legacyConfirmationWithoutEndpointIsRejectedWithoutChangingRecord() throws Exception {
        Path path = directory.resolve("legacy-confirmation.json");
        String original = """
                {"environment":"https://ops.example.test","operationId":"operation-42",\
                "expectedEpoch":17,"brokerId":"broker-c","action":"complete-learner-sync"}
                """;
        Files.writeString(path, original);
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));

        IOException failure = assertThrows(IOException.class, () -> OperationRequestFile.read(path));

        assertTrue(failure.getMessage().contains("Membership operation requires a membership endpoint"));
        assertEquals(original, Files.readString(path));
    }

    @Test
    void learnerRecordCannotCarryReasonIgnoredBySubmission() {
        Path path = directory.resolve("learner-with-reason.json");

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> OperationRequestFile.write(path, new OperationRequestFile.Request(
                        "https://ops.example.test", "operation-42", 17, "broker-c", "add-learner",
                        "broker-c.example.test:8802", "ignored reason")));

        assertEquals("Reason is not valid for learner addition", failure.getMessage());
        assertTrue(Files.notExists(path));
    }

    @Test
    void nullDocumentCannotRestoreAnOperationRequest() throws Exception {
        Path path = directory.resolve("null-document.json");
        Files.writeString(path, "null");
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));

        IOException failure = assertThrows(IOException.class, () -> OperationRequestFile.read(path));

        assertEquals("Operation request must not be null", failure.getMessage());
    }

    @Test
    void oversizedRequestCannotBeWrittenAsUnrestorableRecord() {
        Path path = directory.resolve("oversized-write.json");
        OperationRequestFile.Request request = new OperationRequestFile.Request(
                "https://ops.example.test", "operation-42", 17, "broker-c", "remove", "broker-c:8802", "x".repeat(65536));

        IOException failure = assertThrows(IOException.class, () -> OperationRequestFile.write(path, request));

        assertEquals("Operation request exceeds 64 KiB", failure.getMessage());
        assertTrue(Files.notExists(path), "oversized request must fail before creating the record");
    }

    @Test
    void fractionalEpochCannotBeTruncatedOnRestore() throws Exception {
        Path path = directory.resolve("fractional-epoch.json");
        Files.writeString(path, """
                {"environment":"https://ops.example.test","operationId":"operation-42",\
                "expectedEpoch":17.5,"brokerId":"broker-c","action":"remove","reason":"maintenance"}
                """);
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));

        IOException failure = assertThrows(IOException.class, () -> OperationRequestFile.read(path));

        assertTrue(failure.getMessage().contains("Floating-point"), failure.getMessage());
    }

    @Test
    void stringEpochCannotBeCoercedIntoApprovedSemantics() throws Exception {
        Path path = directory.resolve("string-epoch.json");
        Files.writeString(path, """
                {"environment":"https://ops.example.test","operationId":"operation-42",\
                "expectedEpoch":"17","brokerId":"broker-c","action":"remove","reason":"maintenance"}
                """);
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));

        IOException failure = assertThrows(IOException.class, () -> OperationRequestFile.read(path));

        assertTrue(failure.getMessage().contains("coerce"), failure.getMessage());
    }

    @Test
    void trailingJsonCannotHideAnotherRequest() throws Exception {
        Path path = directory.resolve("trailing.json");
        OperationRequestFile.write(path, new OperationRequestFile.Request(
                "https://ops.example.test", "operation-42", 17, "broker-c", "remove", "broker-c:8802", "maintenance"));
        Files.writeString(path, " {}", StandardOpenOption.APPEND);

        IOException failure = assertThrows(IOException.class, () -> OperationRequestFile.read(path));

        assertTrue(failure.getMessage().contains("Trailing token"), failure.getMessage());
    }

    @Test
    void duplicateEpochCannotSilentlyReplaceOriginalSemantics() throws Exception {
        Path path = directory.resolve("duplicate-epoch.json");
        Files.writeString(path, """
                {"environment":"https://ops.example.test","operationId":"operation-42",\
                "expectedEpoch":17,"expectedEpoch":18,"brokerId":"broker-c","action":"remove",\
                "reason":"maintenance"}
                """);
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));

        IOException failure = assertThrows(IOException.class, () -> OperationRequestFile.read(path));

        assertTrue(failure.getMessage().contains("Duplicate field"), failure.getMessage());
    }

    @Test
    void nullEpochCannotBecomeImplicitZeroOnRestore() throws Exception {
        Path path = directory.resolve("null-epoch.json");
        Files.writeString(path, """
                {"environment":"https://ops.example.test","operationId":"operation-42",\
                "expectedEpoch":null,"brokerId":"broker-c","action":"remove","reason":"maintenance"}
                """);
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));

        IOException failure = assertThrows(IOException.class, () -> OperationRequestFile.read(path));

        assertTrue(failure.getMessage().contains("null"), failure.getMessage());
    }

    @Test
    void missingEpochCannotBecomeImplicitZeroOnRestore() throws Exception {
        Path path = directory.resolve("missing-epoch.json");
        Files.writeString(path, """
                {"environment":"https://ops.example.test","operationId":"operation-42",\
                "brokerId":"broker-c","action":"remove","reason":"maintenance"}
                """);
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));

        IOException failure = assertThrows(IOException.class, () -> OperationRequestFile.read(path));

        assertTrue(failure.getMessage().contains("expectedEpoch"), failure.getMessage());
    }

    @Test
    void unboundedRequestFileIsRejectedBeforeJsonParsing() throws Exception {
        Path path = directory.resolve("oversized.json");
        Files.writeString(path, " ".repeat(65537));
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));

        IOException failure = assertThrows(IOException.class, () -> OperationRequestFile.read(path));

        assertEquals("Operation request exceeds 64 KiB", failure.getMessage());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"promote", "demote", "remove", "complete-learner-sync", "add-learner"})
    void membershipRequestRequiresEndpoint(String action) {
        Path path = directory.resolve("irrelevant.json");

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> OperationRequestFile.write(path, new OperationRequestFile.Request(
                        "https://ops.example.test", "operation-42", 17, "broker-c", action, null, null)));

        assertEquals("Membership operation requires a membership endpoint", failure.getMessage());
        assertTrue(Files.notExists(path));
    }

    @Test
    void learnerRequestRequiresMembershipEndpoint() {
        Path path = directory.resolve("learner.json");

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> OperationRequestFile.write(path, new OperationRequestFile.Request(
                        "https://ops.example.test", "operation-42", 17, "broker-c", "add-learner", null, null)));

        assertEquals("Membership operation requires a membership endpoint", failure.getMessage());
        assertTrue(Files.notExists(path));
    }

    @ParameterizedTest
    @CsvSource({"'',17,broker-c", "operation-42,-1,broker-c", "operation-42,17,''"})
    void invalidOperationIdentityCannotBePersisted(String operationId, long epoch, String brokerId) {
        Path path = directory.resolve("invalid.json");

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> OperationRequestFile.write(path, new OperationRequestFile.Request(
                        "https://ops.example.test", operationId, epoch, brokerId, "remove", "broker-c:8802", "maintenance")));

        assertEquals("Operation identity, broker and nonnegative epoch are required", failure.getMessage());
        assertTrue(Files.notExists(path));
    }

    @Test
    void insecureEnvironmentCannotBePersisted() {
        Path path = directory.resolve("insecure.json");

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> OperationRequestFile.write(path, new OperationRequestFile.Request(
                        "http://ops.example.test", "operation-42", 17, "broker-c", "remove", "broker-c:8802", "maintenance")));

        assertEquals("Operation environment must be an HTTPS origin", failure.getMessage());
        assertTrue(Files.notExists(path));
    }

    @Test
    void arbitraryActionCannotBePersisted() {
        Path path = directory.resolve("arbitrary.json");

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> OperationRequestFile.write(path, new OperationRequestFile.Request(
                        "https://ops.example.test", "operation-42", 17, "broker-c", "force", null, "maintenance")));

        assertEquals("Unsupported membership action", failure.getMessage());
        assertTrue(Files.notExists(path));
    }

    @Test
    void directoryCannotBeConvertedIntoRestoredRequest() throws Exception {
        Path path = Files.createDirectory(directory.toRealPath().resolve("directory.json"));
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"));
        Path marker = path.resolve("unchanged.txt");
        Files.writeString(marker, "not an operation request");

        IOException failure = assertThrows(IOException.class, () -> OperationRequestFile.read(path));

        assertEquals("Operation request must be a regular file without symlinks", failure.getMessage());
        assertTrue(Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS));
        assertEquals("not an operation request", Files.readString(marker));
        assertEquals(PosixFilePermissions.fromString("rwx------"), Files.getPosixFilePermissions(path));
    }

    @Test
    void symbolicLinkCannotSubstituteRequestFile() throws Exception {
        Path original = directory.resolve("original.json");
        Path link = directory.resolve("link.json");
        OperationRequestFile.write(original, new OperationRequestFile.Request(
                "https://ops.example.test", "operation-42", 17, "broker-c", "remove", "broker-c:8802", "maintenance"));
        Files.createSymbolicLink(link, original);

        IOException failure = assertThrows(IOException.class, () -> OperationRequestFile.read(link));

        assertEquals("Operation request must be a regular file without symlinks", failure.getMessage());
    }

    @Test
    void requestReadableByOtherUsersIsRejected() throws Exception {
        Path path = directory.resolve("shared.json");
        OperationRequestFile.Request request = new OperationRequestFile.Request(
                "https://ops.example.test", "operation-42", 17, "broker-c", "remove", "broker-c:8802", "maintenance");
        OperationRequestFile.write(path, request);
        Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-r--r--"));

        IOException failure = assertThrows(IOException.class, () -> OperationRequestFile.read(path));

        assertEquals("Operation request must be owner-only", failure.getMessage());
    }

    @Test
    void existingRequestCannotBeSilentlyOverwritten() throws Exception {
        Path path = directory.resolve("existing.json");
        Files.writeString(path, "original request");
        OperationRequestFile.Request replacement = new OperationRequestFile.Request(
                "https://ops.example.test", "replacement", 99, "broker-c", "remove", "broker-c:8802", "maintenance");

        assertThrows(FileAlreadyExistsException.class, () -> OperationRequestFile.write(path, replacement));

        assertEquals("original request", Files.readString(path));
    }

    @Test
    void savedRequestRestoresExactSemanticsWithOwnerOnlyPermissions() throws Exception {
        Path path = directory.resolve("operation.json");
        OperationRequestFile.Request request = new OperationRequestFile.Request(
                "https://ops.example.test", "operation-42", 17, "broker-c", "add-learner",
                "broker-c.example.test:8802", null);

        OperationRequestFile.write(path, request);

        assertTrue(Files.exists(path), "request must be persisted before it can be sent");
        assertEquals(request, OperationRequestFile.read(path));
        assertEquals(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                Files.getPosixFilePermissions(path));
    }
}
