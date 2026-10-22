package fixture.broker.support.raft.statemachine;
import com.co.kc.imchat.plugin.raft.spi.RaftStateHandler;
public final class LegalStateMachine implements RaftStateHandler<Integer, Integer> {
    public Integer apply(Integer command) { return command; }
    public byte[] snapshot() { return new byte[] {1}; }
    public void restore(byte[] bytes) { }
}
