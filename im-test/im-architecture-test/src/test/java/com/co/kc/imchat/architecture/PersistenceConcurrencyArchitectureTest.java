package com.co.kc.imchat.architecture;

import com.baomidou.mybatisplus.annotation.TableId;
import com.co.kc.imchat.common.domain.shared.model.Identification;
import com.co.kc.imchat.plugin.datasource.dao.BaseEntity;
import com.co.kc.imchat.service.message.domain.chat.model.ImGroupChat;
import com.co.kc.imchat.service.message.domain.chat.model.ImPrivateChat;
import com.co.kc.imchat.service.message.domain.message.model.ImGroupInboxMessage;
import com.co.kc.imchat.service.message.domain.message.model.ImPrivateInboxMessage;
import com.co.kc.imchat.service.social.domain.group.model.GroupMember;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PersistenceConcurrencyArchitectureTest {

    private static final Path REPO_ROOT = findRepositoryRoot();
    private static final Pattern PRIVATE_DB_TRANSFORMER = Pattern.compile(
            "private\\s+[^\\n{;]+\\s+(?:restore|build\\w*|[a-z]\\w*From)"
                    + "\\s*\\(\\s*(?:List<)?Db");
    private static final Pattern INLINE_PERSISTENCE_RESTORE = Pattern.compile(
            "(?s)(?:DomainTransformer\\.INSTANCE|transformer)\\.\\w+From\\([^;]+;"
                    + "\\s*\\w+\\.set(?:PkId|RowVersion)");

    @Test
    void keepsDatabaseTechnicalIdsAndCarriesRowVersion() throws Exception {
        TableId tableId = BaseEntity.class.getDeclaredField("id").getAnnotation(TableId.class);

        assertThat(tableId.type()).isEqualTo(com.baomidou.mybatisplus.annotation.IdType.AUTO);
        assertThat(new Identification().getRowVersion()).isEqualTo(0L);
    }

    @Test
    void persistenceIdentityDoesNotAffectDomainEquality() {
        Identification first = new Identification();
        first.setPkId(1L);
        first.setRowVersion(0L);
        Identification second = new Identification();
        second.setPkId(2L);
        second.setRowVersion(3L);

        assertThat(first).isEqualTo(second);
        assertThat(first.hashCode()).isEqualTo(second.hashCode());
    }

    @Test
    void keepsPersistenceFieldsOutOfDomainBuilders() {
        for (Class<?> domainType : Set.of(
                ImPrivateChat.class,
                ImGroupChat.class,
                ImPrivateInboxMessage.class,
                ImGroupInboxMessage.class,
                GroupMember.class)) {
            Class<?> builderType = Stream.of(domainType.getDeclaredClasses())
                    .filter(type -> type.getSimpleName().equals("Builder"))
                    .findFirst()
                    .orElseThrow();

            assertThat(Stream.of(builderType.getDeclaredMethods())
                    .map(method -> method.getName())
                    .filter(Set.of("pkId", "rowVersion")::contains)
                    .toList())
                    .as(domainType.getSimpleName())
                    .isEmpty();
        }
    }

    @Test
    void messageShardUpdatesIncludeUserIdAndBulkRevocationInvalidatesStaleVersions()
            throws IOException {
        for (String repository : List.of(
                "MysqlImPrivateChatRepository.java",
                "MysqlImGroupChatRepository.java",
                "MysqlImPrivateInboxMessageRepository.java",
                "MysqlImGroupInboxMessageRepository.java")) {
            String source = Files.readString(findFile(repository), StandardCharsets.UTF_8);
            assertThat(source)
                    .as(repository)
                    .contains(".update(")
                    .contains("::getUserId");
        }

        String oauthSessionRepository = Files.readString(
                findFile("MysqlOAuthSessionRepository.java"), StandardCharsets.UTF_8);
        assertThat(oauthSessionRepository)
                .contains(".setIncrBy(DbIamOAuthAuthorization::getVersion, 1)");
    }

    @Test
    void repositoriesDelegateDatabaseToDomainMappingToTransformers() throws IOException {
        try (Stream<Path> files = Files.walk(REPO_ROOT)) {
            List<Path> repositories = files
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .filter(path -> path.toString().contains("/infrastructure/domain/repository/"))
                    .filter(path -> path.getFileName().toString().endsWith("Repository.java"))
                    .toList();

            for (Path repository : repositories) {
                String source = Files.readString(repository, StandardCharsets.UTF_8);
                assertThat(PRIVATE_DB_TRANSFORMER.matcher(source).find())
                        .as(repository.toString())
                        .isFalse();
                assertThat(INLINE_PERSISTENCE_RESTORE.matcher(source).find())
                        .as(repository.toString())
                        .isFalse();
            }
        }
    }

    private static Path findFile(String fileName) throws IOException {
        try (Stream<Path> files = Files.walk(REPO_ROOT)) {
            return files.filter(path -> path.getFileName().toString().equals(fileName))
                    .filter(path -> path.toString().contains("/src/main/java/"))
                    .findFirst()
                    .orElseThrow();
        }
    }

    private static Path findRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve("im-test/im-architecture-test"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Repository root not found");
    }
}
