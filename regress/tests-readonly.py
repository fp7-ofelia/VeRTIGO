#!/usr/bin/python
from fvregress import *
import string     # really?  you have to do this?
import sys


# start up a flowvisor with 1 switch (default) and two guests

#h= HyperTest(guests=[('localhost',54321),('localhost',54322)],
#    hyperargs=["-v0", "-a", "flowvisor-conf.d-base", "ptcp:%d"% HyperTest.OFPORT],valgrind=valgrindArgs)

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
        h.spawnFlowVisor(configFile="tests-readonly.xml")
    h.lamePause()
    h.addSwitch(name='switch1',port=port)
    h.addSwitch(name='switch2',port=port)

    if wantPause:
        doPause("start tests")

#################################### Start Tests
    feature_request =      FvRegress.OFVERSION + '05 0008 2d47 c5eb'
    feature_request_after =  FvRegress.OFVERSION + '05 0008 0000 0102'
    h.runTest(name="feature_request",timeout=timeout,  events= [
            TestEvent( "send","guest","alice", feature_request),
            TestEvent( "recv","switch","switch1", feature_request_after,strict=True),
            ])
######################################
    bad_flow_mod = FvRegress.OFVERSION + '''0e 00 48 01 01 00 00 00 00 00 00 00 02 00 10
    18 07 67 87 00 0d b9 15 c0 44 ff ff 08 00 11 00
    c0 a8 02 fe c0 a8 02 02 00 43 00 44 00 00 00 05
    00 00 80 00 00 17 70 97 40 6f 98 02 00 00 00 00
    00 00 00 08 00 01 00 00'''
    err1 =  FvRegress.OFVERSION + '''01 00 54 00 00 00 00 00 03 00 02 01 0e 00 48
        00 00 01 03 00 00 00 00 00 02 00 10 18 07 67 87
        00 0d b9 15 c0 44 ff ff 08 00 11 00 c0 a8 00 00
        c0 a8 02 02 00 43 00 44 00 00 00 05 00 00 80 00
        00 17 70 97 40 6f 98 02 00 00 00 00 00 00 00 08
        00 01 00 00'''
    h.runTest(name="bad flow_mod write",timeout=timeout,  events= [
            TestEvent( "send","guest","alice", bad_flow_mod),
            TestEvent( "recv","guest","alice", err1),
            ])
######################################
    ping_in = FvRegress.OFVERSION + '''0a 00 74 00 00 00 00 00 00 35 db 00 62 00 01
    00 00 00 1c f0 ed 98 5a 00 22 41 fa 73 01 08 00
    45 00 00 54 f3 b9 00 00 40 01 00 97 c0 a8 02 7c
    c0 a8 02 8c 08 00 06 05 0d 07 00 37 d8 0c b1 49
    6b 63 05 00 08 09 0a 0b 0c 0d 0e 0f 10 11 12 13
    14 15 16 17 18 19 1a 1b 1c 1d 1e 1f 20 21 22 23
    24 25 26 27 28 29 2a 2b 2c 2d 2e 2f 30 31 32 33
    34 35 36 37'''
    h.runTest(name="lookup on packet_in ",timeout=timeout,  events= [
            TestEvent( "send","switch","switch1", ping_in),
            TestEvent( "recv","guest","bob", ping_in),
            ])
######################################
    legit_flow_mod2 = FvRegress.OFVERSION + '''0e 00 50 02 01 00 00 00 00 00 00 00 01 00 22
    41 fa 73 01 00 1c f0 ed 98 5a ff ff 08 00 01 00
    c0 a8 02 7c c0 a8 02 8c 00 08 00 00 00 00 00 05
    00 00 80 00 ff ff ff ff ff ff 00 00 00 00 00 00
    00 00 00 08 00 01 00 00 00 00 00 08 00 00 00 00'''
    err =  FvRegress.OFVERSION + '''01 00 5c 00 00 00 00 00 03 00 02 01 0e 00 50
    00 00 01 04 00 00 00 00 00 01 00 22 41 fa 73 01
    00 1c f0 ed 98 5a ff ff 08 00 01 00 c0 a8 00 00
    c0 a8 02 8c 00 08 00 00 00 00 00 05 00 00 80 00
    ff ff ff ff ff ff 00 00 00 00 00 00 00 00 00 08
    00 01 00 00 00 00 00 08 00 00 00 00'''
    h.runTest(name="bad flow_mod2",timeout=timeout,  events= [
            TestEvent( "send","guest","alice", legit_flow_mod2),
            TestEvent( "recv","guest","alice", err),
            ])
######################################
    # this is a valid lldp packet
    lldp_packet_out = FvRegress.OFVERSION + '''0d 00 3a 00 00 00 00 ff ff ff ff ff fd 00 08
    00 00 00 08 00 00 00 00 f1 23 20 00 00 01 00 21
    5c 54 a6 a1 88 cc 02 07 04 00 12 e2 98 a5 ce 04
    03 02 00 00 06 02 00 78 00 00'''
    lldp_after_fv = FvRegress.OFVERSION + \
        '''0d 00 5e 00 00 00 00 ff ff ff ff ff fd 00 08
        00 00 00 08 00 00 00 00 f1 23 20 00 00 01 00 21
        5c 54 a6 a1 88 cc 02 07 04 00 12 e2 98 a5 ce 04
        03 02 00 00 06 02 00 78 00 00 02 24 07 61 6c 69
        63 65 00 20 20 20 20 6d 61 67 69 63 20 66 6c 6f
        77 76 69 73 6f 72 31 00 06 15 de ad ca fe'''


    h.runTest(name="LLDP packet_out",timeout=timeout,  events= [
            TestEvent( "send","guest","alice", lldp_packet_out),
            TestEvent( "recv","switch","switch1", lldp_after_fv),
            ])
    # note this looks like an lldp packet, but is not: dl_type == 88aa instead
    bad_packet_out = FvRegress.OFVERSION + '''0d 00 3a 00 00 00 00 ff ff ff ff ff fd 00 08
    00 00 00 08 00 00 00 00 f1 23 20 00 00 01 00 21
    5c 54 a6 a1 88 aa 02 07 04 00 12 e2 98 a5 ce 04
    03 02 00 00 06 02 00 78 00 00'''
    err = FvRegress.OFVERSION + '''01 00 46 00 00 00 00 00 02 00 06 01 0d 00 3a
    00 00 00 00 ff ff ff ff ff fd 00 08 00 00 00 08
    00 00 00 00 f1 23 20 00 00 01 00 21 5c 54 a6 a1
    88 aa 02 07 04 00 12 e2 98 a5 ce 04 03 02 00 00
    06 02 00 78 00 00'''
    h.runTest(name="bad packet_out",timeout=timeout,  events= [
            TestEvent( "send","guest","alice", bad_packet_out),
            TestEvent( "recv","guest","alice", err),
            ])

#########################################
# more tests for this setup HERE
#################################### End Tests
finally:
    if wantPause:
        doPause("start cleanup")
    h.cleanup()

