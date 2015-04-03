
STORM_VERSION="0.9.2"
STORM_URL="http://www.eu.apache.org/dist/storm/apache-storm-$STORM_VERSION-incubating/apache-storm-$STORM_VERSION-incubating.tar.gz"

KESTREL_VERSION="2.4.1"
KESTREL_URL="http://twitter.github.io/kestrel/download/kestrel-$KESTREL_VERSION.zip"

#http://www.servioticy.com/?page_id=201

#Install STORM
##IMPORTANT: This is a non-distributed installation of STORM for testing purposes! No need

echo "Get storm"

if [ ! -e "apache-storm-$STORM_VERSION-incubating.tar.gz" ]
then
  wget $STORM_URL
fi

if [ -d "storm" ]
then
  rm -rf storm
  rm -rf "apache-storm-$STORM_VERSION-incubating"
fi

tar xzf "apache-storm-$STORM_VERSION-incubating.tar.gz" > /dev/null
mv "apache-storm-$STORM_VERSION-incubating" storm

#Install Kestrel

echo "Get kestrel"

if [ ! -e "kestrel-$KESTREL_VERSION.zip" ]
then
  wget $KESTREL_URL
fi

if [ -d "kestrel" ]
then
  rm -rf kestrel
  rm -rf kestrel-$KESTREL_VERSION
fi

unzip "kestrel-$KESTREL_VERSION.zip" > /dev/null
mv "kestrel-$KESTREL_VERSION" kestrel

if [ ! -e "servioticy_queues.scala" ]
then
  wget https://raw.githubusercontent.com/servioticy/servioticy-vagrant/master/puppet/files/servioticy_queues.scala
fi

cp servioticy_queues.scala kestrel/config

echo "Done"
