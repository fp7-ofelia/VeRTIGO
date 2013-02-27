#!/usr/bin/python
from fvregress import *
import string     # really?  you have to do this?
import sys
import xmlrpclib


# start up a flowvisor with 1 switch (default) and two guests

#h= HyperTest(guests=[('localhost',54321),('localhost',54322)],
#    hyperargs=["-v0", "-a", "flowvisor-conf.d-base", "ptcp:%d"% HyperTest.OFPORT],valgrind=valgrindArgs)

wantPause = True

def test_failed(s):
    s = "TEST FAILED!!!: " + s
    print s
    raise Exception(s)


def test_flowdb(s, count, test_type, display=False):
    print "Testing getSwitchFlowDB: %s" % test_type
    flows = s.api.getSwitchFlowDB("1")
    print "Correctly got %d flows" % len(flows)
    if display:
        for flow in flows:
            print "==== Got flow "
            for key,val in flow.iteritems():
                print "     %s=%s" % (key,val)
    if len(flows) != count:
        test_failed("Wanted %d flows, got %d" % (count, len(flows)))
    print "     SUCCESS"
    print "Testing getSliceRewriteDB %s" % test_type
    rewriteDB = s.api.getSliceRewriteDB("alice","1")
    print "Correctly got %d rewrites" % len(rewriteDB)
    if display:
        for flow, rewrites in rewriteDB.iteritems():
            print "==== Got original: %s" % flow
            for rewrite in rewrites:
                print "--------- Rewrite" 
                for key,val in rewrite.iteritems():
                    print "     => %s=%s" % (key,val)
    if len(rewriteDB) != count:
        test_failed("Wanted %d rewrites, got %d" % (count, len(rewriteDB)))
    print "     SUCCESS"


try:

    h= FvRegress()
    port=16633
    h.addController("alice",    54321)
    h.addController("bob",      54322)

    if len(sys.argv) > 1 :
        wantPause = False
        port=int(sys.argv[1])
        timeout=60
        h.useAlreadyRunningFlowVisor(port)
    else:
        wantPause = False
        timeout=5
        h.spawnFlowVisor(configFile="tests-flowdb.xml")
    h.lamePause()
    h.addSwitch(name='switch1',port=port)
    h.addSwitch(name='switch2',port=port)

    user = "fvadmin"
    passwd = "0fw0rk"
    rpcport = 18080
    s = xmlrpclib.ServerProxy("https://" + user + ":" + passwd + "@localhost:" + str(rpcport) + "/xmlrpc")
    s.verbose=True

    if wantPause:
        doPause("start tests")
#################################### Start Tests
    
    flow_mod1 = FvRegress.OFVERSION + \
                          '''0e 00 50 40 00 90 b6 00 00 00 00 00 00 00 00
                          00 00 00 02 00 0c 29 c6 36 8d ff ff 00 00 08 06
                          00 02 00 00 c0 01 f9 7b c0 01 f9 79 00 00 00 00
                          00 00 00 00 00 00 00 00 00 00 00 05 00 00 80 00
                          00 00 01 6f 00 00 00 01 00 00 00 08 00 02 00 00'''
    flow_mod2 = FvRegress.OFVERSION + \
                          '''0e 00 50 40 00 90 b6 00 00 00 00 00 00 00 00
                          00 00 00 02 22 0c 29 c6 36 8d ff ff 00 00 08 06
                          00 02 00 00 c0 01 f9 7b c0 01 f9 79 00 00 00 00
                          00 00 00 00 00 00 00 00 00 00 00 05 00 00 80 00
                          00 00 01 6f 00 00 00 01 00 00 00 08 00 02 00 00'''
    flow_mod3 = FvRegress.OFVERSION + \
                          '''0e 00 50 40 00 90 b6 00 00 00 00 00 00 00 00
                          00 00 00 02 33 0c 29 c6 36 8d ff ff 00 00 08 06
                          00 02 00 00 c0 01 f9 7b c0 01 f9 79 00 00 00 00
                          00 00 00 00 00 00 00 00 00 00 00 05 00 00 80 00
                          00 00 01 6f 00 00 00 01 00 00 00 08 ff fc 00 00'''
    flow_mod3_after = FvRegress.OFVERSION + \
                    '''0e 00 58 00 00 01 04 00 00 00 00 00 00 00 00
                    00 00 00 02 33 0c 29 c6 36 8d ff ff 00 00 08 06
                    00 02 00 00 c0 01 f9 7b c0 01 f9 79 00 00 00 00
                    00 00 00 00 00 00 00 00 00 00 00 05 00 00 80 00
                    00 00 01 6f 00 00 00 01 00 00 00 08 00 02 00 00
                    00 00 00 08 00 03 00 00'''
    h.runTest(name="flowdb install",timeout=timeout,  events= [
          # send flow_mod, make sure it succeeds
          TestEvent( "send","guest",'alice', flow_mod1),
          TestEvent( "recv","switch",'switch1', flow_mod1), 
          TestEvent( "send","guest",'alice', flow_mod2),
          TestEvent( "recv","switch",'switch1', flow_mod2), 
          # this test sends an OFPP_ALL and make sures it expands
          # FIXME: fix API so we can test if the expand worked...
          # right now, this test is half-assed
          TestEvent( "send","guest",'alice', flow_mod3),
          TestEvent( "recv","switch",'switch1', flow_mod3_after), 
          ])

