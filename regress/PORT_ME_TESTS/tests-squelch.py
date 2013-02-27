#!/usr/bin/python
from fvregress import *
import string 	# really?  you have to do this?

if len(sys.argv) > 1 :
	wantPause = True
	timeout=9999999
	valgrindArgs= []
else:
	wantPause = False
	timeout=5
	valgrindArgs= None

# start up a flowvisor with 1 switch (default) and two guests

#h= HyperTest(guests=[
#			('localhost',54321),
#			('localhost',54322),
#			('localhost',54323),
#			('localhost',54324),
#			],
#	hyperargs=["-v0", "-a", "flowvisor-conf.d-squelch", "ptcp:%d"% HyperTest.OFPORT],valgrind=valgrindArgs)

h = FvRegress.parseConfig(configDir='flowvisor-conf.d-squelch', valgrind=valgrindArgs)


if wantPause:
	doPause("start tests")
#################################### Start Tests
try:


#####################################

	ping_in = FvRegress.OFVERSION + '''0a 00 74 00 00 00 00 00 00 35 db 00 62 00 01
	00 00 00 1c f0 ed 98 5a 00 22 41 fa 73 01 08 00
	45 00 00 54 f3 b9 00 00 40 01 00 97 09 09 09 09
	03 04 04 05 08 00 06 05 0d 07 00 37 d8 0c b1 49
	6b 63 05 00 08 09 0a 0b 0c 0d 0e 0f 10 11 12 13
	14 15 16 17 18 19 1a 1b 1c 1d 1e 1f 20 21 22 23
	24 25 26 27 28 29 2a 2b 2c 2d 2e 2f 30 31 32 33
	34 35 36 37'''

	squelch = FvRegress.OFVERSION + '''0e 00 48 ff ff 00 00 00 34 3f ff 00 00 00 00
    00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00
    00 00 00 00 00 00 00 00 03 04 00 00 00 00 00 00
    00 00 00 00 00 00 00 00 00 00 00 00 00 01 ff ff
    ff ff ff ff ff ff 00 00
	'''
	h.runTest(name="ping by ip_dst/prefix to Frank",timeout=timeout,  events= [
			TestEvent( "send","switch","switch1", ping_in),
			TestEvent( "recv","guest","frank", ping_in),
			TestEvent( "send","switch","switch1", ping_in),
			TestEvent( "recv","switch","switch1", squelch),
			])

#####################################
	packet_out_flood =  FvRegress.OFVERSION + '''0d 0058 0000 abcd ffff ffff
				ffff 0008 0000 0008 fffb 0080 0000 0000
				0001 0000 0000 0002 0800 4500 0032 0000
				4000 4011 2868 c0a8 c800 0304 0102 0001
				0000 001e d7c3 cdc0 251b e6dc ea0c 726d
                973f 2b71 c2e4 1b6f bc11 8250'''
			# note, the xid here is a function of the order of the tests;
			#	DO NOT CHANGE test order
	packet_out_flood_aftr_1 = FvRegress.OFVERSION + '''0d 00 70 04 01 00 00 ff ff ff ff ff ff 00 20
		00 00 00 08 00 00 00 80 00 00 00 08 00 01 00 80
		00 00 00 08 00 02 00 80 00 00 00 08 00 03 00 80
		00 00 00 00 00 01 00 00 00 00 00 02 08 00 45 00
		00 32 00 00 40 00 40 11 28 68 c0 a8 c8 00 03 04
		01 02 00 01 00 00 00 1e d7 c3 cd c0 25 1b e6 dc
		ea 0c 72 6d 97 3f 2b 71 c2 e4 1b 6f bc 11 82 50
		'''
	packet_out_flood_aftr_2 = FvRegress.OFVERSION + '''0d 00 70 01 01 00 00 ff ff ff ff ff ff 00 20
		00 00 00 08 00 00 00 80 00 00 00 08 00 01 00 80
		00 00 00 08 00 02 00 80 00 00 00 08 00 03 00 80
		00 00 00 00 00 01 00 00 00 00 00 02 08 00 45 00
		00 32 00 00 40 00 40 11 28 68 c0 a8 c8 00 03 04
		01 02 00 01 00 00 00 1e d7 c3 cd c0 25 1b e6 dc
		ea 0c 72 6d 97 3f 2b 71 c2 e4 1b 6f bc 11 82 50
		'''
	h.runTest(name="too many packet_outs by Frank",timeout=timeout,  events= [
			TestEvent( "send","guest","frank", packet_out_flood),
			TestEvent( "recv","switch","switch1", packet_out_flood_aftr_1),
			TestEvent( "send","guest","frank", packet_out_flood),
			TestEvent( "recv","switch","switch1", packet_out_flood_aftr_2),
			TestEvent( "send","guest","frank", packet_out_flood),
			#TestEvent( "recv","switch","switch1", packet_out_flood_aftr),
			#TestEvent( "send","guest","frank", packet_out_flood),
			TestEvent( "clear?","switch","switch1", 'nil'),
			])
