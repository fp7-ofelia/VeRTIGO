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

#h= HyperTest(guests=[('localhost',54321)],
#	hyperargs=["-v0", "-a", "flowvisor-conf.d-glen", "ptcp:%d"% HyperTest.OFPORT],valgrind=valgrindArgs)
h = FvRegress.parseConfig(configDir='flowvisor-conf.d-glen', valgrind=valgrindArgs)

if wantPause:
	doPause("start tests")
#################################### Start Tests
try:


	feature_request = 	 FvRegress.OFVERSION + '05 0008 2d47 c5eb'
	feature_request_after =  FvRegress.OFVERSION + '05 0008 0001 0000'
	h.runTest(name="feature_request",timeout=timeout,  events= [
			TestEvent( "send","guest","openpipes", feature_request),
			TestEvent( "recv","switch","switch1", feature_request_after),
			])


##############################################################
	#flow_mod_del_all = FvRegress.OFVERSION + '''0e0048efbefeca000fffff00000000000000000000000000000000000000000000000000000000000000000003000000000000ffffffffffff000000000000'''
	flow_mod_del_all =   FvRegress.OFVERSION + '''0e0048084a06b6ffffffff68e1b2080100000005f04e4f3cc1f9237cb79b6494c75a277565383900ecb2b70000000000000000000000000003000000000000ffff000000000000'''
	flow_mod_del_all_1 = FvRegress.OFVERSION + '''0e004802010000003820f768e100000000000000ff000000000000000000000000000000000000000000000000000000000000000000000003000000000000ffff000000000000'''
	flow_mod_del_all_2 = FvRegress.OFVERSION + '''0e004802010000003820f768e100000000000000ff000000010000000000000000000000000000000000000000000000000000000000000003000000000000ffff000000000000'''
	flow_mod_del_all_3 = FvRegress.OFVERSION + '''0e004802010000003820f768e100000000000000ff000000020000000000000000000000000000000000000000000000000000000000000003000000000000ffff000000000000'''
	flow_mod_del_all_4 = FvRegress.OFVERSION + '''0e004802010000003820f768e100000000000000ff000000030000000000000000000000000000000000000000000000000000000000000003000000000000ffff000000000000'''
	flow_mod_del_all_5 = FvRegress.OFVERSION + '''0e004802010000003820f768e100000000000000ff000000040000000000000000000000000000000000000000000000000000000000000003000000000000ffff000000000000'''
	flow_mod_del_all_6 = FvRegress.OFVERSION + '''0e004802010000003820f768e100000000000000ff000000050000000000000000000000000000000000000000000000000000000000000003000000000000ffff000000000000'''
	flow_mod_del_all_7 = FvRegress.OFVERSION + '''0e004802010000003820f768e100000000000000000c0000110000000000000000000000000000000000000000000000000000000000000003000000000000ffff000000000000'''
	flow_mod_del_all_8 = FvRegress.OFVERSION + '''0e004802010000003820f768e100000000000000188b2700010000000000000000000000000000000000000000000000000000000000000003000000000000ffff000000000000'''
	h.runTest(name="flow mod del all ", timeout=timeout, events= [
			TestEvent( "send","guest","openpipes", flow_mod_del_all),
			TestEvent( "recv","switch","switch1", flow_mod_del_all_1),
			TestEvent( "recv","switch","switch1", flow_mod_del_all_2),
			TestEvent( "recv","switch","switch1", flow_mod_del_all_3),
			TestEvent( "recv","switch","switch1", flow_mod_del_all_4),
			TestEvent( "recv","switch","switch1", flow_mod_del_all_5),
			TestEvent( "recv","switch","switch1", flow_mod_del_all_6),
			TestEvent( "recv","switch","switch1", flow_mod_del_all_7),
			TestEvent( "recv","switch","switch1", flow_mod_del_all_8),
			])
##############################################################


	flow_mod = FvRegress.OFVERSION + '''0e0048000000000ffffffe00060000000000000000000000000000000000000000000000000000000000000000000000008000ffffffffffff06e8000000000000000800010000'''

	flow_mod_exp1 = FvRegress.OFVERSION + '''0e004803010000003820f6000600000000000000ff000000000000000000000000000000000000000000000000000000008000ffffffffffff06e8000000000000000800010000'''
	flow_mod_exp2 = FvRegress.OFVERSION + '''0e004803010000003820f6000600000000000000ff000000010000000000000000000000000000000000000000000000008000ffffffffffff06e8000000000000000800010000'''
	flow_mod_exp3 = FvRegress.OFVERSION + '''0e004803010000003820f6000600000000000000ff000000020000000000000000000000000000000000000000000000008000ffffffffffff06e8000000000000000800010000'''
	flow_mod_exp4 = FvRegress.OFVERSION + '''0e004803010000003820f6000600000000000000ff000000030000000000000000000000000000000000000000000000008000ffffffffffff06e8000000000000000800010000'''
	flow_mod_exp5 = FvRegress.OFVERSION + '''0e004803010000003820f6000600000000000000ff000000040000000000000000000000000000000000000000000000008000ffffffffffff06e8000000000000000800010000'''
	flow_mod_exp6 = FvRegress.OFVERSION + '''0e004803010000003820f6000600000000000000ff000000050000000000000000000000000000000000000000000000008000ffffffffffff06e8000000000000000800010000'''
	flow_mod_exp7 = FvRegress.OFVERSION + '''0e004803010000003820f6000600000000000000000c0000110000000000000000000000000000000000000000000000008000ffffffffffff06e8000000000000000800010000'''
	flow_mod_exp8 = FvRegress.OFVERSION + '''0e004803010000003820f6000600000000000000188b2700010000000000000000000000000000000000000000000000008000ffffffffffff06e8000000000000000800010000'''

	h.runTest(name="glen test 1", timeout=timeout, events= [
			TestEvent( "send","guest","openpipes", flow_mod),
			TestEvent( "recv","switch","switch1", flow_mod_exp1),
			TestEvent( "recv","switch","switch1", flow_mod_exp2),
			TestEvent( "recv","switch","switch1", flow_mod_exp3),
			TestEvent( "recv","switch","switch1", flow_mod_exp4),
			TestEvent( "recv","switch","switch1", flow_mod_exp5),
			TestEvent( "recv","switch","switch1", flow_mod_exp6),
			TestEvent( "recv","switch","switch1", flow_mod_exp7),
			TestEvent( "recv","switch","switch1", flow_mod_exp8),
			])

#########################################
# more tests for this setup HERE
#################################### End Tests
finally:
	if wantPause:
		doPause("start cleanup")
	h.cleanup()

