BPATH="./"

#Run Kestrel
echo "Start kestrel"

rm -rf /tmp/kestrel-queue
rm -rf /tmp/kestrel.log

java -server -Xmx1024m -Dstage=servioticy_queues -jar ${BPATH}kestrel/kestrel_2.9.2-2.4.1.jar 