#####################################
	packet_out_flood =  FvRegress.OFVERSION + '''0d 0058 0000 abcd ffff ffff
				ffff 0008 0000 0008 fffb 0080 0000 0000
				0001 0000 0000 0002 0800 4500 0032 0000
				4000 4011 2868 c0a8 c800 0304 0102 0001
				0000 001e d7c3 cdc0 251b e6dc ea0c 726d
                973f 2b71 c2e4 1b6f bc11 8250'''
			# note, the xid here is a function of the order of the tests;
			#	DO NOT CHANGE test order
	packet_out_flood_aftr_3 = FvRegress.OFVERSION + '''0d 00 70 06 01 00 00 ff ff ff ff ff ff 00 20
		00 00 00 08 00 00 00 80 00 00 00 08 00 01 00 80
		00 00 00 08 00 02 00 80 00 00 00 08 00 03 00 80
		00 00 00 00 00 01 00 00 00 00 00 02 08 00 45 00
		00 32 00 00 40 00 40 11 28 68 c0 a8 c8 00 03 04
		01 02 00 01 00 00 00 1e d7 c3 cd c0 25 1b e6 dc
		ea 0c 72 6d 97 3f 2b 71 c2 e4 1b 6f bc 11 82 50
		'''
	packet_out_flood_aftr_4 = FvRegress.OFVERSION + '''0d 00 70 03 01 00 00 ff ff ff ff ff ff 00 20
		00 00 00 08 00 00 00 80 00 00 00 08 00 01 00 80
		00 00 00 08 00 02 00 80 00 00 00 08 00 03 00 80
		00 00 00 00 00 01 00 00 00 00 00 02 08 00 45 00
		00 32 00 00 40 00 40 11 28 68 c0 a8 c8 00 03 04
		01 02 00 01 00 00 00 1e d7 c3 cd c0 25 1b e6 dc
		ea 0c 72 6d 97 3f 2b 71 c2 e4 1b 6f bc 11 82 50
		'''
#### Sleep a while to let flowvisor unrate limit things
	t=5
	print "############### Sleeping for " + str(t) + " seconds to let the rate limiters recharge"
	time.sleep(t);
	h.runTest(name="too many packet_outs by Frank (again)",timeout=timeout,  events= [
			TestEvent( "send","guest","frank", packet_out_flood),
			TestEvent( "recv","switch","switch1", packet_out_flood_aftr_3),
			TestEvent( "send","guest","frank", packet_out_flood),
			TestEvent( "recv","switch","switch1", packet_out_flood_aftr_4),
			TestEvent( "send","guest","frank", packet_out_flood),
			#TestEvent( "recv","switch","switch1", packet_out_flood_aftr),
			#TestEvent( "send","guest","frank", packet_out_flood),
			TestEvent( "clear?","switch","switch1", 'nil'),
			])
#########################################
# more tests for this setup HERE
#################################### End Tests
finally:
	if wantPause:
		doPause("start cleanup")
	h.cleanup()

