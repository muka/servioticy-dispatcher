
#Run Kestrel
rm -rf /tmp/kestrel-queue
rm -rf /tmp/kestrel.log

cd libs/kestrel/

echo "Start kestrel"
java -server -Xmx1024m -Dstage=servioticy_queues -jar kestrel_2.9.2-2.4.1.jar
