#!/usr/bin/python
from fvregress import *
import string     # really?  you have to do this?
import sys
import xmlrpclib



user="fvadmin"
passwd="0fw0rk"
rpcport=18080
# start up a flowvisor with 1 switch (default) and two guests

wantPause = True

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
        h.spawnFlowVisor(configFile="tests-stats.xml")
    h.lamePause()
    h.addSwitch(name='switch1',port=port)
    h.addSwitch(name='switch2',port=port)


    if wantPause:
        doPause("start tests")
##################################

    # derekso's funky flowstats request
    flow_stats_request = FvRegress.OFVERSION + '''10 00 38 00 00 00 16 00 01 00 00 ff ff ff ff
            00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
            00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
            00 00 00 00 ff 00 ff ff'''
    h.runTest(name="flow stats request (all)", timeout=timeout, events= [
            TestEvent( "send","guest",'alice', flow_stats_request),
            TestEvent( "recv","switch",'switch1', flow_stats_request),
            ])





#########################################
# more tests for this setup HERE
#################################### End Tests
finally:
    if wantPause:
        doPause("start cleanup")
    h.cleanup()

