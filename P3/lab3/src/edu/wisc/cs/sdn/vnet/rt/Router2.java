package edu.wisc.cs.sdn.vnet.rt;

import edu.wisc.cs.sdn.vnet.Device;
import edu.wisc.cs.sdn.vnet.DumpFile;
import edu.wisc.cs.sdn.vnet.Iface;

import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;


import java.util.*;
import java.nio.*;
/**
 * @author Aaron Gember-Jacobson and Anubhavnidhi Abhashkumar
 */
public class Router2 extends Device
{
    /** Routing table for the router */
    private RouteTable routeTable;

    /** ARP cache for the router */
    private ArpCache arpCache;

    //stores packet queues for ARP
    private HashMap<Integer, Queue> pq;
    //timer for RIP
    public Timer timer;

    /**
     * Creates a router for a specific host.
     * @param host hostname for the router
     */
    public Router(String host, DumpFile logfile)
    {
        super(host,logfile);
        this.routeTable = new RouteTable();
        this.arpCache = new ArpCache();
        this.pq = new HashMap<Integer, Queue>();
    }

    /**
     * @return routing table for the router
     */
    public RouteTable getRouteTable()
    { return this.routeTable; }

    /**
     * Load a new routing table from a file.
     * @param routeTableFile the name of the file containing the routing table
     */
    public void loadRouteTable(String routeTableFile)
    {
        if (!routeTable.load(routeTableFile, this))
        {
            System.err.println("Error setting up routing table from file "
                    + routeTableFile);
            System.exit(1);
        }

        System.out.println("Loaded static route table");
        System.out.println("-------------------------------------------------");
        System.out.print(this.routeTable.toString());
        System.out.println("-------------------------------------------------");
    }

    /**
     * Load a new ARP cache from a file.
     * @param arpCacheFile the name of the file containing the ARP cache
     */
    public void loadArpCache(String arpCacheFile)
    {
        if (!arpCache.load(arpCacheFile))
        {
            System.err.println("Error setting up ARP cache from file "
                    + arpCacheFile);
            System.exit(1);
        }

        System.out.println("Loaded static ARP cache");
        System.out.println("----------------------------------");
        System.out.print(this.arpCache.toString());
        System.out.println("----------------------------------");
    }

    /**
     * Handle an Ethernet packet received on a specific interface.
     * @param etherPacket the Ethernet packet that was received
     * @param inIface the interface on which the packet was received
     */
    public void handlePacket(Ethernet etherPacket, Iface inIface)
    {
        System.out.println("*** -> Received packet: " +
                etherPacket.toString().replace("\n", "\n\t"));

        //if ARP handle AR{
        if (etherPacket.getEtherType() == Ethernet.TYPE_ARP){
            this.handlePacketARP(etherPacket, inIface);
        }
        else if (etherPacket.getEtherType() == Ethernet.TYPE_IPv4){ // if IP handle IP
            this.handlePacketIP(etherPacket, inIface);
        }
        // else do nothing
    }

    public void handlePacketIP(Ethernet etherPacket, Iface inIface){

        //to keep track if we should stop

        //check to make sure it is a IP packet
        if (etherPacket.getEtherType() != Ethernet.TYPE_IPv4){
            return;
        }

        IPv4 head = (IPv4) etherPacket.getPayload();

        //check checksum
        int checksum = head.getChecksum();
        head.resetChecksum();
        byte[] head_data = head.serialize();
        head.deserialize(head_data, 0, head_data.length);
        int checksum2 = head.getChecksum();
        if (checksum != checksum2) {
            return;
        }

        //check TTL
        if (head.getTtl() == 0) {
            //TODO add code for ICMP TTL here
            return;
        } else {
            head.setTtl((byte) (head.getTtl() - 1));
            if (head.getTtl() == 0) {
                //TODO add code for ICM{ TTL here
                return;
            }
        }


        head.resetChecksum();

        //this is a part to double check things
        //TODO add deistino part un reachable
        //TODO add echo reply
        //TODO handlRIP
        if (head.getDestinationAddress() == inIface.getIpAddress()) {
            return;
        }
        for (Iface iface: interfaces.values()) {
            if (head.getDestinationAddress() == iface.getIpAddress()) {
                return;
            }
        }


        RouteEntry entry = this.routeTable.lookup(head.getDestinationAddress());

        //TODO ICMP desnition net unreachable
        if (entry == null){
            return;
        }

        if (entry.getInterface() == inIface){
            return;
        }

        int nextIP = entry.getGatewayAddress();
        if (nextIP == 0) { // next ip is dest
            nextIP = head.getDestinationAddress();
        }
        ArpEntry nextIPArp = arpCache.lookup(nextIP);

        //TODO
        if (nextIPArp == null) {

            ARP a = new ARP();
            a.setProtocolType(ARP.PROTO_TYPE_IP);
            a.setProtocolAddressLength((byte)4);
            a.setOpCode(ARP.OP_REQUEST);

            a.setHardwareType(ARP.HW_TYPE_ETHERNET);
            a.setHardwareAddressLength((byte)Ethernet.DATALAYER_ADDRESS_LENGTH);
            a.setTargetHardwareAddress(ByteBuffer.allocate(8).putInt(0).array());
            a.setTargetProtocolAddress(nextIP);
            a.setSenderHardwareAddress(inIface.getMacAddress().toBytes());
            a.setSenderProtocolAddress(inIface.getIpAddress());

            Ethernet e = new Ethernet();

            e.setEtherType(Ethernet.TYPE_ARP);
            e.setSourceMACAddress(inIface.getMacAddress().toBytes());
            e.setPayload(a);
            e.setDestinationMACAddress("FF:FF:FF:FF:FF:FF");
            e.serialize();

            if(!pq.containsKey(nextIP)){
                pq.put(nextIP, new LinkedList());
            }

            Queue q = pq.get(nextIP);
            q.add(etherPacket);

            Thread reply = new Thread(new Runnable() {
                public void run() {

                    try{

                        this.sendPacket(e, inIface);
                        Thread.sleep(1000);
                        if (arpCache.lookup(nextIP) != null){
                            return;
                        }

                        this.sendPacket(e, inIface);
                        Thread.sleep(1000);
                        if (arpCache.lookup(nextIP) != null){
                            return;
                        }

                        this.sendPacket(e, inIface);
                        Thread.sleep(1000);
                        if (arpCache.lookup(nextIP) != null){
                            return;
                        }

                        //TODO send destination host unreachable message
                    }
                    catch(Exception e){
                        System.out.println(e);
                        System.out.println("Something went wrong with ARP reply");
                    }
                }
            });
            relpy.start();
        }
        else{
            //prep packet to be sent
            etherPacket.setSourceMACAddress(entry.getInterface().getMacAddress().toBytes());
            etherPacket.setDestinationMACAddress(nextIPArp.getMac().toBytes());

            //send packets
            boolean sent = sendPacket(etherPacket, entry.getInterface());

            //check if it was sent
            if (sent == false){
                System.out.println("Something went wrong, packet wasn't sent");
            }
        }

    }



