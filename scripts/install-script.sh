#!/bin/sh

# sadly hacked together install script; really only used to bootstrap rpm or debs
# apologies in advance to anyone who has to debug this

prefix_default=/usr/local
root_default=""
binuser_default=$USER
bingroup_default=`groups | cut -f1 -d\ `
 
# if root is installing, then assume they don't want to run fv as root
if [ "X$USER" = "Xroot" ] ; then
	fvuser_default=root
	fvgroup_default=root
    sudo=""
else
	fvuser_default=$binuser_default
	fvgroup_default=$bingroup_default
    sudo=sudo
fi

install="$sudo install"
base=`dirname $0`/..
scriptd=$base/scripts
libs=$base/lib
dist=$base/dist
jni=$base/jni
config=vertigo-config.xml
#verbose=-v

usage="$0 [-p prefix_dir] [-u vertigo_user] [-g vertigo_group] [-r root_dir]"
usage_str="p:u:g:r:"

while getopts $usage_str opt; do
    case $opt in
    p)
        prefix=$OPTARG
        echo "Set prefix to '$prefix'" >&2
    ;;
    u)
        fvuser=$OPTARG
        echo "Set fvuser to '$vertigo_user'" >&2
    ;;
    g)
        fvgroup=$OPTARG
        echo "Set fvgroup to '$vertigo_group'" >&2
    ;;
    r)
        root=$OPTARG
        echo "Set root to '$root'" >&2
    ;;
    \?)
        echo "Invalid option: -$OPTARG" >&2
        cat << EOF  >&2
        Usage:
        $usage
            defaults:
                prefix_dir=$prefix_default
                fvuser=$fvuser_default
                fvgroup=$fvgroup_default
                root=$root_default
EOF
        exit 1
    ;;
esac
done

echo "Using source dir: $base"

test -z "$prefix" && read -p "Installation prefix ($prefix_default): " prefix
if [ "X$prefix" = "X" ] ; then
    prefix=$prefix_default
fi

test -z "$fvuser" && read -p "VeRTIGO User (needs to already exist) ($fvuser_default): " fvuser
if [ "X$fvuser" = "X" ] ; then
    fvuser=$fvuser_default
fi
id $fvuser 2>/dev/null 1>/dev/null

while [ "$?" -ne "0" ] ; do
    read -p "VeRTIGO User (needs to already exist, '$fvuser' does not exist) ($fvuser_default): " fvuser
    if [ "X$fvuser" = "X" ] ; then
    	fvuser=$fvuser_default
    fi
    id $fvuser 2>/dev/null 1>/dev/null
done

test -z "$fvgroup" && read -p "VeRTIGO Group (needs to already exist) ($fvgroup_default): " fvgroup
if [ "X$fvgroup" = "X" ] ; then
    fvgroup=$fvgroup_default
fi
id -g $fvgroup 2>/dev/null 1>/dev/null

while [ "$?" -ne "0" ] ; do
    read -p "VeRTIGO Group (needs to already exist, '$fvgroup' does not exist) ($fvgroup_default): " fvgroup
    if [ "X$fvgroup" = "X" ] ; then
        fvgroup=$fvgroup_default
    fi
    id -g $fvgroup 2>/dev/null 1>/dev/null
done


if [ "X$binuser" = "X" ] ; then
    binuser=$binuser_default
fi

if [ "X$bingroup" = "X" ] ; then
    bingroup=$bingroup_default
fi

test -z "$root" && read -p "Install to different root directory ($root_default) " root
if [ "X$root" = "X" ] ; then
    root=$root_default
fi


echo Installing VeRTIGO into $root$prefix with prefix=$prefix as user/group ${fvuser}:${fvgroup}

bin_SCRIPTS="\
    vectl \
    "

sbin_SCRIPTS="\
    veconfig \
    vertigo \
    "

