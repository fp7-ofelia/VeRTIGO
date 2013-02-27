#!/usr/bin/python

"""@package fv_regress
    Regression framework for testing flowvisor
    """
from threading import Thread,Lock,Condition
import socket
import sys
import binascii # for hexlify() and unhexlify()
import os
import time
import select
import signal
import string
import subprocess
import traceback
import pdb
import re
from struct import *

class FvExcept(Exception):
    pass

def of_cmp(s1, s2):
    """Compare openflow messages except XIDs"""
    if (not s1 and s2) or (s1 and not s2):  # what if one is None
        return False
    #pdb.set_trace()
    val= len(s1) == len(s2) and s1[0:8] == s2[0:8] and (len(s1) <= 16 or s1[16:] == s2[16:])
    #print " of_cmp("+ s1 +" , "+ s2 + ") == "+ str(val)
    return val

def of_diff(got, want, strict=False):
    """Print the first difference between openflow mesasges"""
    #pdb.set_trace()
    err=False
    if not got : 
        got = "***NOTHING***"
    print "Got  : " + got
    print "Want : " + want
    i=0
    for i in range(0,min(8,len(got))):
        if(got[i] != want[i]):
            err=True
            break
    if not err and strict :     # should we skip XID?
        for i in range(8,min(16,len(got))):
            if(got[i] != want[i]):
                err=True
                break
    if not err: 
        for i in range(16,len(got)):
            if(got[i] != want[i]):
                break
    print "       " + " "*i + "^ mismatch here ( nibble %d)" % i
    before_context=2
    after_context=20
    print "GOT ..." + got[max(0,i-before_context):min(len(got),i+after_context)]
    print "WNT ..." + want[max(0,i-before_context):min(len(want),i+after_context)]

def b2a(str):
    """Translate binary to an ascii hex code (almost) human readable form"""
    if str :
        return binascii.hexlify(str)
    else :
        return "***NONE***"
def a2b(str):
    """Translate almost human readible form with whitespace to a binary form"""
    return binascii.unhexlify(str.translate(string.maketrans('',''),string.whitespace))

def findInPathorEnv(binary):
    """Lookup program 'binary' in our environment first (as $BINARY) and then 
        in our path and return the full path to it"""
    #pdb.set_trace()
    envname = re.sub('\.sh','',binary).upper()    # export FLOWVISOR=/my/path/to/flowvisor
    if envname in os.environ:
        return os.environ[envname]
    for dir in ['', '..', "../scripts"] + os.environ['PATH'].split(':'):
        if dir == '':
            sep = ''
        else : 
            sep = '/'
        tmp = dir + sep + binary
        if os.access(tmp,os.X_OK) : 
            return tmp
    return None

def findJar(jar):
    for dir in ['.', './dist', '../dist', '../../dist']:
        if dir == '':
            sep = ''
        else : 
            sep = '/'
        tmp = dir + sep + jar
        if os.access(tmp,os.W_OK) : 
            return tmp
    return None


