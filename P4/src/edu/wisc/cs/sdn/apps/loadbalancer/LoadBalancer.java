package edu.wisc.cs.sdn.apps.loadbalancer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.openflow.protocol.OFMessage;
import org.openflow.protocol.OFPacketIn;
import org.openflow.protocol.OFType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.wisc.cs.sdn.apps.util.ArpServer;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch.PortChangeType;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.ImmutablePort;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.devicemanager.IDevice;
import net.floodlightcontroller.devicemanager.IDeviceService;
import net.floodlightcontroller.devicemanager.internal.DeviceManagerImpl;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.util.MACAddress;

import edu.wisc.cs.sdn.apps.util.L3Routing;
import edu.wisc.cs.sdn.apps.util.SwitchCommands;

public class LoadBalancer implements IFloodlightModule, IOFSwitchListener,
		IOFMessageListener
{
	public static final String MODULE_NAME = LoadBalancer.class.getSimpleName();
	
	private static final byte TCP_FLAG_SYN = 0x02;
	
	private static final short IDLE_TIMEOUT = 20;
	
	// Interface to the logging system
    private static Logger log = LoggerFactory.getLogger(MODULE_NAME);
    
    // Interface to Floodlight core for interacting with connected switches
    private IFloodlightProviderService floodlightProv;
    
    // Interface to device manager service
    private IDeviceService deviceProv;
    
    // Switch table in which rules should be installed
    private byte table;
    
    // Set of virtual IPs and the load balancer instances they correspond with
    private Map<Integer,LoadBalancerInstance> instances;

    /**
     * Loads dependencies and initializes data structures.
     */
	@Override
	public void init(FloodlightModuleContext context)
			throws FloodlightModuleException 
	{
		log.info(String.format("Initializing %s...", MODULE_NAME));
		
		// Obtain table number from config
		Map<String,String> config = context.getConfigParams(this);
        this.table = Byte.parseByte(config.get("table"));
        
        // Create instances from config
        this.instances = new HashMap<Integer,LoadBalancerInstance>();
        String[] instanceConfigs = config.get("instances").split(";");
        for (String instanceConfig : instanceConfigs)
        {
        	String[] configItems = instanceConfig.split(" ");
        	if (configItems.length != 3)
        	{ 
        		log.error("Ignoring bad instance config: " + instanceConfig);
        		continue;
        	}
        	LoadBalancerInstance instance = new LoadBalancerInstance(
        			configItems[0], configItems[1], configItems[2].split(","));
            this.instances.put(instance.getVirtualIP(), instance);
            log.info("Added load balancer instance: " + instance);
        }
        
		this.floodlightProv = context.getServiceImpl(
				IFloodlightProviderService.class);
        this.deviceProv = context.getServiceImpl(IDeviceService.class);
        
        /*********************************************************************/
        /* TODO: Initialize other class variables, if necessary              */
        
        /*********************************************************************/
	}

	/**
     * Subscribes to events and performs other startup tasks.
     */
	@Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException 
	{
		log.info(String.format("Starting %s...", MODULE_NAME));
		this.floodlightProv.addOFSwitchListener(this);
		this.floodlightProv.addOFMessageListener(OFType.PACKET_IN, this);
		
		/*********************************************************************/
		/* TODO: Perform other tasks, if necessary                           */
		
		/*********************************************************************/
	}
	
	/**
     * Event handler called when a switch joins the network.
     * @param DPID for the switch
     */
	@Override
	public void switchAdded(long switchId) 
	{
		IOFSwitch sw = this.floodlightProv.getSwitch(switchId);
		log.info(String.format("Switch s%d added", switchId));
		
		/*********************************************************************/
		/* TODO: Install rules to send:                                      */
		/*       (1) packets from new connections to each virtual load       */
		/*       balancer IP to the controller                               */
		/*       (2) ARP packets to the controller, and                      */
		/*       (3) all other packets to the next rule table in the switch  */
		
		/*********************************************************************/

		for (Integer ip : instances.keySet()) {
			
			// TCP
			OFMatch match = new OFMatch();
			match.setNetworkProtocol(OFMatch.IP_PROTO_TCP);
			match.setDataLayerType(OFMatch.ETH_TYPE_IPV4);
			match.setNetworkDestination(ip);
			OFAction action = new OFActionOutput(OFPort.OFPP_CONTROLLER);
			List<OFAction> actions = Arrays.asList(action);
			OFInstruction instr = new OFInstructionApplyActions(actions);
			List<OFInstruction> instrs = Arrays.asList(instr);
			SwitchCommands.installRule(sw, this.table, (short)(SwitchCommands.DEFAULT_PRIORITY + 1), match, instrs);
			
			// ARP
			match = new OFTMatch();
			match.setDataLayerType(OFMatch.ETH_TYPE_ARP);
			match.setNetworkDestination(ip);
			action = new OFActionOutput(OFPort.OFPP_CONTROLLER);
			List<OFAction> actions = Arrays.asList(action);
			instr = new OFInstructionApplyActions(actions);
			List<OFInstruction> instrs = Arrays.asList(instr);
			SwitchCommand.installRule(sw, this.table, (short)(SwitchCommands.DEFAULT_PRIORITY + 1), match, instrs);

		}

		// Other packets
		OFMatch match = new OFMatch();
		OFInstruction instr = new OFInstructionGotoTable(L3Routing.table);
		List<OFInstruction> instrs = Arrays.asList(instr);
		SwitchCommands.installRule(sw, this.table, SwitchCommands.DEFAULT_PRIORITY, match, instrs);
	}
	
	/**
	 * Handle incoming packets sent from switches.
	 * @param sw switch on which the packet was received
	 * @param msg message from the switch
	 * @param cntx the Floodlight context in which the message should be handled
	 * @return indication whether another module should also process the packet
	 */
	@Override
	public net.floodlightcontroller.core.IListener.Command receive(
			IOFSwitch sw, OFMessage msg, FloodlightContext cntx) 
	{
		// We're only interested in packet-in messages
		if (msg.getType() != OFType.PACKET_IN)
		{ return Command.CONTINUE; }
		OFPacketIn pktIn = (OFPacketIn)msg;
		
		// Handle the packet
		Ethernet ethPkt = new Ethernet();
		ethPkt.deserialize(pktIn.getPacketData(), 0,
				pktIn.getPacketData().length);
		
		/*********************************************************************/
		/* TODO: Send an ARP reply for ARP requests for virtual IPs; for TCP */
		/*       SYNs sent to a virtual IP, select a host and install        */
		/*       connection-specific rules to rewrite IP and MAC addresses;  */
		/*       ignore all other packets                                    */
	
		// ARP reply	
		if (ethPkt.getEtherType() == Ethernet.TYPE_ARP) {
			ARP arpPayload = (ARP)ethPkt.getPayload();
			int ip = IPv4.toIPv4Address(arpPayload.getTargetProtocolAddress());

			if (this.instances.containsKey(ip)) {
				byte[] mac = this.instances.get(ip).getVirtualMAC();
				ARP arp = new ARP();
				arp.setOpCode(ARP.OP_REPLY);
				arp.setProtocolAddressLength(arpPayload.getProtocolAddressLength());
				arp.setSenderProtocolAddress(ip);
				arp.setProtocolType(arpPayload.getProtocolType());
				arp.setTargetProtocolAddress(arpPayload.getSenderProtocolAddress());
				arp.setHardwareAddressLength(arpPayload.getHardwareAddressLength());
				arp.setSenderHardwareAddress(mac);
				arp.setHardwareType(arpPayload.getHardwareType());
				arp.setTargetHardwareAddress(arpPayload.getSenderHardwareAddress());

				Ethernet ether = new Ethernet();
				ether.setEtherType(Ethernet.TYPE_ARP);
				ether.setDestinationMACAddress(arpPayload.getSourceMACAddress());
				ether.setSourceMACAddress(mac);
				ether.setPayload(arp);
				
				SwitchCommands.sendPacket(sw, (short)pktIn.getInPort(), ether);
			}
		}
	        // TCP reply
		else if (ethPkt.getEtherType() == Ethernet.TYPE_IPv4) {
			IPv4 ipPacket = (IPv4)ethPkt.getPayload();

			if (ipPacket.getProtocol() == IPv4.PROTOCOL_TCP) {
				TCP tcpPacket = (TCP)ipPacket.getPayload();

				if (tcpPacket.getFlags() == TCP_FLAG_SYN) {
					int ipDest = ipPacket.getDestinationAddress();
					int nextIp = this.instances.get(ipDest).getNextHostIP();

					OFMatch match = new OFMatch();
					match.setDataLayerType(Ethernet.TYPE_IPv4);
					match.setNetworkSource(ipPacket.getSourceAddress());
					match.setNetworkDestination(ipDest);
					match.setNetworkProtocol(OFMatch.IP_PROTO_TCP);
					match.setTransportSource(OFMatch.IP_PROTO_TCP, tcpPacket.getSourcePort());
					match.setTransportDestination(OFMatch.IP_PROTO_TCP, tcpPacket.getDestinationPort());

					OFAction ip = new OFActionSetField(OFOXMFieldType.IPV4_DST, nextIp);
					OFAction mac = new OFActionSetField(OFOXMFieldType.ETH_DST, this.getHostMACAddress(nextIp));
					List<OFAction> actions = Arrays.asList(ip, mac);
					OFInstruction instr = new OFInstructionApplyActions(actions);
					OFInstruction def = new OFInstructionGoToTable(L3Routing.table);
					List<OFInstruction> instrs = Arrays.asList(instr, def);

					SwitchCommands.installRule(sw, table, (short)(SwitchCommands.DEFAULT_PRIORITY + 2), match, instrs, 
							SwitchCommands.NO_TIMEOUT, IDLE_TIMEOUT);

					match = new OFMatch();
					match.setDataLayerType(Ethernet.TYPE_IPv4);
					match.setNetworkSource(nextIp);
					match.setNetworkDestination(ipPacket.getSourceAddress());
					match.setNetworkProtocol(OFMatch.IP_PROTO_TCP);
					match.setTransportSource(OFMatch.IP_PROTO_TCP, tcpPacket.getDestinationPort());
					match.setTransportDestination(OFMatch.IP_PROTO_TCP, tcpPacket.getSourcePort());

					ip = new OFActionSetField(OFOXMFieldType.IPV4_SRC, ipDest);
					mac = new OFActionSetField(OFOXMFieldType.ETH_SRC, this.instances.get(ipDest).getVirtualMAC());
					List<OFAction> actions = Arrays.asList(ip, mac);
					instr = new OFInstructionApplyActions(actions);
					def = new OFInstructionGoToTable(L3Routing.table);
					List<OFInstruction> instrs = Arrays.asList(instr, def);

					SwitchCommands.installRule(sw, table, (short)(SwitchCommands.DEFAULT_PRIORITY + 2), match, instrs, 
							SwitchCommands.NO_TIMEOUT, IDLE_TIMEOUT);
				}
			}
		}

		/*********************************************************************/

		
		// We don't care about other packets
		return Command.CONTINUE;
	}
	
	/**
	 * Returns the MAC address for a host, given the host's IP address.
	 * @param hostIPAddress the host's IP address
	 * @return the hosts's MAC address, null if unknown
	 */
	private byte[] getHostMACAddress(int hostIPAddress)
	{
		Iterator<? extends IDevice> iterator = this.deviceProv.queryDevices(
				null, null, hostIPAddress, null, null);
		if (!iterator.hasNext())
		{ return null; }
		IDevice device = iterator.next();
		return MACAddress.valueOf(device.getMACAddress()).toBytes();
	}

	/**
	 * Event handler called when a switch leaves the network.
	 * @param DPID for the switch
	 */
	@Override
	public void switchRemoved(long switchId) 
	{ /* Nothing we need to do, since the switch is no longer active */ }

	/**
	 * Event handler called when the controller becomes the master for a switch.
	 * @param DPID for the switch
	 */
	@Override
	public void switchActivated(long switchId)
	{ /* Nothing we need to do, since we're not switching controller roles */ }

	/**
	 * Event handler called when a port on a switch goes up or down, or is
	 * added or removed.
	 * @param DPID for the switch
	 * @param port the port on the switch whose status changed
	 * @param type the type of status change (up, down, add, remove)
	 */
	@Override
	public void switchPortChanged(long switchId, ImmutablePort port,
			PortChangeType type) 
	{ /* Nothing we need to do, since load balancer rules are port-agnostic */}

	/**
	 * Event handler called when some attribute of a switch changes.
	 * @param DPID for the switch
	 */
	@Override
	public void switchChanged(long switchId) 
	{ /* Nothing we need to do */ }
	
    /**
     * Tell the module system which services we provide.
     */
	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() 
	{ return null; }

	/**
     * Tell the module system which services we implement.
     */
	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> 
			getServiceImpls() 
	{ return null; }

	/**
     * Tell the module system which modules we depend on.
     */
	@Override
	public Collection<Class<? extends IFloodlightService>> 
			getModuleDependencies() 
	{
		Collection<Class<? extends IFloodlightService >> floodlightService =
	            new ArrayList<Class<? extends IFloodlightService>>();
        floodlightService.add(IFloodlightProviderService.class);
        floodlightService.add(IDeviceService.class);
        return floodlightService;
	}

	/**
	 * Gets a name for this module.
	 * @return name for this module
	 */
	@Override
	public String getName() 
	{ return MODULE_NAME; }

	/**
	 * Check if events must be passed to another module before this module is
	 * notified of the event.
	 */
	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) 
	{
		return (OFType.PACKET_IN == type 
				&& (name.equals(ArpServer.MODULE_NAME) 
					|| name.equals(DeviceManagerImpl.MODULE_NAME))); 
	}

	/**
	 * Check if events must be passed to another module after this module has
	 * been notified of the event.
	 */
	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) 
	{ return false; }
}
