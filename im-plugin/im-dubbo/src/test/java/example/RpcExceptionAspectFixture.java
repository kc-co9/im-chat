package example;

import com.co.kc.imchat.common.exception.NotFoundException;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * 位于应用扫描包之外的 Dubbo Provider 切面测试夹具。
 */
public final class RpcExceptionAspectFixture {
    private RpcExceptionAspectFixture() {
    }

    public interface Contract {
        void execute();
    }

    @DubboService(interfaceClass = Contract.class)
    public static class Service implements Contract {
        @Override
        public void execute() {
            throw new NotFoundException("internal detail");
        }
    }
}