##########################################################################
class FakeController(Thread):
    DefTimeout=5
    def __init__(self, name, port):
        Thread.__init__(self)
        self.name=name
        self.port=port
        self.switch_lock = Lock()
        self.sliced_switches = {}
        self.listen_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.listen_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR,1)
        #self.listen_sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEPORT,1)
        self.listen_sock.bind(('',port))
        self.listen_sock.listen(5)
        self.setDaemon(True)
        print "    Spawning new Controller " + self.name + " on listenning on port " + str(port)
    def run(self):
        print "        Controller" + self.name + ": Starting io loop"
        while 1 :
            (sock, address) = self.listen_sock.accept()
            self.switch_lock.acquire()
            print "    Controller " + self.name + ": got a new connection from " + address[0] + ":"+ str(address[1]) + " : spawning new FakeSwitch"
            sw = FakeSwitch("sliced_switch_"+self.name+"_"+ str(len(self.sliced_switches)),sock=sock)
            sw.start()
            print "    Controller " + self.name + ": sending hello"
            sw.send(a2b(FvRegress.HELLO))
            m = sw.recv_blocking(timeout=FakeController.DefTimeout)
            if not of_cmp(FvRegress.HELLO,b2a(m)) :
                raise FvExcept("mismatched hellos in FakeController " + self.name)
            print "    Controller " + self.name + ": sending features request"
            sw.send(a2b(FvRegress.FEATURE_REQUEST))
            print "    Controller " + self.name + ": waiting for switch features"
            m = sw.recv_blocking(timeout=FakeController.DefTimeout)
            if not m :
                raise FvExcept("Got no feature_response from sliced switch " + address[0] + ":" + str(address[1]) + \
                    " on FakeController " + self.name)
            if len(m) < 32 :
                raise FvExcept("Got short feature_response from sliced switch " + address[0] + ":" + str(address[1]) + \
                    " on FakeController " + self.name)
            print "    Controller " + self.name + ": got switch features"
            dpid = unpack("!Q ",m[8:16])[0]
            print "    Controller " + self.name + ": got a connection from switch with dpid= '" + str(dpid) + "'"
            self.sliced_switches[dpid]=sw
            self.switch_lock.release()
    def name(self):
            return self.name
    def getSwitch(self, dpid):
        self.switch_lock.acquire()
        try:
            switch = self.sliced_switches[dpid]
        except (KeyError):
            self.cleanup()
            raise FvExcept("tried to access switch with dpid='" + str(dpid) + "' on fake controller " + self.name)
        self.switch_lock.release()
        return switch
    def getSwitches(self) :
        return self.sliced_switches
    def set_dead(self) :
        for sw in self.sliced_switches.values():
            sw.set_dead()

##########################################################################
class FakeSwitch(Thread):
    """ Acts as both a physical and sliced switch """
    BUFSIZE=4096
    def __init__(self,name, sock=None,host=None,port=None):
        usage= """Must specify one of socket or host+port"""
        Thread.__init__(self)
        if sock == None and ( host == None or port == None) :
            raise FvExcept(usage)
        # Setup socket
        if sock != None :
            self.sock= sock
            how = " on port " + str(sock.getsockname())
        else :
            self.sock= socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            if self.sock == None :
                raise FvExcept("socket returned None")
            self.sock.connect((host,port))
            if self.sock == None :
                raise FvExcept("connect returned None")
            how = " with connection " + host + ":" + str(port) + " " +  str(self.sock.getsockname())
        print "    Created switch "+ name + how
        # set up msg queues and locks
        self.sock.setsockopt(socket.SOL_TCP, socket.TCP_NODELAY,1)
        self.name = name
        self.msg_cond = Condition()
        self.msgs = []
        self.alive=True
        self.setDaemon(True)
    def run(self):
        print "    Starting io loop for switch "+ self.name
        while self.alive :
            try:
                m = self.sock.recv(FakeSwitch.BUFSIZE)
                if m == '' :
                    if self.alive:
                        print "Switch " + self.name + " got EOF ; exiting..."
                        self.alive=False
                    return
            except (Exception), e :
                if self.alive:
                    print "Switch " + self.name + " got " + str(e) + "; exiting..."
                return
            #print "----------------- got packet"
            self.msg_cond.acquire()
            msgs = self.of_frame(m)
            for m in msgs :
                self.msgs.append(m)
                self.msg_cond.notify()
            self.msg_cond.release()
    def send(self,m) :
        return self.sock.send(m)
    def recv(self) :
        self.msg_cond.acquire()
        if len(self.msgs) > 0 :
            m = self.msgs.pop()
        else :
            m = None
        self.msg_cond.release()
        return m
    def recv_blocking(self, timeout=None) :
        self.msg_cond.acquire()
        if len(self.msgs) == 0 :     # assumes wakeup by notify() -- no stampeed!
            self.msg_cond.wait(timeout)
        if len(self.msgs) == 0 :
            return None    # timed out
        m = self.msgs.pop(0)
        self.msg_cond.release()
        return m
    def of_frame(self, m):
        msgs = []
        while 1 :
            if len(m) < 8 :
                raise FvExcept(" bad framing in recv() = m == '" + b2a(m) +"'")
            (size,) = unpack("!2x H",m[0:4])
            #pdb.set_trace()
            msgs.append(m[0:size])
            if len(m) > size :
                m=m[size:]
            else :
                return msgs
    def is_alive(self):
        return self.alive
    def set_dead(self):
        self.alive=False