####################################
    test_flowdb(s, 0, "flowdb disabled")
#################################### Start Tests
    print "Testing setConfig track_flows=True"
    try:
        if not s.api.setConfig("flowvisor!track_flows","True"): 
            test_failed("setConfig returned False")
        else:
            print "     SUCCESS"
    except StandardError,e :
            test_failed("setConfig returned: %s" % str(e))
#################################### Start Tests
    flow_mod1 = FvRegress.OFVERSION + \
                          '''0e 00 50 40 00 90 b6 00 00 00 00 00 00 00 00
                          00 00 00 02 00 0c 29 c6 36 8d ff ff 00 00 08 06
                          00 02 00 00 c0 01 f9 7b c0 01 f9 79 00 00 00 00
                          00 00 00 00 00 00 00 00 00 00 00 05 00 00 80 00
                          00 00 01 6f 00 00 00 01 00 00 00 08 00 02 00 00'''
    flow_mod2 = FvRegress.OFVERSION + \
                          '''0e 00 50 40 00 90 b6 00 00 00 00 00 00 00 00
                          00 00 00 02 22 0c 29 c6 36 8d ff ff 00 00 08 06
                          00 02 00 00 c0 01 f9 7b c0 01 f9 79 00 00 00 00
                          00 00 00 00 00 00 00 00 00 00 00 05 00 00 80 00
                          00 00 01 6f 00 00 00 01 00 00 00 08 00 02 00 00'''
    flow_mod3 = FvRegress.OFVERSION + \
                          '''0e 00 50 40 00 90 b6 00 00 00 00 00 00 00 00
                          00 00 00 02 33 0c 29 c6 36 8d ff ff 00 00 08 06
                          00 02 00 00 c0 01 f9 7b c0 01 f9 79 00 00 00 00
                          00 00 00 00 00 00 00 00 00 00 00 05 00 00 80 00
                          00 00 01 6f 00 00 00 01 00 00 00 08 ff fc 00 00'''
    flow_mod3_after = FvRegress.OFVERSION + \
                    '''0e 00 58 00 00 01 04 00 00 00 00 00 00 00 00
                    00 00 00 02 33 0c 29 c6 36 8d ff ff 00 00 08 06
                    00 02 00 00 c0 01 f9 7b c0 01 f9 79 00 00 00 00
                    00 00 00 00 00 00 00 00 00 00 00 05 00 00 80 00
                    00 00 01 6f 00 00 00 01 00 00 00 08 00 02 00 00
                    00 00 00 08 00 03 00 00'''
    h.runTest(name="flowdb install",timeout=timeout,  events= [
          # send flow_mod, make sure it succeeds
          TestEvent( "send","guest",'alice', flow_mod1),
          TestEvent( "recv","switch",'switch1', flow_mod1), 
          TestEvent( "send","guest",'alice', flow_mod2),
          TestEvent( "recv","switch",'switch1', flow_mod2), 
          # this test sends an OFPP_ALL and make sures it expands
          # FIXME: fix API so we can test if the expand worked...
          # right now, this test is half-assed
          TestEvent( "send","guest",'alice', flow_mod3),
          TestEvent( "recv","switch",'switch1', flow_mod3_after), 
          ])

####################################
    test_flowdb(s, 3, "flowdb enabled")
####################################
    flow_expire1 = FvRegress.OFVERSION + \
                '''0b 00 58 00 00 00 01 
                00 00 00 00 00 00 00 00 00 00 00 02 00 0c 29 c6 
                36 8d ff ff 00 00 08 06 00 02 00 00 c0 01 f9 7b 
                c0 01 f9 79 00 00 00 00
                00 00 00 00 00 00 00 00 80 00 00 00 00 00 00 00
                00 00 00 0b 00 00 00 00 00 00 00 00 00 00 01 e1
                00 00 00 00 00 00 02 58
                '''
    h.runTest(name="flowdb flow remove",timeout=timeout,  events= [
          # send flow_mod, make sure it succeeds
          TestEvent( "send","switch",'switch1', flow_expire1),
          TestEvent( "recv","guest",'alice', flow_expire1), 
          ])
