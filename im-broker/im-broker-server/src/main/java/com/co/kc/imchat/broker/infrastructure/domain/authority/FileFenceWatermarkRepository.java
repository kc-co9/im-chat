package com.co.kc.imchat.broker.infrastructure.domain.authority;

import com.co.kc.imchat.common.utils.GeneratorUtils;
import com.co.kc.imchat.broker.domain.authority.repository.FenceWatermarkRepository;
import com.co.kc.imchat.broker.domain.cluster.model.RoutingEpoch;
import com.co.kc.imchat.common.utils.JsonUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

/**
 * 以 fsync 加同目录原子替换持久化本机 routing fence 水位。
 */
public final class FileFenceWatermarkRepository implements FenceWatermarkRepository {

    private final Path markerFile;
    private final String clusterId;
    private final String brokerId;
    private Optional<RoutingEpoch> fencedThroughEpoch;

    public FileFenceWatermarkRepository(Path markerFile, String clusterId, String brokerId) {
        if (markerFile == null) {
            throw new IllegalArgumentException("markerFile must not be null");
        }
        requireText(clusterId, "clusterId");
        requireText(brokerId, "brokerId");
        this.markerFile = markerFile.toAbsolutePath().normalize();
        this.clusterId = clusterId;
        this.brokerId = brokerId;
        this.fencedThroughEpoch = readMarker();
    }

    @Override
    public synchronized Optional<RoutingEpoch> highestFencedEpoch() {
        return fencedThroughEpoch;
    }

    @Override
    public synchronized void advanceTo(RoutingEpoch routingEpoch) {
        if (routingEpoch == null) {
            throw new IllegalArgumentException("routingEpoch must not be null");
        }
        if (fencedThroughEpoch.map(epoch -> routingEpoch.value() <= epoch.value()).orElse(false)) {
            return;
        }
        Marker marker = new Marker(clusterId, brokerId, routingEpoch.value());
        Path parent = markerFile.getParent();
        Path temporaryFile = parent.resolve(markerFile.getFileName() + "." + GeneratorUtils.nextUUID()
                + ".tmp");
        try {
            Files.createDirectories(parent);
            writeAndSync(temporaryFile, JsonUtils.toJson(marker));
            Files.move(temporaryFile, markerFile, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            syncDirectory(parent);
            fencedThroughEpoch = Optional.of(routingEpoch);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to persist routing fence watermark", exception);
        } finally {
            try {
                Files.deleteIfExists(temporaryFile);
            } catch (IOException ignored) {
                // The marker write already failed; a uniquely named temp file is safe to retain.
            }
        }
    }

    private Optional<RoutingEpoch> readMarker() {
        if (!Files.exists(markerFile)) {
            return Optional.empty();
        }
        try {
            Marker marker = JsonUtils.fromJson(Files.readString(markerFile), Marker.class);
            if (marker == null || !clusterId.equals(marker.clusterId())
                    || !brokerId.equals(marker.brokerId())) {
                throw new IllegalStateException("routing fence watermark identity does not match");
            }
            if (marker.fencedThroughEpoch() < 0L) {
                throw new IllegalStateException("routing fence watermark epoch is invalid");
            }
            return Optional.of(new RoutingEpoch(marker.fencedThroughEpoch()));
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read routing fence watermark", exception);
        }
    }

    private static void writeAndSync(Path path, String content) throws IOException {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE)) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
    }

    private static void syncDirectory(Path directory) throws IOException {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private record Marker(String clusterId, String brokerId, long fencedThroughEpoch) {
    }
}
