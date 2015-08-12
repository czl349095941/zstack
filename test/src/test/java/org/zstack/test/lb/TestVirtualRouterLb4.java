package org.zstack.test.lb;

import junit.framework.Assert;
import org.junit.Before;
import org.junit.Test;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.componentloader.ComponentLoader;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.SimpleQuery;
import org.zstack.header.identity.SessionInventory;
import org.zstack.header.network.l3.L3NetworkInventory;
import org.zstack.header.vm.VmInstanceInventory;
import org.zstack.header.vm.VmNicInventory;
import org.zstack.network.service.lb.*;
import org.zstack.network.service.vip.VipVO;
import org.zstack.network.service.virtualrouter.lb.VirtualRouterLoadBalancerBackend.LbTO;
import org.zstack.network.service.virtualrouter.lb.VirtualRouterLoadBalancerBackend.RefreshLbCmd;
import org.zstack.simulator.appliancevm.ApplianceVmSimulatorConfig;
import org.zstack.simulator.virtualrouter.VirtualRouterSimulatorConfig;
import org.zstack.test.Api;
import org.zstack.test.ApiSenderException;
import org.zstack.test.DBUtil;
import org.zstack.test.WebBeanConstructor;
import org.zstack.test.deployer.Deployer;
import org.zstack.utils.CollectionUtils;
import org.zstack.utils.function.Function;

/**
 * 
 * @author frank
 * 
 * 1. create a lb
 * 2. add a nic
 *
 * confirm the nic added successfully
 *
 * 3. remove the nic
 *
 * confirm the nic removed successfully
 *
 * 4. add the nic again
 * 5. make the operation fail
 *
 * confirm on vr reference is created
 *
 */
public class TestVirtualRouterLb4 {
    Deployer deployer;
    Api api;
    ComponentLoader loader;
    CloudBus bus;
    DatabaseFacade dbf;
    SessionInventory session;
    VirtualRouterSimulatorConfig vconfig;
    ApplianceVmSimulatorConfig aconfig;

    @Before
    public void setUp() throws Exception {
        DBUtil.reDeployDB();
        WebBeanConstructor con = new WebBeanConstructor();
        deployer = new Deployer("deployerXml/lb/TestVirtualRouterLb4.xml", con);
        deployer.addSpringConfig("VirtualRouter.xml");
        deployer.addSpringConfig("VirtualRouterSimulator.xml");
        deployer.addSpringConfig("KVMRelated.xml");
        deployer.addSpringConfig("vip.xml");
        deployer.addSpringConfig("lb.xml");
        deployer.build();
        api = deployer.getApi();
        loader = deployer.getComponentLoader();
        vconfig = loader.getComponent(VirtualRouterSimulatorConfig.class);
        aconfig = loader.getComponent(ApplianceVmSimulatorConfig.class);
        bus = loader.getComponent(CloudBus.class);
        dbf = loader.getComponent(DatabaseFacade.class);
        session = api.loginAsAdmin();
    }
    
    @Test
    public void test() throws ApiSenderException {
        LoadBalancerInventory lb = deployer.loadBalancers.get("lb");
        LoadBalancerVO lbvo = dbf.findByUuid(lb.getUuid(), LoadBalancerVO.class);
        Assert.assertNotNull(lbvo);
        Assert.assertNotNull(lbvo.getProviderType());
        Assert.assertFalse(lbvo.getListeners().isEmpty());
        Assert.assertFalse(lbvo.getVmNicRefs().isEmpty());

        vconfig.refreshLbCmds.clear();
        VmInstanceInventory vm1 = deployer.vms.get("TestVm1");
        final VmNicInventory nic1 = vm1.getVmNics().get(0);
        api.addVmNicToLoadBalancer(lb.getUuid(), nic1.getUuid());
        Assert.assertFalse(vconfig.refreshLbCmds.isEmpty());
        RefreshLbCmd cmd = vconfig.refreshLbCmds.get(0);

        Assert.assertFalse(cmd.getLbs().isEmpty());
        LbTO to = cmd.getLbs().get(0);
        LoadBalancerListenerVO l = lbvo.getListeners().iterator().next();
        Assert.assertEquals(l.getProtocol(), to.getMode());
        Assert.assertEquals(l.getInstancePort(), to.getInstancePort());
        Assert.assertEquals(l.getLoadBalancerPort(), to.getLoadBalancerPort());

        VipVO vip = dbf.findByUuid(lbvo.getVipUuid(), VipVO.class);
        Assert.assertNotNull(vip);
        Assert.assertFalse(vconfig.vips.isEmpty());
        Assert.assertEquals(vip.getIp(), to.getVip());

        String nicIp = CollectionUtils.find(to.getNicIps(), new Function<String, String>() {
            @Override
            public String call(String arg) {
                return arg.equals(nic1.getIp()) ? arg : null;
            }
        });
        Assert.assertNotNull(nicIp);
        lbvo = dbf.findByUuid(lb.getUuid(), LoadBalancerVO.class);
        LoadBalancerVmNicRefVO ref = CollectionUtils.find(lbvo.getVmNicRefs(), new Function<LoadBalancerVmNicRefVO, LoadBalancerVmNicRefVO>() {
            @Override
            public LoadBalancerVmNicRefVO call(LoadBalancerVmNicRefVO arg) {
                return arg.getVmNicUuid().equals(nic1.getUuid()) ? arg : null;
            }
        });
        Assert.assertNotNull(ref);

        vconfig.refreshLbCmds.clear();
        api.removeNicFromLoadBalancer(lb.getUuid(), nic1.getUuid(), null);
        Assert.assertFalse(vconfig.refreshLbCmds.isEmpty());
        cmd = vconfig.refreshLbCmds.get(0);
        Assert.assertFalse(cmd.getLbs().isEmpty());
        to = cmd.getLbs().get(0);
        nicIp = CollectionUtils.find(to.getNicIps(), new Function<String, String>() {
            @Override
            public String call(String arg) {
                return arg.equals(nic1.getIp()) ? arg : null;
            }
        });
        Assert.assertNull(nicIp);
        lbvo = dbf.findByUuid(lb.getUuid(), LoadBalancerVO.class);
        ref = CollectionUtils.find(lbvo.getVmNicRefs(), new Function<LoadBalancerVmNicRefVO, LoadBalancerVmNicRefVO>() {
            @Override
            public LoadBalancerVmNicRefVO call(LoadBalancerVmNicRefVO arg) {
                return arg.getVmNicUuid().equals(nic1.getUuid()) ? arg : null;
            }
        });
        Assert.assertNull(ref);

        vconfig.refreshLbSuccess = false;
        boolean s = false;
        try {
            api.addVmNicToLoadBalancer(lb.getUuid(), nic1.getUuid());
        } catch (ApiSenderException e) {
            s = true;
        }
        Assert.assertTrue(s);
        lbvo = dbf.findByUuid(lb.getUuid(), LoadBalancerVO.class);
        ref = CollectionUtils.find(lbvo.getVmNicRefs(), new Function<LoadBalancerVmNicRefVO, LoadBalancerVmNicRefVO>() {
            @Override
            public LoadBalancerVmNicRefVO call(LoadBalancerVmNicRefVO arg) {
                return arg.getVmNicUuid().equals(nic1.getUuid()) ? arg : null;
            }
        });
        Assert.assertNull(ref);
    }
}
