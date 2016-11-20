package org.zstack.network.service.eip;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.cloudbus.CloudBusCallBack;
import org.zstack.header.core.workflow.FlowTrigger;
import org.zstack.header.core.workflow.NoRollbackFlow;
import org.zstack.header.core.Completion;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.errorcode.OperationFailureException;
import org.zstack.header.message.MessageReply;
import org.zstack.network.service.eip.EipConstant.Params;
import org.zstack.network.service.vip.*;

import java.util.Map;

/**
 */
@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class EipReturnVipFlow extends NoRollbackFlow {
    @Autowired
    private CloudBus bus;

    @Override
    public void run(final FlowTrigger trigger, Map data) {
        VipInventory vip = (VipInventory) data.get(VipConstant.Params.VIP.toString());
        boolean needUnlock = data.containsKey(Params.NEED_UNLOCK_VIP.toString());
        ReleaseVipMsg msg = needUnlock ? new ReleaseAndUnlockVipMsg() : new ReleaseVipMsg();
        msg.setReleasePeerL3Network(true);
        msg.setVipUuid(vip.getUuid());
        bus.makeTargetServiceIdByResourceUuid(msg, VipConstant.SERVICE_ID, vip.getUuid());
        bus.send(msg, new CloudBusCallBack(trigger) {
            @Override
            public void run(MessageReply reply) {
                if (!reply.isSuccess()) {
                    throw new OperationFailureException(reply.getError());
                }

                trigger.next();
            }
        });
    }
}
