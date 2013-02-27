#!/usr/bin/python
from fvregress import *
import string     # really?  you have to do this?
import sys

wantPause = True

#################################### Start Tests
try:
    h = FvRegress()
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
        h.spawnFlowVisor(configFile="tests-flood.xml")
    h.lamePause()
    h.addSwitch(name='switch1',port=port)

    if wantPause:
        doPause("start tests")

    ############################################################
    feature_request =      FvRegress.OFVERSION + '05 0008 2d47 c5eb'
    feature_request_after =  FvRegress.OFVERSION + '05 0008 0000 0102'
    h.runTest(name="feature_request",timeout=timeout,  events= [
            TestEvent( "send","guest","alice", feature_request),
            TestEvent( "recv","switch","switch1", feature_request_after,strict=True),
            ])

    ############################################################
    feature_reply  =     FvRegress.OFVERSION + '''06 00e0 0000 0102 0000 76a9
                d40d 2548 0000 0100 0200 0000 0000 001f
                0000 03ff 0000 1ac1 51ff ef8a 7665 7468
                3100 0000 0000 0000 0000 0000 0000 0000
                0000 0000 0000 00c0 0000 0000 0000 0000
                0000 0000 0001 ce2f a287 f670 7665 7468
                3300 0000 0000 0000 0000 0000 0000 0000
                0000 0000 0000 00c0 0000 0000 0000 0000
                0000 0000 0002 ca8a 1ef3 77ef 7665 7468
                3500 0000 0000 0000 0000 0000 0000 0000
                0000 0000 0000 00c0 0000 0000 0000 0000
                0000 0000 0003 fabc 778d 7e0b 7665 7468
                3700 0000 0000 0000 0000 0000 0000 0000
                0000 0000 0000 00c0 0000 0000 0000 0000
                0000 0000'''
    # this reply should strip the STP bit, and trim the ports down to the allowable set
    feature_reply_after =FvRegress.OFVERSION + '''06 00b0 2d47 c5eb 0000 76a9 d40d 2548
                0000 0100 0200 0000 0000 001f 0000 03ff
                0000 1ac1 51ff ef8a 7665 7468 3100 0000
                0000 0000 0000 0000 0000 0000 0000 0000
                0000 00c0 0000 0000 0000 0000 0000 0000
                0002 ca8a 1ef3 77ef 7665 7468 3500 0000
                0000 0000 0000 0000 0000 0000 0000 0000
                0000 00c0 0000 0000 0000 0000 0000 0000
                0003fabc778d7e0b766574683700000000000000000000000000000000000000000000c0000000000000000000000000'''

    h.runTest(name="feature_reply", timeout=timeout, events= [
            TestEvent( "send","switch","switch1", feature_reply),
            TestEvent( "recv","guest","alice", feature_reply_after),
            ])
    ####################################################################################

    packet_out_flood = FvRegress.OFVERSION + '''0d 0058 0000 abcd ffff ffff
                ffff 0008 0000 0008 fffb 0080 0000 0000
                0001 0000 0000 0002 0800 4500 0032 0000
                4000 4011 2868 c0a8 c800 c0a8 c901 0001
                0000 001e d7c3 cdc0 251b e6dc ea0c 726d
                973f 2b71 c2e4 1b6f bc11 8250'''
    packet_out_flood_aftr = FvRegress.OFVERSION + '''0d 00 68 09 01 00 00 ff ff ff ff ff ff 00 18
                00 00 00 08 00 00 00 80 00 00 00 08 00 02 00 80
                00 00 00 08 00 03 00 80 00 00 00 00 00 01 00 00
                00 00 00 02 08 00 45 00 00 32 00 00 40 00 40 11
                28 68 c0 a8 c8 00 c0 a8 c9 01 00 01 00 00 00 1e
                d7 c3 cd c0 25 1b e6 dc ea 0c 72 6d 97 3f 2b 71
                c2 e4 1b 6f bc 11 82 50'''
    packet_out2_flood = FvRegress.OFVERSION + '''0d 0058 0000 abcd ffff ffff
                ffff 0008 0000 0008 fffb 0080 0000 0000
                0002 0000 0000 0001 0800 4500 0032 0000
                4000 4011 2868 c0a8 c800 c0a8 c901 0001
                0000 001e d7c3 cdc0 251b e6dc ea0c 726d
                973f 2b71 c2e4 1b6f bc11 8250'''
    packet_out2_flood_aftr = packet_out2_flood

    h.runTest(name="packet_out native flood for bob", timeout=timeout, events= [
            TestEvent( "send","guest",'alice', packet_out_flood),
            TestEvent( "recv","switch",'switch1', packet_out_flood_aftr),
            TestEvent( "send","guest",'bob', packet_out2_flood),
            TestEvent( "recv","switch",'switch1', packet_out2_flood_aftr),
            ])




#########################################
# more tests for this setup HERE
#################################### End Tests
finally:
    if wantPause:
        doPause("start cleanup")
    h.cleanup()