LIBS="\
    commons-logging-1.1.jar \
    openflow.jar \
    jsse.jar \
    ws-commons-util-1.0.2.jar \
    xmlrpc-client-3.1.3.jar \
    xmlrpc-common-3.1.3.jar \
    xmlrpc-server-3.1.3.jar \
    asm-3.0.jar \
    cglib-2.2.jar \
    commons-codec-1.4.jar \
    gson-1.7.1.jar \
    jetty-continuation-7.0.2.v20100331.jar \
    jetty-http-7.0.2.v20100331.jar \
    jetty-io-7.0.2.v20100331.jar \
    jetty-security-7.0.2.v20100331.jar \
    jetty-server-7.0.2.v20100331.jar \
    jetty-util-7.0.2.v20100331.jar \
    servlet-api-2.5.jar \
    mysql-connector-java-5.1.15-bin.jar \
    hsqldb.jar \
    "

DOCS="\
    README
    README.dev
    INSTALL
    "

owd=`pwd`
cd $scriptd

for script in $bin_SCRIPTS $sbin_SCRIPTS envs ; do 
    echo Updating $script.sh to $script
    sed -e "s!#base=PREFIX!base=$prefix!" -e "s!#configbase=PREFIX!configbase=$prefix!"< $script.sh > $script
done

echo Creating directories

for d in bin sbin libexec/vertigo etc share/man/man1 share/man/man8 share/doc/vertigo ; do 
    echo Creating $prefix/$d
    $install $verbose --owner=$binuser --group=$bingroup --mode=755 -d $root$prefix/$d
done

for d in /etc/init.d /var/log/vertigo ; do
    if [ ! -d $root$d ] ; then
        echo Creating $d
        $install $verbose --owner=$binuser --group=$bingroup --mode=755 -d $root$d
    fi
done


echo "Creating $prefix/etc/vertigo (owned by user=$fvuser  group=$fvgroup)"
$install $verbose --owner=$fvuser --group=$fvgroup --mode=2755 -d $root$prefix/etc/vertigo

echo Installing scripts
$install $verbose --owner=$binuser --group=$bingroup --mode=755 $bin_SCRIPTS $root$prefix/bin
$install $verbose --owner=$binuser --group=$bingroup --mode=755 $sbin_SCRIPTS $root$prefix/sbin

echo "Installing SYSV startup script (not enabled by default)"
cp fv-startup.sh fv-startup
sed -i -e "s/FVUSER/$fvuser/" fv-startup
sed -i -e "s,PREFIX,$prefix," fv-startup
$install $verbose --owner=$binuser --group=$bingroup --mode=755 fv-startup  $root/etc/init.d/vertigo


echo Installing JNI libraries
cd $owd
cd $jni
make install DSTDIR=$root$prefix/libexec/vertigo

echo Installing jars
cd $owd
cd $libs
$install $verbose --owner=$binuser --group=$bingroup --mode=644 $LIBS $root$prefix/libexec/vertigo

echo Installing vertigo.jar
cd $owd
cd $dist
$install $verbose --owner=$binuser --group=$bingroup --mode=644 vertigo.jar  $root$prefix/libexec/vertigo

echo Installing manpages
cd $owd
cd doc
$install $verbose --owner=$binuser --group=$bingroup --mode=644 fvctl.1  $root$prefix/share/man/man1
$install $verbose --owner=$binuser --group=$bingroup --mode=644 fvconfig.1  $root$prefix/share/man/man1
$install $verbose --owner=$binuser --group=$bingroup --mode=644 flowvisor.8  $root$prefix/share/man/man8
# do we need to run makewhatis manually here? I think it's a cronjob on most systems


echo Installing configs
cd $owd
$install $verbose --owner=$fvuser --group=$fvgroup --mode=644 $scriptd/envs $root$prefix/etc/vertigo/envs.sh

echo Installing documentation
cd $owd
$install $verbose --owner=$binuser --group=$bingroup --mode=644 $DOCS $root$prefix/share/doc/vertigo

if [ ! -f $root$prefix/etc/vertigo/config.xml ] ; then 
    echo Generating a default config VeRTIGO config
    install_root=$root $root$prefix/sbin/veconfig generate $root$prefix/etc/vertigo/config.xml
fi
