/**
 * *****************************************************************************
 * Copyright 2014 Barcelona Supercomputing Center (BSC)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 *****************************************************************************
 */
package com.servioticy.dispatcher;

import backtype.storm.Config;
import backtype.storm.LocalCluster;
import backtype.storm.StormSubmitter;
import backtype.storm.generated.AlreadyAliveException;
import backtype.storm.generated.InvalidTopologyException;
import backtype.storm.spout.KestrelThriftSpout;
import backtype.storm.topology.TopologyBuilder;
import backtype.storm.tuple.Fields;
import com.servioticy.dispatcher.bolts.*;
import org.apache.commons.cli.*;

import java.util.Arrays;

/**
 * @author Álvaro Villalba Navarro <alvaro.villalba@bsc.es>
 */
public class DispatcherTopology {

    /**
     * @param args
     * @throws InvalidTopologyException
     * @throws AlreadyAliveException
     * @throws InterruptedException
     */
    public static void main(String[] args) throws AlreadyAliveException, InvalidTopologyException, InterruptedException, ParseException {

        Options options = new Options();

        options.addOption(OptionBuilder.withArgName("file")
                .hasArg()
                .withDescription("Config file path.")
                .create("f"));
        options.addOption(OptionBuilder.withArgName("topology")
                .hasArg()
                .withDescription("Name of the topology in storm. If no name is given it will run in local mode.")
                .create("t"));
        options.addOption(OptionBuilder
                .withDescription("Enable debugging")
                .create("d"));
        options.addOption(OptionBuilder
                .withDescription("Enable topology debugging")
                .create("v"));

        CommandLineParser parser = new GnuParser();
        CommandLine cmd = parser.parse(options, args);

        String path = null;
        if (cmd.hasOption("f")) {
            path = cmd.getOptionValue("f");
        }

        DispatcherContext dc = new DispatcherContext();
        dc.loadConf(path);

        TopologyBuilder builder = new TopologyBuilder();

        // TODO Auto-assign workers to the spout in function of the number of Kestrel IPs
        builder.setSpout("updates", new KestrelThriftSpout(Arrays.asList(dc.updatesAddresses), dc.updatesPort, dc.updatesQueue, new UpdateDescriptorScheme()));
        builder.setSpout("actions", new KestrelThriftSpout(Arrays.asList(dc.actionsAddresses), dc.actionsPort, dc.actionsQueue, new ActuationScheme()));

        builder.setBolt("prepare", new PrepareBolt(dc))
                .shuffleGrouping("updates");

        builder.setBolt("actuationdispatcher", new ActuationDispatcherBolt(dc))
                .shuffleGrouping("actions");

        builder.setBolt("subretriever", new SubscriptionRetrieveBolt(dc))
                .shuffleGrouping("prepare", "subscription");

        builder.setBolt("externaldispatcher", new ExternalDispatcherBolt(dc))
                .fieldsGrouping("subretriever", "externalSub", new Fields("subid"));
        builder.setBolt("internaldispatcher", new InternalDispatcherBolt(dc))
                .fieldsGrouping("subretriever", "internalSub", new Fields("subid"));

        builder.setBolt("streamdispatcher", new StreamDispatcherBolt(dc))
                .shuffleGrouping("subretriever", "streamSub")
                .shuffleGrouping("prepare", "stream");
        builder.setBolt("streamprocessor", new StreamProcessorBolt(dc))
                .shuffleGrouping("streamdispatcher", "default");

        if (dc.benchmark) {
            builder.setBolt("benchmark", new BenchmarkBolt(dc))
                    .shuffleGrouping("streamdispatcher", "benchmark")
                    .shuffleGrouping("subretriever", "benchmark")
                    .shuffleGrouping("streamprocessor", "benchmark")
                    .shuffleGrouping("prepare", "benchmark");
        }

        Config conf = new Config();
        conf.setDebug(cmd.hasOption("d"));

        conf.put(Config.TOPOLOGY_DEBUG, cmd.hasOption("v"));

        if (cmd.hasOption("t")) {
            StormSubmitter.submitTopology(cmd.getOptionValue("t"), conf, builder.createTopology());
        } else {
            conf.setMaxTaskParallelism(4);
            LocalCluster cluster = new LocalCluster();
            cluster.submitTopology("dispatcher", conf, builder.createTopology());

        }

    }

}
