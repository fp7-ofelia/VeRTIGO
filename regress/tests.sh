#!/bin/sh

out=tests.results
sleep=0.5

#tests='tests-base.py  tests-ports.py tests-dynconfig.py tests-mobility.py tests-readonly.py tests-match.py tests-match_all.py tests-glen.py tests-deny.py tests-squelch.py tests-qos.py tests-of1-0.py'

tests="\
tests-base.py \
tests-api.py \
tests-ports.py \
tests-safety.py \
tests-deleteSlice.py \
tests-josh-arp.py \
tests-vlan.py \
tests-readonly.py \
tests-kk-topo.py \
tests-disconnected.py \
tests-stats.py \
tests-flowdb.py \
tests-config.sh \
tests-flood.py \
"

    

# need to completely fill in the test case
#tests-expand.py \
rm -f $out



cat << EOF 2>&1 | tee -a $out
----------------------------------------
----------------------------------------
----- Verfiying regression framework
----------------------------------------
----------------------------------------
EOF

./fvregress.py | tee -a $out 2>&1
grep -q FAILED $out
if [ $? -eq 0 ] ; then
    echo $p FAILED | tee -a $out
    exit 1
fi
for p in $tests ; do
	cat << EOF 2>&1 | tee -a $out
----------------------------------------
----------------------------------------
----- Running test $p
----------------------------------------
----------------------------------------
EOF
	./$p | tee -a $out 2>&1 
	grep -q FAILED $out
	if [ $? -eq 0 ] ; then
		echo $p FAILED | tee -a $out
		exit 1
	fi
	echo ----- Sleeping $sleep sec to let flowvisor shutdown
	sleep $sleep
done
	cat << EOF 2>&1 | tee -a $out
----------------------------------------
----------------------------------------
----- All Tests Succeeded!
----------------------------------------
----------------------------------------
EOF
