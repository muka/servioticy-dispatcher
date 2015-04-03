
DISPATCHER_HOME="`pwd`/../"

storm/bin/storm jar $DISPATCHER_HOME/dispatcher.jar com.servioticy.dispatcher.DispatcherTopology -f $DISPATCHER_HOME/dispatcher.xml

