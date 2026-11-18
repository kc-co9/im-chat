package com.co.kc.imchat.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BoltInvocationBoundaryTest {
    @Test
    void productionConsumersUseControlledBoltBoundary() {
        JavaClasses production = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.co.kc.imchat");
        assertThat(violations(production)).isEmpty();
    }

    @Test
    void rejectsAddressOnlyInvocationContracts() {
        JavaClasses fixtures = new ClassFileImporter().importPackages("com.co.kc.imchat.plugin.bolt.spi.fixture");
        assertThat(violations(fixtures))
                .anyMatch(detail -> detail.contains("AddressInvocationBypass") && detail.contains("address-only"));
    }

    @Test
    void rejectsBusinessRawCodecPipelineAndUnsafeBypasses() {
        JavaClasses fixtures = new ClassFileImporter().importPackages("com.co.kc.imchat.gateway.ws.handler.fixture");
        List<String> failures = violations(fixtures);
        for (String method : List.of("invoke", "replaceCodec", "selectTransport", "writeRaw", "replacePipeline", "writeUnsafe")) {
            assertThat(failures).as("independent bypass method %s", method)
                    .anyMatch(detail -> detail.contains("." + method + "("));
        }
    }

    @Test
    void acceptsControlledOwnerProtocolWrites() {
        JavaClasses fixtures = new ClassFileImporter().importPackages("com.co.kc.imchat.plugin.bolt.channel.fixture",
                        "com.co.kc.imchat.gateway.ws.server.handler.fixture");
        assertThat(violations(fixtures)).isEmpty();
        for (String method : List.of("write", "writeAndFlush")) {
            assertThat(fixtures.stream().flatMap(type -> type.getMethodCallsFromSelf().stream())
                    .anyMatch(call -> call.getTarget().getName().equals(method))).as(method).isTrue();
        }
    }

    private static List<String> violations(JavaClasses classes) {
        List<String> failures = new ArrayList<>();
        for (JavaClass type : classes) {
            for (JavaMethod method : type.getMethods()) {
                if (type.isAssignableTo("com.co.kc.imchat.plugin.bolt.spi.BoltInvoker")
                        && method.getName().equals("invoke")
                        && !method.getRawParameterTypes().isEmpty()
                        && method.getRawParameterTypes().get(0).getName().equals("java.lang.String")) {
                    failures.add(method.getFullName() + " declares address-only Bolt invocation");
                }
            }
            if (inPackage(type, "com.co.kc.imchat.plugin.bolt")) {
                continue;
            }
            for (JavaConstructorCall call : type.getConstructorCallsFromSelf()) {
                if (call.getTargetOwner().isAssignableTo("com.alipay.remoting.connection.ConnectionFactory")) {
                    failures.add(call.getOrigin().getFullName() + " constructs owned codec/factory API "
                            + call.getTarget().getFullName());
                }
            }
            for (JavaMethodCall call : type.getMethodCallsFromSelf()) {
                String target = call.getTargetOwner().getName();
                String method = call.getTarget().getName();
                boolean nativeBoltCall = call.getTargetOwner().isAssignableTo("com.alipay.remoting.rpc.RpcClient")
                        && (method.startsWith("invoke") || method.equals("oneway") || method.equals("option")
                        || method.equals("setConnectionManager"));
                boolean consumer = isBoltConsumer(type);
                boolean rawTransport = consumer && (target.startsWith("io.netty.channel.")
                        && (method.startsWith("write") || method.equals("pipeline") || method.equals("unsafe")
                        || method.startsWith("add") || method.equals("remove") || method.equals("replace")));
                if (nativeBoltCall || rawTransport) {
                    failures.add(call.getOrigin().getFullName() + " calls owned transport API " + call.getTarget().getFullName());
                }
            }
        }
        return failures;
    }

    private static boolean isBoltConsumer(JavaClass type) {
        return inPackage(type, "com.co.kc.imchat.broker")
                || inPackage(type, "com.co.kc.imchat.service")
                || inPackage(type, "com.co.kc.imchat.plugin.gossip")
                || inPackage(type, "com.co.kc.imchat.gateway.ws.sdk")
                || inPackage(type, "com.co.kc.imchat.gateway.ws.handler")
                || type.getDirectDependenciesFromSelf().stream().anyMatch(dependency ->
                inPackage(dependency.getTargetClass(), "com.co.kc.imchat.plugin.bolt")
                        || inPackage(dependency.getTargetClass(), "com.alipay.remoting"));
    }

    private static boolean inPackage(JavaClass type, String packageName) {
        return type.getPackageName().equals(packageName) || type.getPackageName().startsWith(packageName + ".");
    }
}