###########################################################################
class TestEvent:
    send = 'send'
    recv = 'recv'
    clear = 'clear?'
    delay = 'delay'
    switch = 'switch'
    countSwitches = 'countSwitches'
    guest = 'guest'
    actions = [ send, recv , clear, delay, countSwitches]
    actors  = [ guest, switch ]
    def __init__(self,action,actor,actorID,packet,actorID2='switch1',strict=False):
        # action=string.lower(action)    # lame with extra lamesauce
        # action=string.lower(actor)
        if not action in TestEvent.actions:
            raise "Bad action: %s ; needs to be one of %s " % [action,' '.join(TestEvent.actions)]
        if not actor in TestEvent.actors:
            raise "Bad actor : %s ; needs to be one of %s " % [actor ,' '.join(TestEvent.actors )]
        self.action=action
        self.actorID=actorID
        self.actorID2=actorID2
        self.actor=actor
        if packet != None :
            self.packet=packet.translate(string.maketrans('',''),string.whitespace)
        self.strict = strict
##########################################################################

class FvRegress:
    OFVERSION = "01"
    OFPORT=16633
    HELLO = OFVERSION + "0000085680f7a9"
    FEATURE_REQUEST = OFVERSION + "0500085680f7aa"
    FLOW_MOD_FLUSH = b2a(a2b (OFVERSION + '''
        0e 00 48 ef be fe ca 00 3f ff ff 00 00 00 00
        00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
        00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
        00 00 00 00 00 00 00 00 00 03 00 00 00 00 00 00
        ff ff ff ff ff ff 00 00
        '''))
    logfile = 'fv_regress.log'

    def __init__(self):
        # set up the guests
        self.fakeControllers={}
        self.fakeSwitches={}
        self.name2dpid = {}
        self.dpid2name = {}
        self.unique_dpid = 1
        self.firstTest = True
        self.fv_child=None
    def setDpid2Name(self, dpid,name):
        self.dpid2name[dpid]=name
        self.name2dpid[name]=dpid
        print "    Mapping dpid "+str(dpid) +" to " + name
    def getDpidFromName(self,name):
        #print "Looking up dpid by name=" + name
        try :
            dpid = self.name2dpid[name]
            #print "        Mapped name " + name + " to dpid "+str(dpid)
            return dpid
        except (KeyError):
            self.cleanup()
            raise FvExcept("Switch named " + name + " does not have a matching dpid")
    def addController(self, name, port):
        fc = FakeController(name,port)
        self.fakeControllers[name]= fc
        fc.start()
    def spawnFlowVisor(self,configFile="test-base.xml",port=OFPORT,fv_cmd="flowvisor.sh",fv_args=["-d","DEBUG", "-l"]):
        """start the flowvisor"""
        cmd = findInPathorEnv(fv_cmd)
        if cmd:
            print "    Using flowvisor from : " + cmd
        else:
                raise FvExcept("could not find " +fv_cmd )
        cmdline = [cmd] + fv_args + [configFile]
        print "    Spawning '" +  " ".join(cmdline) 

        self.logfile = open(FvRegress.logfile,'w+')
        self.fv_child=subprocess.Popen(cmdline,stdout=self.logfile.fileno(),
                stderr=self.logfile.fileno(),close_fds=True)
        print '    flowvisor spawned at Pid=%d (output stored in %s) ' %  ( self.fv_child.pid, FvRegress.logfile)
    def useAlreadyRunningFlowVisor(self,port=None) :
        if(port):
            FvRegress.OFPORT = port
        self.fv_child = None
        self.logfile = open(FvRegress.logfile,'w+')
        print "     Using already spawned flowvisor running on tcp port %d" % FvRegress.OFPORT
    def addSwitch(self,name,port=OFPORT, dpid=None, switch_features=None, nPorts=4):
        sw = FakeSwitch(name,host='localhost', port=port)
        self.fakeSwitches[name]=sw
        print "    Switch " + name + " sent hello"
        if dpid == None :
            dpid = self.unique_dpid
            self.unique_dpid = self.unique_dpid +1
        sw.send(a2b(FvRegress.HELLO))
        sw.start()
        self.setDpid2Name(dpid,name)
        try:
            m = sw.recv_blocking(timeout=3)
            if not of_cmp(b2a(m),FvRegress.HELLO) :
                of_diff(b2a(m), FvRegress.HELLO)
                raise FvExcept("Failed to get hello from flowvisor for new switch " + name + " got "+ b2a(m))
            print "    addSwitch: Got hello flush for " + name

            m = sw.recv_blocking(timeout=3)
            if not of_cmp(b2a(m),FvRegress.FLOW_MOD_FLUSH) :
                of_diff(b2a(m), FvRegress.FLOW_MOD_FLUSH)
                raise FvExcept("Failed to get a flow_mod flush from flowvisor for new switch " + name + " got " + b2a(m))
            print "    addSwitch: Got flow_mod flush for " + name

            m = sw.recv_blocking(timeout=3)
            if not of_cmp(b2a(m),FvRegress.FEATURE_REQUEST) :
                of_diff(b2a(m), FvRegress.FEATURE_REQUEST)
                raise FvExcept("Failed to get features_request from flowvisor for new switch " + name + " got " + b2a(m))
            print "    addSwitch: Got feature_request (from FV) for " + name

            if switch_features == None:
                switch_features = self.make_feature_reply(nPorts=nPorts, dpid=dpid, switchNum=len(self.fakeSwitches))
            else :
                switch_features = a2b(switch_features)
            switch_features= switch_features[0:4] + m[4:8] + switch_features[8:]    # match xids
            sw.send(switch_features)
            print "    addSwitch: Sent switch_features to flowvisor"
            # NOW repeat for fake controller
            for cont in self.fakeControllers.keys() :
                print "    addSwitch: Waiting for features_request from fakeController '" + cont + "'"
                m = sw.recv_blocking(timeout=3)
                if not of_cmp(b2a(m),FvRegress.FEATURE_REQUEST) :
                    raise FvExcept("Failed to get features_request from fake controller " + \
                        cont + " for new switch " + name + " got " + b2a(m))
                print "    addSwitch: Got feature_request (from FC " + cont + "(?)) for " + name
                switch_features= switch_features[0:4] + m[4:8] + switch_features[8:]    # match xids
                sw.send(switch_features)
                print "    addSwitch: Sent switch_features response: (to fake controller " + cont  + "?)"
        except (Exception),e :
            print "############ Test %s FAILED! :exception=%s" % (name,e)
            print str(e)
            self.cleanup()
            raise(e)
    def make_feature_reply(self, dpid, nPorts=4, xid=1, switchNum=0):

        size = nPorts * 48 + 32
        capabilities_field = 7
        actions_field = 1

        features_reply  = FvRegress.OFVERSION +  \
                '06' + \
                b2a(pack("!H I Q I B 3x I I",
                    size,
                    xid,
                    dpid,
                    128,    # nbuffers
                    2,    # ntables,
                    capabilities_field,
                    actions_field,
                    ))
        for i in range(0,nPorts) :
            features_reply += self.make_phy_port(port_no=i, name="port " + str(i), addr=str(dpid) + str(i * 10000))
        return a2b(features_reply)
    def make_phy_port(self, name, addr, port_no) :
        return b2a( pack( "!H 6s 16s I I I I I I",
            port_no,
            addr,
            name,
            0, # config
            0, # state
            0, # curr features
            0, # advertized features
            0, # supported features
            0, # peer features (!?)
            ))
    def newconfig(self):
        print "Reading new config (sending kill -HUP to pid=%d)" %  self.fv_child.pid
        os.kill(self.fv_child.pid,signal.SIGHUP)
    def cleanup(self):
        # now clean up
        # should not need to clean up fake_switch or fake_controller threads; they are daemons
        if self.fv_child :
            print "Cleaning up (killing pid=%d)" %  self.fv_child.pid
            os.kill(self.fv_child.pid,signal.SIGUSR1)
        else :
            print "Not killing fv process: using already running flowvisor"
        print "Cleaning up fake switches"
        if self.fakeSwitches:
            for sw in self.fakeSwitches.values() :
                sw.set_dead()
        self.fakeSwitches=None
        print "Cleaning up fake controllers"
        if self.fakeControllers:
            for cont in self.fakeControllers.values() :
                cont.set_dead()
        self.fakeControllers=None
        print "Sleeping for a second to let things die"
        time.sleep(1)
        print "Done cleaning up"
    def runTest(self,name,events,timeout=5,allowFail=False):
        count=0
        if self.firstTest :
            print "#################################### Init Done #################"
            print "    Sleeping a bit before first test to make sure flowvisor has stabilized"
            time.sleep(1.5)
            self.firstTest=False
        print "############ Starting Test : %s" % name
        try:
            for e in events:
                if e.action == TestEvent.send:
                    ret=self.runSendTest(e,count)
                elif e.action == TestEvent.recv:
                    ret=self.runRecvTest(e,count,recvTimeout=timeout/2)
                elif e.action == TestEvent.clear:
                    ret=self.runClear(e,count,recvTimeout=timeout/2)
                elif e.action == TestEvent.countSwitches:
                    ret=self.runCountSwitches(e,count,recvTimeout=timeout/2)
                elif e.action == TestEvent.delay:
                    time.sleep(1)
                    ret=True
                else:
                    raise FvExcept("Unhandled event type %s for event %d " % [e.action,count])
                if not ret :
                    if(allowFail):
                        print "############ Test %s FAILED! -- ignoring" % name
                    else:
                        raise FvExcept("############ Test %s FAILED!" % name)
                    return ret
                count+=1
        except (Exception),e :
            print "############ Test %s FAILED! : exception= %s" % (name,e)
            self.cleanup()
            traceback.print_exc()
            raise(e)
        print "############ Test %s SUCCEEDED!" % name
        return True
    def lamePause(self,msg="Sleeping to let FV initalize", pause=1.0):
        print msg + ": " + str(pause) + " seconds"
        time.sleep(pause)
    def runSendTest(self,event,count):
        msg= "%s packet %d from %s %s" % ( event.action, count, event.actor, event.actorID)
        #packet = binascii.unhexlify(event.packet)
        packet = a2b(event.packet)

        if event.actor == TestEvent.switch:
            print msg
            self.fakeSwitches[event.actorID].send(packet)
        elif event.actor == TestEvent.guest:
            sw = self.fakeControllers[event.actorID].getSwitch(self.getDpidFromName(event.actorID2))
            msg = msg + " to switch %s" % (event.actorID2)
            try:
                cont = self.fakeControllers[event.actorID]
            except (KeyError):
                self.cleanup()
                raise FvExcept("unknown actor " + event.actorID)
            try:
                sw = cont.getSwitch(self.getDpidFromName(event.actorID2))
            except (KeyError):
                self.cleanup()
                raise FvExcept("unknown switch " + event.actorID2 + " connected to guest " +event.actorID)
            print msg
            sw.send(packet)
        else :
            raise "Unhandled actor %s" %  event.actor
        print msg + " (sent " + str( len(packet) ) + " bytes)"
        return True    # this always succeeds if we don't timeout
    def runRecvTest(self,event,count,recvTimeout=1):
        msg= "%s packet %d from %s %s " % ( event.action, count, event.actor, event.actorID)
        ret=True
        print msg
        if event.actor == TestEvent.switch:
            sw = self.fakeSwitches[event.actorID]
        elif event.actor == TestEvent.guest:
            sw = self.fakeControllers[event.actorID].getSwitch(self.getDpidFromName(event.actorID2))
            msg = msg + " from switch %s" % (event.actorID2)
        else :
            raise "Unhandled actor %s" % event.actor
        if event.strict :
            msg = msg + " (STRICT_XID)"
        correct_packet = a2b(event.packet)
        #print '                select(recvTimeout=%d)' % recvTimeout
        packet = sw.recv_blocking(timeout=recvTimeout)
        success = self.comparePackets(packet,event)

        if success :
            print msg + " (read " + str(len(packet)) + " bytes - correct)"
        elif packet:
            print msg + " (FAILED! got "+ str(len(packet)) + " bytes but was expecting " + str(len(correct_packet))+" bytes )"
        else :
            print msg + " (FAILED! got ZERO bytes but was expecting " + str(len(correct_packet))+" bytes )"
        return success
    def runCountSwitches(self,event,count,recvTimeout=1):
        msg= "%s test %d " % ( event.action, count)
        cont = self.fakeControllers[event.actorID]
        count=0
        switches = cont.getSwitches()
        for dpid in switches :
            if switches[dpid].isAlive() :
                count = count + 1
        if count == event.actorID2 :
            print msg + " SUCCESS: user " + event.actorID + " has " + str(count) + " switches"
            return True
        else :
            print msg + " FAILED: user " + event.actorID + " has " + str(count) + " switches not " + str(event.actorID2)
            return False

    def runClear(self,event,count,recvTimeout=1):
        msg= "%s test %d " % ( event.action, count)
        ret=True
        print msg

        for sw in self.fakeSwitches.values() :
            packet = sw.recv()
            if  packet != None :
                print msg + " (FAILED)"
                self.comparePackets(packet,event)
                return False
        for cont in self.fakeControllers.values() :
            for sw in cont.getSwitches().values() :
                packet = sw.recv()
                if  packet != None :
                    print msg + " (FAILED) controller " + cont.name + " had a message"
                    self.comparePackets(packet,event)
                    return False
        return True
    def comparePackets(self,gotP,event):
        #pdb.set_trace()
        if gotP != None :
            got = b2a(gotP)
        else :
            got = ''
        if event.strict :
            success =  got == event.packet
        else :
            success = of_cmp(got, event.packet)
        if not success :
            of_diff(got=got, want=event.packet,strict=event.strict)
        return success
    def doPause(str):
        print "Press RETURN to %s" % str
        sys.stdin.readline()
    def parseConfig(configFile, alreadyRunning=False, port=OFPORT,*args):
        h = FvRegress()
        if alreadyRunning : 
            print "##ParseConfig: connecting to already running flowvisor"
            h.useAlreadyRunningFlowVisor(port=port)
        else :
            print "##ParseConfig: spawning flowvisor"
            h.spawnFlowVisor(configFile=configFile,port=port*args)
        return h
    parseConfig = staticmethod(parseConfig)    # fugly python hackery


