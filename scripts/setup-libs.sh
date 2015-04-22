#!/bin/bash

storm_file="apache-storm-0.9.4"
storm_url="http://mirrors.muzzy.it/apache/storm/$storm_file/$storm_file.tar.gz"

kestrel_file="kestrel-2.4.1"
kestrel_url="http://twitter.github.io/kestrel/download/$kestrel_file.zip"

scala_queue="https://raw.githubusercontent.com/servioticy/servioticy-vagrant/security/puppet/files/servioticy_queues.scala"

cd libs/

echo "Install STORM"
if [ ! -d "./storm" ]; then
	wget $storm_url
	rm -rf storm
	rm -rf $storm_file
	tar xzf "$storm_file.tar.gz"
	mv $storm_file storm
fi

echo "Install Kestrel"
if [ ! -d "./kestrel" ]; then
	#Install Kestrel
	wget $kestrel_url
	rm -rf $kestrel_file
	unzip "$kestrel_file.zip"
	mv $kestrel_file kestrel
	wget $scala_queue
	mv servioticy_queues.scala kestrel/config

fi;

echo "Done"
