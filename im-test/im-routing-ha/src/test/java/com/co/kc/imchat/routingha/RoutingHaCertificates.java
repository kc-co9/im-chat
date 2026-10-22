package com.co.kc.imchat.routingha;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** 每个 Broker 独立私钥、唯一 URI 身份；只把公钥证书互相导入信任库。 */
final class RoutingHaCertificates {
    static final String PASSWORD = "changeit";
    static final String PREFIX = "spiffe://im-chat/broker/";

    private RoutingHaCertificates() {
    }

    static List<Path> create(Path directory, List<String> identities) throws Exception {
        List<Path> stores = new ArrayList<>();
        for (String identity : identities) {
            Path store = directory.resolve(identity + ".p12");
            keytool(directory, "-genkeypair", "-alias", identity, "-keyalg", "RSA", "-keysize", "2048",
                    "-validity", "2", "-dname", "CN=" + identity, "-storetype", "PKCS12", "-keystore",
                    store.toString(), "-storepass", PASSWORD, "-keypass", PASSWORD,
                    "-ext", "SAN=URI:" + PREFIX + identity, "-noprompt");
            keytool(directory, "-exportcert", "-alias", identity, "-keystore", store.toString(),
                    "-storepass", PASSWORD, "-file", directory.resolve(identity + ".cer").toString());
            stores.add(store);
        }
        for (int target = 0; target < stores.size(); target++) {
            for (int source = 0; source < stores.size(); source++) {
                if (source != target) {
                    String identity = identities.get(source);
                    keytool(directory, "-importcert", "-alias", identity, "-keystore", stores.get(target).toString(),
                            "-storepass", PASSWORD, "-file", directory.resolve(identity + ".cer").toString(),
                            "-noprompt");
                }
            }
        }
        return List.copyOf(stores);
    }

    private static void keytool(Path directory, String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "keytool").toString());
        command.addAll(List.of(arguments));
        Path output = directory.resolve("keytool.log");
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile()).start();
        if (!process.waitFor(20, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("keytool exceeded 20s budget: " + output);
        }
        if (process.exitValue() != 0) {
            throw new IOException("keytool failed: " + Files.readString(output));
        }
    }
}
