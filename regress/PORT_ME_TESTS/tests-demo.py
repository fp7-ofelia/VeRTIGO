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
	valgrindArgs= ['valgrind', '--log-file=valgrind.out'  ]

# start up a flowvisor with 1 switch (default) and two guests

#h= HyperTest(guests=[('localhost',54321)],
#	hyperargs=["-v0", "-a", "flowvisor-conf.d-demo", "ptcp:%d"% HyperTest.OFPORT],valgrind=valgrindArgs)

h = FvRegress.parseConfig(configDir='flowvisor-conf.d-demo',valgrind=valgrindArgs)


if wantPause:
	doPause("start tests")
#################################### Start Tests
try:
	exact_match_flow_mod = FvRegress.OFVERSION + '''0e004800010000000000000008000db916ef94001cf0ed985affff080006000a4f01650a4f01f2001682ba0000000500008000ffffffff00000000000000000000000800020000'''

	h.runTest(name="exact match flow mod",timeout=timeout,  events= [
			TestEvent( "send","guest","production", exact_match_flow_mod),
			TestEvent( "recv","switch","switch1", exact_match_flow_mod),
			])




#########################################
# more tests for this setup HERE
#################################### End Tests
finally:
	if wantPause:
		doPause("start cleanup")
	h.cleanup()