#########################################
    test_flowdb(s, 2, "flow removed")
#########################################
    flow_del_all = FvRegress.OFVERSION + \
                          '''0e 00 48 40 00 90 b6 ff ff ff ff 00 00 00 00
                          00 00 00 02 00 0c 29 c6 36 8d ff ff 00 00 08 06
                          00 00 00 00 c0 01 f9 7b c0 01 f9 79 00 00 00 00
                          00 00 00 00 00 00 00 00 00 03 00 05 00 00 80 00
                          00 00 01 6f 00 00 00 01'''
    fm_expand1  = FvRegress.OFVERSION + \
                          '''0e 00 48 00 00 01 08 00 3f ff fa 00 00 00 00
                        00 00 00 02 00 00 00 00 00 00 00 00 00 00 00 00
                        00 00 00 00 c0 01 f9 7b c0 01 f9 79 00 00 00 00
                        00 00 00 00 00 00 00 00 00 03 00 05 00 00 80 00
                        00 00 01 6f 00 00 00 01'''
    fm_expand2  = FvRegress.OFVERSION + \
                          '''0e 00 48 00 00 01 08 00 3f ff fa 00 00 00 01
                        00 00 00 02 00 00 00 00 00 00 00 00 00 00 00 00
                        00 00 00 00 c0 01 f9 7b c0 01 f9 79 00 00 00 00
                        00 00 00 00 00 00 00 00 00 03 00 05 00 00 80 00
                        00 00 01 6f 00 00 00 01'''
    fm_expand3  = FvRegress.OFVERSION + \
                          '''0e 00 48 00 00 01 08 00 3f ff fa 00 02 00 00
                        00 00 00 02 00 00 00 00 00 00 00 00 00 00 00 00
                        00 00 00 00 c0 01 f9 7b c0 01 f9 79 00 00 00 00
                        00 00 00 00 00 00 00 00 00 03 00 05 00 00 80 00
                        00 00 01 6f 00 00 00 01'''
    fm_expand4  = FvRegress.OFVERSION + \
                          '''0e 00 48 00 00 01 08 00 3f ff fa 00 02 00 01
                        00 00 00 02 00 00 00 00 00 00 00 00 00 00 00 00
                        00 00 00 00 c0 01 f9 7b c0 01 f9 79 00 00 00 00
                        00 00 00 00 00 00 00 00 00 03 00 05 00 00 80 00
                        00 00 01 6f 00 00 00 01'''
    fm_expand5  = FvRegress.OFVERSION + \
                          '''0e 00 48 00 00 01 08 00 3f ff fa 00 03 00 00
                        00 00 00 02 00 00 00 00 00 00 00 00 00 00 00 00
                        00 00 00 00 c0 01 f9 7b c0 01 f9 79 00 00 00 00
                        00 00 00 00 00 00 00 00 00 03 00 05 00 00 80 00
                        00 00 01 6f 00 00 00 01'''
    fm_expand6  = FvRegress.OFVERSION + \
                          '''0e 00 48 00 00 01 08 00 3f ff fa 00 03 00 01
                        00 00 00 02 00 00 00 00 00 00 00 00 00 00 00 00
                        00 00 00 00 c0 01 f9 7b c0 01 f9 79 00 00 00 00
                        00 00 00 00 00 00 00 00 00 03 00 05 00 00 80 00
                        00 00 01 6f 00 00 00 01'''
    h.runTest(name="flowdb del all",timeout=timeout,  events= [
          # send flow_mod, make sure it succeeds
          TestEvent( "send","guest",'alice', flow_del_all), 
          TestEvent( "recv","switch",'switch1', fm_expand1),
          TestEvent( "recv","switch",'switch1', fm_expand2),
          TestEvent( "recv","switch",'switch1', fm_expand3),
          TestEvent( "recv","switch",'switch1', fm_expand4),
          TestEvent( "recv","switch",'switch1', fm_expand5),
          TestEvent( "recv","switch",'switch1', fm_expand6),
          ])
#########################################
    test_flowdb(s, 0, "flow all deleted", display=True)
#########################################
# more tests for this setup HERE
#################################### End Tests
finally:
    if wantPause:
        doPause("start cleanup")
    h.cleanup()