    public void handlePacketARP(Ethernet etherPacket, Iface inIface){

        if (etherPacket.getEtherType() != Ethernet.TYPE_ARP){
            return;
        }

        ARP head = (ARP) etherPacket.getpayload();

        if (head.getOpCode() != ARP.OP_REQUEST){
            if (head.getOpCode() != ARp.OP_REPLY){
                return;
            }
            else{
                ByteBuffer bb = ByteBuffer.wrap(head.getSenderProtocolAddress());
                int addy = bb.getInt();
                arpCache.insert(new MacAddress(head.getSenderHardwareAddress()), addy);
                Queue sends = pq.get(new Integer(addy));
                while (sends != null && sends.peek() != null){
                    Ethernet temp_p = (Ethernet)sends.poll();
                    p.setDestinationMACAddress(head.getSenderHardwareAddress());
                    this.sendPacket(temp_p, inIface);
                }
            }
        }


        int targ = ByteBuffer.wrap(head.getTargetProtocolAddress()).getInt();
        if (inIface.getIpAddress != targ){
            return;
        }

        Ethernet e = new Ethernet();

        e.setDestinationMACAddress(etherPacket.getsourceMACAddress());
        e.setSourceMACAddress(inIface.getMacAddress().toBytes);
        e.setEtherType(Ethernet.TYPE_ARP);

        ARP a = new ARP();

        a.setProtocol(ARP.PROTO_TYPE_IP);
        a.setProtocolAddressLength((byte)4);
        a.setOPCode(ARP.OP_REPLY);

        a.setHardwareAddressLength((byte)Ethernet.DATALAYER_ADDRESS_LENGTH);
        a.arp.setHardwareType(ARP.HW_TYPE_ETHERNET);
        a.setTargetHardwareAddress(head.getSenderHardwareAddress());
        a.setTargetProtocolAddress(head.getSenderProtocolAddress());
        a.setSenderHardwareAddress(inIface.getMacAddress().toBytes());
        a.setSenderProtocolAddress(inIface.getIpAddress());

        e.setPayload(a);
        e.serialize();
        this.sendPacket(e, inIface);
    }

    public void handlePacketRIP(Ethernet etherPacket, Iface inIface){
      IPv4 head = (IPv4) etherPacket.getPayload();
      if (head.getProtocol() != IPv4){
        return;
      }

      UDP data = (UDP) head.getPayload();
      int checksum = data.getChecksum();
      head.resetChecksum();
      byte[] seri = data.serialize();
      data.deserialize(seri, 0, seri.length);
      int checksum2 = data.getChecksum();
      
      if (checksum != checksum2){
        return;
      }  
      if (data.getDestinationPort() != UDP.RIP_PORT){
        return; 
      }


      RIPv2 rip = (RIPv2) data.getPayLoad();
      if (rip.getCommand() == RIPv2.COMMAND_REQUEST && etherPacket.getDestinationMAC().toLong() == MACAddress.valueOf("FF:FF:FF:FF:FF:FF").toLong() && head.getDestinationAddress() == IPv4.toIPv4Address("224:0.0.9")){
        this.sendRIP(inIface, false, true);
        return;
      }

      for (RIPv2Entry r: rip.getEntries()){
        int addy = r.getAddress();
        int cost = r.getMetric() + 1;
        int next = r.getNextHopAddress();
        int mask = r.getSubnetMask();

        r.setMetric(cost);
        RouteEntry entry = this.routeTable.lookup(addy);
        if (entry == null || entry.getCost() > cost){
          this.routeTable.insert(addy, next, mask, inIface, cost);
          for (Iface ifaces : this.iterfaces.values()) {
            this.sendRIP(inIface, false, false);
          }
        }
      }

    }




