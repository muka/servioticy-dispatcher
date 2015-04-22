
cd libs/storm/

echo "Start storm"
bin/storm jar ../../dispatcher.jar com.servioticy.dispatcher.DispatcherTopology -f ../../dispatcher.xml -d
