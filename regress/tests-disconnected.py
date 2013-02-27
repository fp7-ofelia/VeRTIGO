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

    if len(sys.argv) > 1 :
        wantPause = False
        port=int(sys.argv[1])
        timeout=60
        h.useAlreadyRunningFlowVisor(port)
    else:
        wantPause = False
        timeout=5
        h.spawnFlowVisor(configFile="tests-disconnected.xml")
    h.lamePause()
    h.addSwitch(name='switch1',port=port)
    h.addSwitch(name='switch2',port=port)


    if wantPause:
        doPause("start tests")
#################################### Start Tests
    feature_request =     FvRegress.OFVERSION + '05 0008 2d47 c5eb'
    feature_request_after = FvRegress.OFVERSION + '05 0008 0000 0101'
    h.runTest(name="feature_request",timeout=timeout,  events= [
            # send features_request
            TestEvent( "send","guest",'alice', feature_request),
            # make sure the XID is updated
            TestEvent( "recv","switch",'switch1', feature_request_after, strict=True),
            ])

    ############################################################
    feature_reply  =     FvRegress.OFVERSION + '''06 00e0 0000 0101 0000 76a9
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
    feature_reply_after = FvRegress.OFVERSION + '''06 00 b0 2d 47 c5 eb 00 00 76 a9 d4 0d 25 48
        00 00 01 00 02 00 00 00 00 00 00 1f 00 00 03 ff
        00 00 1a c1 51 ff ef 8a 76 65 74 68 31 00 00 00
        00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
        00 00 00 c0 00 00 00 00 00 00 00 00 00 00 00 00
        00 02 ca 8a 1e f3 77 ef 76 65 74 68 35 00 00 00
        00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
        00 00 00 c0 00 00 00 00 00 00 00 00 00 00 00 00
        00 03 fa bc 77 8d 7e 0b 76 65 74 68 37 00 00 00
        00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
        00 00 00 c0 00 00 00 00 00 00 00 00 00 00 00 00'''



    h.runTest(name="feature_reply", timeout=timeout, events= [
            # send features_reply (with xid from request as above)
            TestEvent( "send","switch",'switch1', feature_reply),
            # make sure the reply has pruned ports
            TestEvent( "recv","guest",'alice', feature_reply_after),
            ])
    ############################################################
    packet_to_g0_p0 =  FvRegress.OFVERSION + '''0a 0052 0000 0000 0000 0101
                0040 0000 0000 0000 0000 0001 0000 0000
                0002 0800 4500 0032 0000 0000 40ff f72c
                c0a8 0028 c0a8 0128 7a18 586b 1108 97f5
                19e2 657e 07cc 31c3 11c7 c40c 8b95 5151
                3354 51d5 0036'''
    packet_to_g1_p0 =  FvRegress.OFVERSION + '''0a 0052 0000 0001 0000 0101
                0040 0001 0000 0000 0000 0002 0000 0000
                0001 0800 4500 0032 0000 0000 40ff f72c
                c0a8 0028 c0a8 0128 7a18 586b 1108 97f5
                19e2 657e 07cc 31c3 11c7 c40c 8b95 5151
                3354 51d5 0036'''
    drop_rule = FvRegress.OFVERSION + '''0e 00 48 00 00 00 00 00 00 00 00 00 01 00 00
                00 00 00 01 00 00 00 00 00 02 ff ff 00 00 08 00
                00 ff 00 00 c0 a8 00 28 c0 a8 01 28 00 00 00 00
                00 00 00 00 00 00 00 00 00 00 00 01 00 00 00 00
                00 00 00 00 00 00 00 01'''
    h.runTest(name="packet_in drop rule", timeout=timeout, events= [
            TestEvent( "send","switch",'switch1', packet_to_g0_p0),
            TestEvent( "recv","guest",'alice', packet_to_g0_p0),
            TestEvent( "send","switch",'switch1', packet_to_g1_p0),
            TestEvent( "recv","switch",'switch1', drop_rule),
            ])
################################################################
    probe = '''01 23 20 00 00 01 00 12 e2 b8 dc 4c 88 cc 02 07
                04 e2 b8 dc 3b 17 95 04 03 02 00 01 06 02 00 78
                00 00'''
    lldp=  FvRegress.OFVERSION + \
                    '''0a 00 34 00 00 00 00 00 00 01 01 00 22 00 03
                    00 00''' + probe
    # given an LLDP to no one (i.e., no trailer) then should go to alice but 
    #   not bob (bob is disconnected
    h.runTest(name="lldp disconnected", timeout=timeout, events= [
            TestEvent( "send","switch",'switch1', lldp),
            TestEvent( "recv","guest",'alice', lldp),
            TestEvent( "clear?","guest", 'alice',packet=None),
            TestEvent( "clear?","switch", 'switch1',packet=None),
            ])
#########################################
# more tests for this setup HERE
#################################### End Tests
finally:
    if wantPause:
        doPause("start cleanup")
    h.cleanup()