    public void forwardRIP(Iface inIface, boolean req, boolean broad){
     
      IPv4 ip = new IPv4(); 
      Ethernet e = new Ethernet();
      UDP udp = new UDP();
      RIPv2 rip = new RIPv2();
      e.setPayload(ip);
      ip.setPayload(udp);
      udp.setPayload(rip);

      e.setSourceMACAddress("FF:FF:FF:FF:FF:FF");
      e.setEtherType(Ethernet.TYPE_IPv4);

      if (broad){
        e.setDestinationMACAddress("FF:FF:FF:FF:FF:FF"); 
      }
      else{
        e.setDestinationMACAddress(inIface.getMacAddress().toBytes());
      }

      if (req){
        rip.setCommand(RIPv2.COMMAND_REQUEST);
      }
      else{
        rip.setCommand(RIPv2.COMMAND_RESPONSE);
      }

      udp.getDestinationPort(UDP.RIP_PORT);
      udp.setSourcePort(UDP.RIP_PORT);

      for (RouteEntry entry : this.routeTable.getAll()){
          int addy = entry.getDestinationAddress();
          int cost = entry.getCost();
          int next = inIface.getIPAddress();
          int mask = entry.getMaskAddress();

          RIPc2Entry rip_entry = new RIPc2Entryi(addy, mask, cost);
          rip_entry.setNextHopAddress(next);
          rip.addEntry(rip_entry);
      }

      e.serialize();
      this.sendPacket(e, inIface);

    }

    public void timerRun(){
      for (Iface iface: this.interfaces.values()){
          this.forwardRIP(iface, false, true);
      }
    }
    class updateTimer extends TimerTask{
        public void run(){
            timerRun();
        }
    }

    public void createRT(){
        for (Iface iface : this.interfaces.values()){
            int mask = iface.getSubnetMask();
            int dest = iface.getIPAddress() & mask;
            this.routeTable.insert(dest, 0, mask, ifaces, 1);
        }

        for (Iface ifaces: this.interfaces.values()){
            this.forwardRIP(ifaces, true, true);
        }
        this.timer = new Timer();
        this.timer.scheduleAtFixedRate(new updateTimer(), 10000, 10000);

    }

    public void icmpError(Ethernet etherPacket, Iface inIface, int type, int code, boolean echo) {
        Ethernet ether = new Ethernet();
        IPv4 ip = new IPv4();
        ICMP icmp = new ICMP();
        Data data = new Data();
        ether.setPayload(ip);
        ip.setPayload(icmp);
        icmp.setPayload(data);

        // set IP fields
        ip.setTtl((byte)64);
        ip.setProtocol(Ipv4.PROTOCOL_ICMP);
        IPv4 srcPacket = (IPv4)etherPacket.getPayload();

        if (echo) {
            ip.setSourceAdress(srcPacket.getDestinationAddress());
        } else {
            ip.setSourceAdress(inIface.getIpAddress()); // not sure if right
        }

        ip.setDestinationAddress(srcPacket.getSourceAddress());

        //set ethernet fields
        ether.setEtherType(Ethernet.TYPE_IPv4);
        ether.setSourceMACAddress(inIface.getMacAddress().toBytes());

        // FIXME think this is right but keep in mind I don't check for null
        // find next hop
        RouteEntry entry = routeTable.lookup(srcPacket.getSourceAddress());
        int nextIP = entry.getGatewayAddress();
        if (nextIP == 0) { // next ip is dest
            nextIP = srcPacket.getSourceAddress();
        }
        ArpEntry nextIPArp = arpCache.lookup(nextIP);
        ether.setDestinationMACAddress(nextIPArp.getMac().toBytes());

        // set ICMP fields
        icmp.setIcmpType((byte)type);
        icmp.setIcmpCode((byte)code);

        // payload
        byte[] srcBytes = srcPacket.serialize();
        byte[] icmpPayload;
        int numBytes;

        if (echo) {
            numBytes = srcPacket.getTotalLength();
            icmpPayload = new byte[numBytes + 4]; // 4 bytes for padding
        } else {
            numBytes = srcPacket.getHeaderLength() * 4 + 8;
            icmpPayload = new byte[numBytes + 4]; // 4 bytes for padding
        }

        // copy source header to payload
        for (int i = 0; i < numBytes; i++) {
            icmpPayload[i + 4] = srcBytes[i];
        }

        data.setData(icmpPayload);
        sendPacket(ether, inIface);
    }
  }
