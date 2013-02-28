#!/bin/sh

SSL_KEYPASSWD=CHANGEME_PASSWD

configbase=/usr/local

#install_root is for installing to a new directory, e.g., for chroot()

if [ -z $configbase ] ; then
    configbase=`pwd`/`dirname $0`/..
    install_dir=$configbase/dist
    jars=$configbase/lib
    config_dir=$configbase
    SSL_KEYSTORE=$configbase/mySSLKeyStore
else
    install_dir=$install_root$configbase/libexec/vertigo
    jars=$install_dir
    config_dir=$install_root$configbase/etc/vertigo
    SSL_KEYSTORE=$config_dir/mySSLKeyStore
fi


fv_defines="-Dorg.flowvisor.config_dir=$config_dir -Dorg.flowvisor.install_dir=$install_dir"

# Setup some environmental variables
classpath=$jars/openflow.jar:\
$jars/xmlrpc-client-3.1.3.jar:\
$jars/xmlrpc-common-3.1.3.jar:\
$jars/xmlrpc-server-3.1.3.jar:\
$jars/commons-logging-1.1.jar:\
$jars/ws-commons-util-1.0.2.jar:\
$jars/jsse.jar:\
$jars/asm-3.0.jar:\
$jars/cglib-2.2.jar:\
$jars/commons-codec-1.4.jar:\
$jars/gson-1.7.1.jar:\
$jars/jetty-continuation-7.0.2.v20100331.jar:\
$jars/jetty-http-7.0.2.v20100331.jar:\
$jars/jetty-io-7.0.2.v20100331.jar:\
$jars/jetty-security-7.0.2.v20100331.jar:\
$jars/jetty-server-7.0.2.v20100331.jar:\
$jars/jetty-util-7.0.2.v20100331.jar:\
$jars/json-org.jar:\
$jars/hsqldb.jar:\
$jars/mysql-connector-java-5.1.15-bin.jar:\
$jars/servlet-api-2.5.jar:\
$install_dir/vertigo.jar

# ssl options for the jvm

sslopts="-Djavax.net.ssl.keyStore=$SSL_KEYSTORE -Djavax.net.ssl.keyStorePassword=$SSL_KEYPASSWD"

# for ssl debugging options
#sslopts="$sslopts -Djava.protocol.handler.pkgs=com.sun.net.ssl.internal.www.protocol -Djavax.net.debug=ssl"