def doPause(str):
    print "======= Press RETURN to %s" % str
    sys.stdin.readline()



if __name__ == '__main__':
    debug=False
    config_request =  FvRegress.OFVERSION + '0700085680f7a9'
    config_request_translated =      FvRegress.OFVERSION + '07000804010000'    # flowvisor should translate xid
    config_request_translated2 =      FvRegress.OFVERSION + '07000805010000'    # flowvisor should translate xid
    h= FvRegress()
    port=16633
    try: 
        h.addController("alice",    54321)
        h.addController("bob",      54322)
        if len(sys.argv) > 1 :
            port=int(sys.argv[1])
            h.useAlreadyRunningFlowVisor(port)
        else:
            h.spawnFlowVisor(configFile="tests-base.xml")
        h.lamePause()
        h.addSwitch(name='switch1',port=port)
        h.addSwitch(name='switch2',port=port)

        if debug:
            h.doPause("start tests")
        h.runTest("config request test", [
                TestEvent( "send","guest",'alice', packet=config_request),
                TestEvent( "recv","switch",'switch1', packet=config_request_translated),        # turn off strict xid checking
                TestEvent( "send","guest",'alice',actorID2='switch2', packet=config_request),
                TestEvent( "recv","switch",'switch2', packet=config_request_translated2),
                ])

        if debug:
            h.doPause("do cleanup")
    finally:
        h.cleanup()
