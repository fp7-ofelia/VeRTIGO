#!/usr/bin/python
from fvregress import *
import string     # really?  you have to do this?
import sys


# start up a flowvisor with 1 switch (default) and two guests

#h= HyperTest(guests=[('localhost',54321),('localhost',54322)],
#    hyperargs=["-v0", "-a", "flowvisor-conf.d-base", "ptcp:%d"% HyperTest.OFPORT],valgrind=valgrindArgs)

wantPause= True
try:

    h= FvRegress()
    port=16633
    h.addController("josh",    50812)

    if len(sys.argv) > 1 :
        wantPause = False
        port=int(sys.argv[1])
        timeout=60
        h.useAlreadyRunningFlowVisor(port)
    else:
        wantPause = False
        timeout=5
        h.spawnFlowVisor(configFile="tests-josh-arp.xml")
    h.lamePause()
    h.addSwitch(name='switch1',port=port)


    if wantPause:
        doPause("start tests")
#################################### Start Tests
    # fm from controller to setup arp
    flow_mod_arp_before = FvRegress.OFVERSION + \
                        '''0e 00 50 40 00 90 b6 00 00 00 00 00 05 00 0c
                            29 82 59 5b 00 0c 29 c6 36 8d ff ff 00 00 08 06
                            00 02 00 00 c0 01 f9 7b c0 01 f9 79 00 00 00 00
                            00 00 00 00 00 00 00 00 00 00 00 05 00 00 80 00
                            00 00 01 6f 00 00 00 01 00 00 00 08 00 30 00 00'''


    flow_mod_arp_after = flow_mod_arp_before
#    flow_mod_arp_after = FvRegress.OFVERSION + \
#                    '''0e 00 50 00 00 01 01 00 0f ff 00 00 05 00 0c
#                    29 82 59 5b 00 0c 29 c6 36 8d ff ff 00 00 08 06
#                    00 02 00 00 c0 01 f9 7b c0 01 f9 79 00 00 00 00
#                    00 00 00 00 00 00 00 00 00 00 00 05 00 00 80 00
#                    00 00 01 6f 00 00 00 01 00 00 00 08 00 30 00 00'''
    h.runTest(name="flow_mod install arp",timeout=timeout,  events= [
            # send flow_mod
            TestEvent( "send","guest",'josh', flow_mod_arp_before),
            # make sure it's not fuxored
            TestEvent( "recv","switch",'switch1', flow_mod_arp_after), ])

#########################################
# more tests for this setup HERE
#################################### End Tests
finally:
    if wantPause:
        doPause("start cleanup")
    h.cleanup()

