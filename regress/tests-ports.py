#!/usr/bin/python
from fvregress import *
import string     # really?  you have to do this?
import xmlrpclib

wantPause = True

test="ports"
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
        h.spawnFlowVisor(configFile="tests-"+test+".xml")
    h.lamePause()


    if wantPause:
        doPause("start tests")


# start up a flowvisor with 1 switch and two guests

    switch_features= FvRegress.OFVERSION + '''06 00 e0 ef be ad de 00 00 00 00 00 00 00 02
                        00 00 00 80 02 00 00 00 00 00 00 00 00 00 00 00
                        00 99 32 30 00 00 00 00 70 6f 72 74 20 31 35 33
                        00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
                        00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
                        00 01 32 31 30 30 30 30 70 6f 72 74 20 31 00 00
                        00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
                        00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
                        00 02 32 32 30 30 30 30 70 6f 72 74 20 32 00 00
                        00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
                        00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
                        00 03 32 33 30 30 30 30 70 6f 72 74 20 33 00 00
                        00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
                        00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00'''

    if wantPause:
        doPause("before adding switch1")
    h.addSwitch(name='switch1',dpid=2, switch_features=switch_features)    


    if wantPause:
        doPause("start tests")
#################################### Start Tests
#    # send a packet in from a port that was not listed in switch_features
#    packet_to_g0_p0 =  FvRegress.OFVERSION + '''0a 0052 0000 0000 0000 0101
#                0040 0000 0000 0000 0000 0001 0000 0000
#                0002 0800 4500 0032 0000 0000 40ff f72c
#                c0a8 0028 c0a8 0128 7a18 586b 1108 97f5
#                19e2 657e 07cc 31c3 11c7 c40c 8b95 5151
#                3354 51d5 0036'''
#    # should dynamically grow for alice
#    packet_to_g0_p0 =  FvRegress.OFVERSION + '''0a 0052 0000 0001 0000 0101
#                0040 0001 0000 0000 0000 0002 0000 0000
#                0001 0800 4500 0032 0000 0000 40ff f72c
#                c0a8 0028 c0a8 0128 7a18 586b 1108 97f5
#                19e2 657e 07cc 31c3 11c7 c40c 8b95 5151
#                3354 51d5 0036'''
#    # should not be sent to bob (explicitly lists ports)
#    h.runTest(name="switch1controller packet_in routing - by port", timeout=timeout, events= [
#            TestEvent( "send","switch","switch1", packet=packet_to_g0_p0),
#            TestEvent( "recv","guest","alice", packet=packet_to_g0_p0),
#            TestEvent( "clear?","guest","bob", packet=""),
#            ])
    ############################################################
    # test port stats pruning

    port_stats_request_before = FvRegress.OFVERSION + \
        '''10 00 14 00 00 03 cf 00 04 00 00 ff ff 00 00
        00 00 00 00'''
    port_stats_request_after_alice = FvRegress.OFVERSION + \
        '''10 00 14 00 00 01 02 00 04 00 00 ff ff 00 00
        00 00 00 00'''
    port_stats_request_after_bob = FvRegress.OFVERSION + \
        '''10 00 14 00 00 01 03 00 04 00 00 ff ff 00 00
        00 00 00 00'''
    port_stats_reply  = FvRegress.OFVERSION + \
        '''11 01 ac 00 00 01 02 00 04 00 00 00 01 00 00
        00 00 00 00 00 00 00 00 00 8a 8c 55 00 00 00 00
        00 1b fb 14 00 00 00 02 0b 7a 82 ae 00 00 00 00
        0e 2b be 02 00 00 00 00 00 00 00 00 00 00 00 00
        00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
        00 00 00 00 ff ff ff ff ff ff ff ff ff ff ff ff
        ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff
        ff ff ff ff 00 03 00 00 00 00 00 00 00 00 00 00
        00 86 f2 d5 00 00 00 00 00 75 9e e7 00 00 00 00
        2b cf 69 de 00 00 00 01 ed 71 df 6c 00 00 00 00
        00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
        00 00 00 00 00 00 00 00 00 00 00 00 ff ff ff ff
        ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff
        ff ff ff ff ff ff ff ff ff ff ff ff 00 2d 00 00
        00 00 00 00 00 00 00 00 00 26 9c 4e 00 00 00 00
        00 1d 95 ea 00 00 00 00 1e 5f 61 6f 00 00 00 00
        31 65 e3 b5 00 00 00 00 00 00 00 00 00 00 00 00
        00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
        00 00 00 00 ff ff ff ff ff ff ff ff ff ff ff ff
        ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff
        ff ff ff ff ff fe 00 00 00 00 00 00 ff ff ff ff
        ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff
        ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff
        ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff
        ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff
        ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff
        ff ff ff ff ff ff ff ff ff ff ff ff'''
    port_stats_reply2  = FvRegress.OFVERSION + \
        '''11 01 ac 00 00 01 03 00 04 00 00 00 01 00 00
        00 00 00 00 00 00 00 00 00 8a 8c 55 00 00 00 00
        00 1b fb 14 00 00 00 02 0b 7a 82 ae 00 00 00 00
        0e 2b be 02 00 00 00 00 00 00 00 00 00 00 00 00
        00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
        00 00 00 00 ff ff ff ff ff ff ff ff ff ff ff ff
        ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff
        ff ff ff ff 00 03 00 00 00 00 00 00 00 00 00 00
        00 86 f2 d5 00 00 00 00 00 75 9e e7 00 00 00 00
        2b cf 69 de 00 00 00 01 ed 71 df 6c 00 00 00 00
        00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
        00 00 00 00 00 00 00 00 00 00 00 00 ff ff ff ff
        ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff
        ff ff ff ff ff ff ff ff ff ff ff ff 00 2d 00 00
        00 00 00 00 00 00 00 00 00 26 9c 4e 00 00 00 00
        00 1d 95 ea 00 00 00 00 1e 5f 61 6f 00 00 00 00
        31 65 e3 b5 00 00 00 00 00 00 00 00 00 00 00 00
        00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
        00 00 00 00 ff ff ff ff ff ff ff ff ff ff ff ff
        ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff
        ff ff ff ff ff fe 00 00 00 00 00 00 ff ff ff ff
        ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff
        ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff
        ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff
        ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff
        ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff
        ff ff ff ff ff ff ff ff ff ff ff ff'''
    port_stats_reply_alice  = FvRegress.OFVERSION + \
        '''11 01 ac 00 00 03 d1 00 04 00 00 00 01 00 00
        00 00 00 00 00 00 00 00 00 8a 8c 55 00 00 00 00
        00 1b fb 14 00 00 00 02 0b 7a 82 ae 00 00 00 00
        0e 2b be 02 00 00 00 00 00 00 00 00 00 00 00 00
        00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
        00 00 00 00 ff ff ff ff ff ff ff ff ff ff ff ff
        ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff
        ff ff ff ff 00 03 00 00 00 00 00 00 00 00 00 00
        00 86 f2 d5 00 00 00 00 00 75 9e e7 00 00 00 00
        2b cf 69 de 00 00 00 01 ed 71 df 6c 00 00 00 00
        00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
        00 00 00 00 00 00 00 00 00 00 00 00 ff ff ff ff
        ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff
        ff ff ff ff ff ff ff ff ff ff ff ff 00 2d 00 00
        00 00 00 00 00 00 00 00 00 26 9c 4e 00 00 00 00
        00 1d 95 ea 00 00 00 00 1e 5f 61 6f 00 00 00 00
        31 65 e3 b5 00 00 00 00 00 00 00 00 00 00 00 00
        00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
        00 00 00 00 ff ff ff ff ff ff ff ff ff ff ff ff
        ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff
        ff ff ff ff ff fe 00 00 00 00 00 00 ff ff ff ff
        ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff
        ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff
        ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff
        ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff
        ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff
        ff ff ff ff ff ff ff ff ff ff ff ff'''
    port_stats_reply_bob  = FvRegress.OFVERSION + \
        '''11 00 dc 00 00 03 cf 00 04 00 00 00 01 00 00
        00 00 00 00 00 00 00 00 00 8a 8c 55 00 00 00 00
        00 1b fb 14 00 00 00 02 0b 7a 82 ae 00 00 00 00
        0e 2b be 02 00 00 00 00 00 00 00 00 00 00 00 00
        00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
        00 00 00 00 ff ff ff ff ff ff ff ff ff ff ff ff
        ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff
        ff ff ff ff 00 03 00 00 00 00 00 00 00 00 00 00
        00 86 f2 d5 00 00 00 00 00 75 9e e7 00 00 00 00
        2b cf 69 de 00 00 00 01 ed 71 df 6c 00 00 00 00
        00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
        00 00 00 00 00 00 00 00 00 00 00 00 ff ff ff ff
        ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff ff
        ff ff ff ff ff ff ff ff ff ff ff ff'''


    h.runTest(name="port_stats pruning", timeout=timeout, events= [
            # alice sends a port_stats request
            TestEvent( "send","guest","alice", packet=port_stats_request_before),
            # fv changes the XID
            TestEvent( "recv","switch","switch1", packet=port_stats_request_after_alice, strict=True),
            TestEvent( "send","switch","switch1", packet=port_stats_reply,strict=True),
            TestEvent( "recv","guest","alice",packet=port_stats_reply_alice),

            # bob sends a port_stats request
            TestEvent( "send","guest","bob", packet=port_stats_request_before),
            # fv changes the XID
            TestEvent( "recv","switch","switch1", packet=port_stats_request_after_bob, strict=True),
            TestEvent( "send","switch","switch1", packet=port_stats_reply2,strict=True),
            TestEvent( "recv","guest","bob",packet=port_stats_reply_bob)

            ])



    ############################################################
    packet_out_pAll = FvRegress.OFVERSION + '''0d 0058 0000 abcd ffff ffff
            ffff 0008 0000 0008 fffb 0080 0000 0000
            0001 0000 0000 0002 0800 4500 0032 0000
            4000 4011 2868 c0a8 c800 c0a8 c901 0001
            0000 001e d7c3 cdc0 251b e6dc ea0c 726d
            973f 2b71 c2e4 1b6f bc11 8250'''
    # note, the xid here is a function of the order of the tests;
    #   DO NOT CHANGE test order
    packet_out_p0_aftr_port0 = FvRegress.OFVERSION+ '''0d 00 70 02 01 00 00 ff ff ff ff ff ff 00 20
            00 00 00 08 00 99 00 80 00 00 00 08 00 01 00 80
            00 00 00 08 00 02 00 80 00 00 00 08 00 03 00 80
            00 00 00 00 00 01 00 00 00 00 00 02 08 00 45 00
            00 32 00 00 40 00 40 11 28 68 c0 a8 c8 00 c0 a8
            c9 01 00 01 00 00 00 1e d7 c3 cd c0 25 1b e6 dc
            ea 0c 72 6d 97 3f 2b 71 c2 e4 1b 6f bc 11 82 50'''
    packet_out_pAll_bob = FvRegress.OFVERSION + '''0d 0058 0000 abcd ffff ffff
            ffff 0008 0000 0008 fffb 0080 0000 0000
            0002 0000 0000 0001 0800 4500 0032 0000
            4000 4011 2868 c0a8 c800 c0a8 c901 0001
            0000 001e d7c3 cdc0 251b e6dc ea0c 726d
            973f 2b71 c2e4 1b6f bc11 8250'''
    packet_out_pAll_bob_aftr = FvRegress.OFVERSION+ '''0d 00 60 00 00 ab cd ff ff ff ff ff ff 00 10
            00 00 00 08 00 01 00 80 00 00 00 08 00 03 00 80
            00 00 00 00 00 02 00 00 00 00 00 01 08 00 45 00
            00 32 00 00 40 00 40 11 28 68 c0 a8 c8 00 c0 a8
            c9 01 00 01 00 00 00 1e d7 c3 cd c0 25 1b e6 dc
            ea 0c 72 6d 97 3f 2b 71 c2 e4 1b 6f bc 11 82 50'''
    h.runTest(name="packet_out; valid", timeout=timeout, events= [
            # alice sends a FLOOD packet_out; alice has access to all ports
            TestEvent( "send","guest","alice", packet=packet_out_pAll),
            # fv expands it to ports=1,153,2,3 (yes, 153... to ensure there aren't huge jumps)
            TestEvent( "recv","switch","switch1", packet=packet_out_p0_aftr_port0),
            # bob sends a FLOOD packet_out
            TestEvent( "send","guest","bob", packet=packet_out_pAll_bob),
            # fv expands it to ports=1,3
            TestEvent( "recv","switch","switch1", packet=packet_out_pAll_bob_aftr),
            ])


    ############################################################
    packet_out_pInPort = FvRegress.OFVERSION + '''0d 0058 0000 abff ffff ffff
            0001 0008 0000 0008 fffb 0080 0000 0000
            0001 0000 0000 0002 0800 4500 0032 0000
            4000 4011 2868 c0a8 c800 c0a8 c901 0001
            0000 001e d7c3 cdc0 251b e6dc ea0c 726d
            973f 2b71 c2e4 1b6f bc11 8250'''
    packet_out_pInPort_aftr = FvRegress.OFVERSION+ '''0d 00 68 04 01 00 00 ff ff ff ff 00 01 00 18
            00 00 00 08 00 99 00 80 00 00 00 08 00 02 00 80
            00 00 00 08 00 03 00 80 00 00 00 00 00 01 00 00
            00 00 00 02 08 00 45 00 00 32 00 00 40 00 40 11
            28 68 c0 a8 c8 00 c0 a8 c9 01 00 01 00 00 00 1e
            d7 c3 cd c0 25 1b e6 dc ea 0c 72 6d 97 3f 2b 71
            c2 e4 1b 6f bc 11 82 50'''
    h.runTest(name="dont flood in_port; valid", timeout=timeout, events= [
            TestEvent( "send","guest","alice", packet=packet_out_pInPort),
            TestEvent( "recv","switch","switch1", packet=packet_out_pInPort_aftr),
            ])
    ############################################################
    # dynamically add port 13, emulating HP's dyn port stuff: bug #184
    port_mod_add_13 = FvRegress.OFVERSION + '''0c 0040 0000 dead
            00
            0000 0000 0000 00

            000d
            0102 0304 0506
            8080 8080 2049 4100 0000 0000 0000 0000
            0000 0000
            0000 0000
            0000 0000
            0000 0000
            0000 0000
            0000 0000
            '''
    packet_out_pAll = FvRegress.OFVERSION + '''0d 0058 0000 abcd ffff ffff
            ffff 0008 0000 0008 fffb 0080 0000 0000
            0001 0000 0000 0002 0800 4500 0032 0000
            4000 4011 2868 c0a8 c800 c0a8 c901 0001
            0000 001e d7c3 cdc0 251b e6dc ea0c 726d
            973f 2b71 c2e4 1b6f bc11 8250'''
    # note, the xid here is a function of the order of the tests;
    #   DO NOT CHANGE test order
    packet_out_p0_aftr_port0 = FvRegress.OFVERSION+ '''0d 00 78 02 01 00 00 ff ff ff ff ff ff 00 28
            00 00 00 08 00 99 00 80 00 00 00 08 00 01 00 80
            00 00 00 08 00 02 00 80 00 00 00 08 00 03 00 80
            00 00 00 08 00 0d 00 80
            00 00 00 00 00 01 00 00 00 00 00 02 08 00 45 00
            00 32 00 00 40 00 40 11 28 68 c0 a8 c8 00 c0 a8
            c9 01 00 01 00 00 00 1e d7 c3 cd c0 25 1b e6 dc
            ea 0c 72 6d 97 3f 2b 71 c2 e4 1b 6f bc 11 82 50'''
    packet_out_pAll_bob = FvRegress.OFVERSION + '''0d 0058 0000 abcd ffff ffff
            ffff 0008 0000 0008 fffb 0080 0000 0000
            0002 0000 0000 0001 0800 4500 0032 0000
            4000 4011 2868 c0a8 c800 c0a8 c901 0001
            0000 001e d7c3 cdc0 251b e6dc ea0c 726d
            973f 2b71 c2e4 1b6f bc11 8250'''
    packet_out_pAll_bob_aftr = FvRegress.OFVERSION+ '''0d 00 60 00 00 ab cd ff ff ff ff ff ff 00 10
            00 00 00 08 00 01 00 80 00 00 00 08 00 03 00 80
            00 00 00 00 00 02 00 00 00 00 00 01 08 00 45 00
            00 32 00 00 40 00 40 11 28 68 c0 a8 c8 00 c0 a8
            c9 01 00 01 00 00 00 1e d7 c3 cd c0 25 1b e6 dc
            ea 0c 72 6d 97 3f 2b 71 c2 e4 1b 6f bc 11 82 50'''
    h.runTest(name="packet_out; flood to dynamic port", timeout=timeout, events= [
            # announce that port 13 has been added
            TestEvent( "send","switch","switch1", packet=port_mod_add_13),
            TestEvent( "delay","switch","switch1",packet=None),
            TestEvent( "send" ,"guest","alice", packet=packet_out_pAll),
            # fv expands it to ports=1,153,2,3,13  -- 13 is new, relative to prev test
            TestEvent( "recv","switch","switch1", packet=packet_out_p0_aftr_port0),
            # bob sends a FLOOD packet_out
            TestEvent( "send","guest","bob", packet=packet_out_pAll_bob),
            # fv expands it to ports=1,3        # bob should not change
            TestEvent( "recv","switch","switch1", packet=packet_out_pAll_bob_aftr),
            ])

    #########################################
    rpcport=18080
    user="fvadmin"
    passwd="0fw0rk"
    s = xmlrpclib.ServerProxy("https://" + user + ":" + passwd + "@localhost:" + str(rpcport) + "/xmlrpc")
    change = { "operation" : "REMOVE", "id" : "3"}
    print "Sleeping 1 sec to let change propagate"
    time.sleep(1)
    ### now remove access from Bob on port 3
    if not s.api.changeFlowSpace([change]) :
        raise "FAILED: FlowSpace Change failed!"
    else :
        print "SUCCESS: FLowSpace Changed: removed bob's port 3"

    bob_without_port3 = FvRegress.OFVERSION + '''0d 00 58 00 00 ab cd ff ff ff ff ff ff 00 08
            00 00 00 08 00 01 00 80 00 00 00 00 00 02 00 00
            00 00 00 01 08 00 45 00 00 32 00 00 40 00 40 11
            28 68 c0 a8 c8 00 c0 a8 c9 01 00 01 00 00 00 1e
            d7 c3 cd c0 25 1b e6 dc ea 0c 72 6d 97 3f 2b 71
            c2 e4 1b 6f bc 11 82 50'''

    h.runTest(name="packet_out; without port3", timeout=timeout, events= [
            # bob sends a FLOOD packet_out
            TestEvent( "send","guest","bob", packet=packet_out_pAll_bob),
            # fv expands it to just ports=1 (even though it's the same input as before)
            TestEvent( "recv","switch","switch1", packet=bob_without_port3),
            ])

    #########################################
    change = { "operation" : "ADD", "priority" : "200", 
            "dpid":"all", "match":"in_port=2,dl_src=00:00:00:00:00:00:00:01", "actions":"Slice=bob:4"}
    ### now add access for Bob on port 2
    if not s.api.changeFlowSpace([change]) :
        raise "FAILED: FlowSpace Change failed!"
    else :
        print "SUCCESS: FLowSpace Changed: added bob's port 2"

    bob_with_port2 = FvRegress.OFVERSION + '''0d 00 60 00 00 ab cd ff ff ff ff ff ff 00 10
            00 00 00 08 00 01 00 80 00 00 00 08 00 02 00 80
            00 00 00 00 00 02 00 00 00 00 00 01 08 00 45 00
            00 32 00 00 40 00 40 11 28 68 c0 a8 c8 00 c0 a8
            c9 01 00 01 00 00 00 1e d7 c3 cd c0 25 1b e6 dc
            ea 0c 72 6d 97 3f 2b 71 c2 e4 1b 6f bc 11 82 50'''

    h.runTest(name="packet_out; with port2", timeout=timeout, events= [
            # bob sends a FLOOD packet_out
            TestEvent( "send","guest","bob", packet=packet_out_pAll_bob),
            # fv expands it to just ports=1 (even though it's the same input as before)
            TestEvent( "recv","switch","switch1", packet=bob_with_port2),
            ])
    #########################################
    change = { "operation" : "REMOVE", "id" : "5" }
    ### now remove access from all Alice's ports
    if not s.api.changeFlowSpace([change]) :
        raise "FAILED: FlowSpace Change failed!"
    else :
        print "SUCCESS: FLowSpace Changed: removed Alice's access"
    h.lamePause("Sleeping to let FV and test suite drop switch", 0.5)
    h.runTest(name="dropped Alice", timeout=timeout, events= [
            # Make sure Alice has no switches connected to her
            TestEvent( "countSwitches","guest","alice", actorID2=0,packet=None),
            ])


#########################################
# more tests for this setup HERE
#################################### End Tests
finally:
    if wantPause:
        doPause("start cleanup")
    h.cleanup()

