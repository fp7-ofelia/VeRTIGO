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
    h.addController("ncast",    54321)
    h.addController("prod",      54322)

    if len(sys.argv) > 1 :
        wantPause = False
        port=int(sys.argv[1])
        timeout=60
        h.useAlreadyRunningFlowVisor(port)
    else:
        wantPause = False
        timeout=5
        h.spawnFlowVisor(configFile="tests-kk-topo.xml")
    h.lamePause()
    h.addSwitch(name='switch1',port=port)


    if wantPause:
        doPause("start tests")
    ################################################################
    lldp_out = FvRegress.OFVERSION + '''0d 00 3a 00 00 00 00 ff ff ff ff ff fd 00 08
                00 00 00 08 00 01 00 00 01 23 20 00 00 01 00 12
                e2 b8 dc 4c 88 cc 02 07 04 e2 b8 dc 3b 17 95 04
                03 02 00 01 06 02 00 78 00 00'''
    lldp_out_after = FvRegress.OFVERSION + '''0d 00 5d 00 00 00 00 ff ff ff ff ff fd 00 08
                00 00 00 08 00 01 00 00 01 23 20 00 00 01 00 12
                e2 b8 dc 4c 88 cc 02 07 04 e2 b8 dc 3b 17 95 04
                03 02 00 01 06 02 00 78 00 00 02 23 07 70 72 6f
                64 00 20 20 20 20 6d 61 67 69 63 20 66 6c 6f 77
                76 69 73 6f 72 31 00 05 15 de ad ca fe'''
    h.runTest(name="lldp hack", timeout=timeout, events= [
            TestEvent( "send","guest",'prod', lldp_out),
            TestEvent( "recv","switch",'switch1', lldp_out_after),
            ])
    ################################################################

    # 34 bytes
    probe = '''01 23 20 00 00 01 00 12 e2 b8 dc 4c 88 cc 02 07
               04 e2 b8 dc 3b 17 95 04 03 02 00 01 06 02 00 78
               00 00'''
    # 35 bytes
    trailer_prod = \
            '''02 23 07 70 72 6f 64 00 20 20 20 20 6d 61 67 69 
               63 20 66 6c 6f 77 76 69 73 6f 72 31 00 05 15 de 
               ad ca fe'''
    # 36 bytes
    trailer_ncast =  \
            ''' 02 24 07 6e 63 61 73 74 00 20 20 20 20 6d 61 67 
                69 63 20 66 6c 6f 77 76 69 73 6f 72 31 00 06 15 
                de ad ca fe'''
    # 24 + probe
    lldp_out =     FvRegress.OFVERSION + '''0d 003a 0000 abcd ffff ffff
                ffff 0008 0000 0008 0001 0080''' + probe
    # 24 + probe + trailer = 93 0x5d
    lldp_out_after_prod = FvRegress.OFVERSION + \
                '''0d 00 5d 00 00 ab cd ff ff ff ff ff ff 00 08
                00 00 00 08 00 01 00 80''' + probe + trailer_prod
    # 24 + probe + trailer = 94 0x5e
    lldp_out_after_ncast = FvRegress.OFVERSION + \
                '''0d 00 5e 00 00 ab cd ff ff ff ff ff ff 00 08
                00 00 00 08 00 01 00 80''' + probe + trailer_ncast
    # 18 + probe + trailer = 87 0x57
    lldp_in_prod =      FvRegress.OFVERSION + \
                '''0a 00 57 00 00 00 00 00 00 01 01 00 45 00 03 
                00 00''' + probe + trailer_prod
    # 18 + probe + trailer = 88 0x59
    lldp_in_ncast =      FvRegress.OFVERSION + \
                '''0a 00 58 00 00 00 00 00 00 01 01 00 46 00 03 
                00 00''' + probe + trailer_ncast 
    # 18 + probe = 52 0x34                ; 
    lldp_in_after =  FvRegress.OFVERSION + \
                '''0a 00 34 00 00 00 00 00 00 01 01 00 22 00 03
                00 00''' + probe
    h.runTest(name="lldp hack", timeout=timeout, events= [
            TestEvent( "send","guest",'prod', lldp_out),
            TestEvent( "recv","switch",'switch1', lldp_out_after_prod),
            TestEvent( "send","guest",'ncast', lldp_out),
            TestEvent( "recv","switch",'switch1', lldp_out_after_ncast),
            TestEvent( "send","switch",'switch1', lldp_in_prod),
            TestEvent( "recv","guest",'prod', lldp_in_after),
            TestEvent( "send","switch",'switch1', lldp_in_ncast),
            TestEvent( "recv","guest",'ncast', lldp_in_after),
            ])
#########################################
    arp_in = FvRegress.OFVERSION + '''0a 00 4e 00 00 00 00 00 10 2e fc 00 3c 00 01
                    00 00 ff ff ff ff ff ff 00 23 ae 35 fd f3 08 06
                    00 01 08 00 06 04 00 01 00 23 ae 35 fd f3 0a 4f
                    01 69 00 00 00 00 00 00 0a 4f 01 9f 00 00 00 00
                    00 00 00 00 00 00 00 00 00 00 00 00 00 00'''
    h.runTest(name="kk's arp", timeout=timeout, events= [
            TestEvent( "send","switch",'switch1', arp_in),
            TestEvent( "recv","guest",'prod', arp_in),
            ])
#########################################
# multi-layer lldp
    # 24 + probe + trailer*2 = 128 0x80
    lldp_out_after_prod_prod = FvRegress.OFVERSION + \
                '''0d 00 80 00 00 ab cd ff ff ff ff ff ff 00 08
                00 00 00 08 00 01 00 80''' + probe + trailer_prod + trailer_prod
    # 18 + probe + trailer*2 = 81 0x7a
    lldp_in_prod_prod =      FvRegress.OFVERSION + \
                '''0a 00 7a 00 00 00 00 00 00 01 01 00 45 00 03 
                00 00''' + probe + trailer_prod + trailer_prod
    h.runTest(name="recursive lldp hack", timeout=timeout, events= [
            # prod sends lldp
            TestEvent( "send","guest",'prod', lldp_out),
            # switch recvs
            TestEvent( "recv","switch",'switch1', lldp_out_after_prod),
            # prod sends into switch again
            TestEvent( "send","guest",'prod', lldp_out_after_prod),
            # switch recvs again
            TestEvent( "recv","switch",'switch1', lldp_out_after_prod_prod),
            # lldp comes back up first FV
            TestEvent( "send","switch",'switch1', lldp_in_prod_prod),
            TestEvent( "recv","guest",'prod', lldp_in_prod),
            # lldp comes back up through 2nd FV
            TestEvent( "send","switch",'switch1', lldp_in_prod),
            TestEvent( "recv","guest",'prod', lldp_in_after),
            ])
    
#########################################
# more tests for this setup HERE
#################################### End Tests
finally:
    if wantPause:
        doPause("start cleanup")
    h.cleanup()

