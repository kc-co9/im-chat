package com.co.kc.imchat.management.ops.cli.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/** 校验 CLI 文件所在目录的信任边界，防止通过共享可写目录替换配置或恢复记录。 */
public final class TrustedFilePath {

    private TrustedFilePath() {
    }

    /** 使用调用方的公开错误文案校验祖先目录，避免改变 CLI 的错误接口。 */
    public static Path resolve(Path path, String ancestorMessage, String ownerMessage,
                               String writableMessage) throws IOException {
        Path absolute = path.toAbsolutePath();
        Path parent = absolute.getParent().toRealPath();
        UserPrincipal currentUser = currentUser(path);
        UserPrincipal rootUser = rootUser(path);
        List<Path> ancestors = new ArrayList<>();
        for (Path directory = parent; directory != null; directory = directory.getParent()) {
            ancestors.add(directory);
        }
        Collections.reverse(ancestors);
        for (Path directory : ancestors) {
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException(ancestorMessage);
            }
            UserPrincipal owner = Files.getOwner(directory, LinkOption.NOFOLLOW_LINKS);
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(directory, LinkOption.NOFOLLOW_LINKS);
            if (!owner.equals(currentUser) && !owner.equals(rootUser)) {
                throw new IOException(ownerMessage);
            }
            if (permissions.contains(PosixFilePermission.GROUP_WRITE)
                    || permissions.contains(PosixFilePermission.OTHERS_WRITE)) {
                throw new IOException(writableMessage);
            }
        }
        return parent.resolve(absolute.getFileName());
    }

    /** 获取当前进程用户，缺少系统用户信息时让调用方安全失败。 */
    private static UserPrincipal currentUser(Path path) throws IOException {
        return path.getFileSystem().getUserPrincipalLookupService()
                .lookupPrincipalByName(System.getProperty("user.name"));
    }

    /** 获取 root 用户，用于允许 root 所有但不可被其他用户写入的祖先目录。 */
    private static UserPrincipal rootUser(Path path) throws IOException {
        return path.getFileSystem().getUserPrincipalLookupService().lookupPrincipalByName("root");
    }

    /** 校验现有文件为当前用户所有的 owner-only 常规文件。 */
    public static void requireOwnerOnly(Path path, String subject) throws IOException {
        requireOwnerOnly(path, subject, subject + " must belong to the current user", subject + " must be owner-only");
    }

    /** 校验现有文件，并保留调用方对所有权和权限失败的不同错误提示。 */
    public static void requireOwnerOnly(Path path, String subject, String ownerMessage,
                                        String permissionMessage) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException(subject + " must be a regular file without symlinks");
        }
        if (!Files.getOwner(path, LinkOption.NOFOLLOW_LINKS).equals(currentUser(path))) {
            throw new IOException(ownerMessage);
        }
        if (!Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
                .containsAll(Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS))) {
            throw new IOException(permissionMessage);
        }
    }
}
