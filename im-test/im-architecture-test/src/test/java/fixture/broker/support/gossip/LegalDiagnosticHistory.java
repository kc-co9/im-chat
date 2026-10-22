package fixture.broker.support.gossip;

import com.co.kc.imchat.broker.model.cqrs.dto.GossipRecordDTO;
import com.co.kc.imchat.common.utils.AssertUtils;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

public final class LegalDiagnosticHistory {
    private final Deque<GossipRecordDTO> records = new ArrayDeque<>();

    public synchronized void record(GossipRecordDTO record) {
        AssertUtils.argNotNull("record must not be null", record);
        records.addFirst(record);
        if (records.size() > 2) {
            records.removeLast();
        }
    }

    public synchronized List<GossipRecordDTO> recent() {
        return List.copyOf(records);
    }
}
