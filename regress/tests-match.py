#!/usr/bin/python
from fvregress import *
import string 	# really?  you have to do this?

wantPause = True

#################################### Start Tests
try:
    h= FvRegress()
    port=16633
    h.addController("alice",     54321)
    h.addController("bob",       54322)
    h.addController("cathy",     54323)
    h.addController("doug",      54324)
    h.addController("eric",      54325)
    h.addController("frank",     54326)
    h.addController("george",    54327)
    h.addController("hank",      54328)
    h.addController("ingrid",    54329)

    if len(sys.argv) > 1 :
        wantPause = False
        port=int(sys.argv[1])
        timeout=60
        h.useAlreadyRunningFlowVisor(port)
    else:
        wantPause = False
        timeout=5
        h.spawnFlowVisor(configFile="tests-match.xml")

    h.lamePause(pause=1)
    h.addSwitch(name='switch1',port=port)
    h.lamePause(pause=1)

    feature_request = 	 FvRegress.OFVERSION + '05 0008 2d47 c5eb'
    feature_request_after =  FvRegress.OFVERSION + '05000800000109'
    h.runTest(name="feature_request",timeout=timeout,  events= [
    		TestEvent( "send","guest","alice", feature_request),
    		TestEvent( "recv","switch","switch1", feature_request_after,strict=True),
    		])

    ############################################################
    feature_reply  =     FvRegress.OFVERSION + '''06 00e0 0000 0109 0000 76a9
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
    # this changed find out why
    feature_reply_after =FvRegress.OFVERSION + '''0600e02d47c5eb000076a9d40d25480000010
    			0020000000000001f000003ff00001ac151ffef
    			8a7665746831000000000000000000000000000
    			00000000000000000c000000000000000000000
    			00000001ce2fa287f6707665746833000000000
    			00000000000000000000000000000000000c000
    			00000000000000000000000002ca8a1ef377ef7
    			665746835000000000000000000000000000000
    			00000000000000c000000000000000000000000
    			00003fabc778d7e0b7665746837000000000000
    			00000000000000000000000000000000c000000
    			0000000000000000000'''


    h.runTest(name="feature_reply", timeout=timeout, events= [
    		TestEvent( "send","switch","switch1", feature_reply),
    		TestEvent( "recv","guest","alice", feature_reply_after),
    		])

#####################################
# these test verify_match_by_packet()
# match ip_dst with prefix

    ping_in = FvRegress.OFVERSION + '''0a 00 74 00 00 00 00 00 00 35 db 00 62 00 01
    00 00 00 1c f0 ed 98 5a 00 22 41 fa 73 01 08 00
    45 00 00 54 f3 b9 00 00 40 01 00 97 09 09 09 09
    03 04 04 05 08 00 06 05 0d 07 00 37 d8 0c b1 49
    6b 63 05 00 08 09 0a 0b 0c 0d 0e 0f 10 11 12 13
    14 15 16 17 18 19 1a 1b 1c 1d 1e 1f 20 21 22 23
    24 25 26 27 28 29 2a 2b 2c 2d 2e 2f 30 31 32 33
    34 35 36 37'''
    h.runTest(name="ping by ip_dst/prefix to Frank",timeout=timeout,  events= [
    		TestEvent( "send","switch","switch1", ping_in),
    		TestEvent( "recv","guest","frank", ping_in),
    		])
#####################################
# these test verify_match_by_packet()
# match vlan_pcp 

    ping_in = FvRegress.OFVERSION + '''0a 00 78 00 00 00 00 00 00 35 db 00 62 00 01
    00 00 00 1c f0 ed 98 5a 00 22 41 fa 73 01 81 00 60 00 
    08 00
    45 00 00 54 f3 b9 00 00 40 01 00 97 10 09 09 09
    13 04 04 05 08 00 06 05 0d 07 00 37 d8 0c b1 49
    6b 63 05 00 08 09 0a 0b 0c 0d 0e 0f 10 11 12 13
    14 15 16 17 18 19 1a 1b 1c 1d 1e 1f 20 21 22 23
    24 25 26 27 28 29 2a 2b 2c 2d 2e 2f 30 31 32 33
    34 35 36 37'''
    h.runTest(name="ping by vlan_pcp to Hank",timeout=timeout,  events= [
    		TestEvent( "send","switch","switch1", ping_in),
    		TestEvent( "recv","guest","hank", ping_in),
    		])
#####################################
# these test verify_match_by_packet()
# match vlan_pcp 

    ping_in = FvRegress.OFVERSION + '''0a 00 78 00 00 00 00 00 00 35 db 00 62 00 01
    00 00 00 1c f0 ed 98 5a 00 22 41 fa 73 01 81 00 40 ff 
    08 00
    45 00 00 54 f3 b9 00 00 40 01 00 97 10 09 09 09
    13 04 04 05 08 00 06 05 0d 07 00 37 d8 0c b1 49
    6b 63 05 00 08 09 0a 0b 0c 0d 0e 0f 10 11 12 13
    14 15 16 17 18 19 1a 1b 1c 1d 1e 1f 20 21 22 23
    24 25 26 27 28 29 2a 2b 2c 2d 2e 2f 30 31 32 33
    34 35 36 37'''
    h.runTest(name="ping by vlan to Ingrid",timeout=timeout,  events= [
    		TestEvent( "send","switch","switch1", ping_in),
    		TestEvent( "recv","guest","ingrid", ping_in),
    		])

#####################################
# these test verify_match_by_packet()
# match ip_src

    ping_in = FvRegress.OFVERSION + '''0a 00 74 00 00 00 00 00 00 35 db 00 62 00 01
    00 00 00 1c f0 ed 98 5a 00 22 41 fa 73 01 08 00
    45 00 00 54 f3 b9 00 00 40 01 00 97 01 01 02 03
    09 09 09 09 08 00 06 05 0d 07 00 37 d8 0c b1 49
    6b 63 05 00 08 09 0a 0b 0c 0d 0e 0f 10 11 12 13
    14 15 16 17 18 19 1a 1b 1c 1d 1e 1f 20 21 22 23
    24 25 26 27 28 29 2a 2b 2c 2d 2e 2f 30 31 32 33
    34 35 36 37'''
    h.runTest(name="ping by ip_src to Alice",timeout=timeout,  events= [
    		TestEvent( "send","switch","switch1", ping_in),
    		TestEvent( "recv","guest","alice", ping_in),
    		])
#####################################
# match ip_dst

    ping_in = FvRegress.OFVERSION + '''0a 00 74 00 00 00 00 00 00 35 db 00 62 00 01
    00 00 00 1c f0 ed 98 5a 00 22 41 fa 73 01 08 00
    45 00 00 54 f3 b9 00 00 40 01 00 97 09 09 09 09
    02 02 03 04 08 00 06 05 0d 07 00 37 d8 0c b1 49
    6b 63 05 00 08 09 0a 0b 0c 0d 0e 0f 10 11 12 13
    14 15 16 17 18 19 1a 1b 1c 1d 1e 1f 20 21 22 23
    24 25 26 27 28 29 2a 2b 2c 2d 2e 2f 30 31 32 33
    34 35 36 37'''
    h.runTest(name="ping by ip_dst to Bob",timeout=timeout,  events= [
    		TestEvent( "send","switch","switch1", ping_in),
    		TestEvent( "recv","guest","bob", ping_in),
    		])
#####################################
# match ip_proto

    ping_in = FvRegress.OFVERSION + '''0a 00 74 00 00 00 00 00 00 35 db 00 62 00 01
    00 00 00 1c f0 ed 98 5a 00 22 41 fa 73 01 08 00
    45 00 00 54 f3 b9 00 00 40 03 00 97 03 03 03 03
    03 03 03 03 08 00 06 05 0d 07 00 37 d8 0c b1 49
    6b 63 05 00 08 09 0a 0b 0c 0d 0e 0f 10 11 12 13
    14 15 16 17 18 19 1a 1b 1c 1d 1e 1f 20 21 22 23
    24 25 26 27 28 29 2a 2b 2c 2d 2e 2f 30 31 32 33
    34 35 36 37'''
    h.runTest(name="packet_in by ip_proto to Cathy",timeout=timeout,  events= [
    		TestEvent( "send","switch","switch1", ping_in),
    		TestEvent( "recv","guest","cathy", ping_in),
    		])
#####################################
# match tp_src for icmp

    ping_in = FvRegress.OFVERSION + '''0a 00 74 00 00 00 00 00 00 35 db 00 62 00 01
    00 00 00 1c f0 ed 98 5a 00 22 41 fa 73 01 08 00
    45 00 00 54 f3 b9 00 00 40 01 00 97 03 03 03 03
    03 03 03 03 50 00 06 05 0d 07 00 37 d8 0c b1 49
    6b 63 05 00 08 09 0a 0b 0c 0d 0e 0f 10 11 12 13
    14 15 16 17 18 19 1a 1b 1c 1d 1e 1f 20 21 22 23
    24 25 26 27 28 29 2a 2b 2c 2d 2e 2f 30 31 32 33
    34 35 36 37'''
    h.runTest(name="icmp by tp_src to Doug",timeout=timeout,  events= [
    		TestEvent( "send","switch","switch1", ping_in),
    		TestEvent( "recv","guest","doug", ping_in),
    		])
#####################################
# match tp_dst for icmp

    ping_in = FvRegress.OFVERSION + '''0a 00 74 00 00 00 00 00 00 35 db 00 62 00 01
    00 00 00 1c f0 ed 98 5a 00 22 41 fa 73 01 08 00
    45 00 00 54 f3 b9 00 00 40 01 00 97 03 03 03 03
    03 03 03 03 00 5a 06 05 0d 07 00 37 d8 0c b1 49
    6b 63 05 00 08 09 0a 0b 0c 0d 0e 0f 10 11 12 13
    14 15 16 17 18 19 1a 1b 1c 1d 1e 1f 20 21 22 23
    24 25 26 27 28 29 2a 2b 2c 2d 2e 2f 30 31 32 33
    34 35 36 37'''
    h.runTest(name="icmp by tp_dst to Eric",timeout=timeout,  events= [
    		TestEvent( "send","switch","switch1", ping_in),
    		TestEvent( "recv","guest","eric", ping_in),
    		])

### note, moved frank first for testing

#####################################
# match tp_dst for icmp

    ping_in = FvRegress.OFVERSION + '''0a 00 74 00 00 00 00 00 00 35 db 00 62 00 01
    00 00 00 1c f0 ed 98 5a 00 22 41 fa 73 01 08 00
    45 30 00 54 f3 b9 00 00 40 01 00 97 00 00 00 00
    00 00 00 00 00 ff 06 05 0d 07 00 37 d8 0c b1 49
    6b 63 05 00 08 09 0a 0b 0c 0d 0e 0f 10 11 12 13
    14 15 16 17 18 19 1a 1b 1c 1d 1e 1f 20 21 22 23
    24 25 26 27 28 29 2a 2b 2c 2d 2e 2f 30 31 32 33
    34 35 36 37'''
    h.runTest(name="icmp by tp_tos to George",timeout=timeout,  events= [
    		TestEvent( "send","switch","switch1", ping_in),
    		TestEvent( "recv","guest","george", ping_in),
    		])

    h.runTest(name="all clear?",timeout=timeout,  events= [ TestEvent( "clear?","switch","switch1", 'nil'), ])
        
# match ip_dst
# match ip_src
# match dl_type
# match dl_dst
# match dl_src


#########################################
#	flow_expire
#	These test verify_match()
    flow_expire = FvRegress.OFVERSION + '''0b 00 58 00 00 00 01 00 3f ff ff 00 00 00 00
    00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
    00 00 00 00 00 00 00 00 00 00 00 00 80 00 01 00
    00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
    00 00 00 0b 00 00 00 00 00 00 00 00 00 00 01 e1
    00 00 00 00 00 00 02 58
    '''
    h.runTest(name="flow_expire in for All ",timeout=timeout,  events= [
    		TestEvent( "send","switch","switch1", flow_expire ),
    		TestEvent( "recv","guest","alice",  flow_expire),
    		TestEvent( "recv","guest","bob",  flow_expire),
    		TestEvent( "recv","guest","cathy",  flow_expire),
    		TestEvent( "recv","guest","doug",  flow_expire),
    		TestEvent( "recv","guest","eric",  flow_expire),
    		TestEvent( "recv","guest","frank",  flow_expire),
    		TestEvent( "recv","guest","george",  flow_expire),
    		TestEvent( "recv","guest","hank",  flow_expire),
    		TestEvent( "recv","guest","ingrid",  flow_expire),
            TestEvent( "clear?","switch","switch1", 'nil')
    		])
    flow_expire = FvRegress.OFVERSION + '''0b 00 58 00 00 00 01 00 3f ff bf 00 00 00 00
    00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
    00 00 00 00 00 00 00 00 00 50 00 00 00 50 01 00
    00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
    00 00 00 0b 00 00 00 00 00 00 00 00 00 00 01 e1
    00 00 00 00 00 00 02 58
    '''
    h.runTest(name="flow_expire in for doug ",timeout=timeout,  events= [
    		TestEvent( "send","switch","switch1", flow_expire ),
    		TestEvent( "recv","guest","doug",  flow_expire),
    		TestEvent( "recv","guest","eric",  flow_expire),
    		TestEvent( "recv","guest","frank",  flow_expire),
    		TestEvent( "recv","guest","george",  flow_expire),
    		TestEvent( "recv","guest","hank",  flow_expire),
    		TestEvent( "recv","guest","ingrid",  flow_expire),
            TestEvent( "clear?","switch","switch1", 'nil')
    		])
    flow_expire = FvRegress.OFVERSION + '''0b 00 58 00 00 00 03 00 3f ff 7f 00 00 00 00
    00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
    00 00 00 00 00 00 00 00 00 00 00 5a 80 00 00 5a
    00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
    00 00 00 0b 00 00 00 00 00 00 00 00 00 00 01 e1
    00 00 00 00 00 00 02 58
    '''
    h.runTest(name="flow_expire in for eric ",timeout=timeout,  events= [
    		TestEvent( "send","switch","switch1", flow_expire ),
    		TestEvent( "recv","guest","eric",  flow_expire),
    		TestEvent( "recv","guest","frank",  flow_expire),
    		TestEvent( "recv","guest","george",  flow_expire),
    		TestEvent( "recv","guest","hank",  flow_expire),
    		TestEvent( "recv","guest","ingrid",  flow_expire),
            TestEvent( "clear?","switch","switch1", 'nil')
    		])

#########################################
# more tests for this setup HERE
#################################### End Tests
finally:
    if wantPause:
    	doPause("start cleanup")
    h.cleanup()

