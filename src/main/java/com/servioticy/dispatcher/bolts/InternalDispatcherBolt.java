/*******************************************************************************
 * Copyright 2014 Barcelona Supercomputing Center (BSC)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/ 
package com.servioticy.dispatcher.bolts;

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.IRichBolt;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.tuple.Tuple;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.servioticy.datamodel.sensorupdate.SensorUpdate;
import com.servioticy.datamodel.subscription.InternalSubscription;
import com.servioticy.dispatcher.DispatcherContext;
import com.servioticy.dispatcher.SUCache;
import com.servioticy.dispatcher.publishers.Publisher;
import de.passau.uni.sec.compose.pdp.servioticy.LocalPDP;
import de.passau.uni.sec.compose.pdp.servioticy.PDP;
import de.passau.uni.sec.compose.pdp.servioticy.PermissionCacheObject;
import org.apache.log4j.Logger;

import java.util.Map;


public class InternalDispatcherBolt implements IRichBolt {

	private static final long serialVersionUID = 1L;
	private OutputCollector collector;
	private TopologyContext context;
	private Publisher publisher;
	private SUCache suCache;
	private DispatcherContext dc;
	private static Logger LOG = Logger.getLogger(InternalDispatcherBolt.class);
	private ObjectMapper mapper;
    private PDP pdp;


    public InternalDispatcherBolt(DispatcherContext dc){
        this.dc = dc;
	}
	
	
	public void prepare(Map stormConf, TopologyContext context,
			OutputCollector collector) {
		this.mapper = new ObjectMapper();
		this.collector = collector;
		this.context = context;
		this.publisher = null;
		this.suCache = new SUCache(25);
        this.pdp = new LocalPDP();
        // Placeholders
        this.pdp.setIdmHost("");
        this.pdp.setIdmPort(0);
        this.pdp.setIdmUser("");
        this.pdp.setIdmPassword("");

		LOG.debug("Service publisher server: " + dc.internalPubAddress + ":" + dc.internalPubPort);
		LOG.debug("Service publisher user/pass: " + dc.internalPubUser + " / "+dc.internalPubPassword);

		
		try {
			publisher = Publisher.factory(dc.internalPubClassName, dc.internalPubAddress, dc.internalPubPort, String.valueOf(context.getThisTaskId()));
		} catch (Exception e) {
			LOG.error("Prepare: ", e);
		}

		try {
			publisher.connect(dc.internalPubUser, dc.internalPubPassword);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void execute(Tuple input) {
		InternalSubscription internalSub;
		SensorUpdate su;
		String sourceSOId;
		String streamId;
        PermissionCacheObject pco = new PermissionCacheObject();

        try{
			su = mapper.readValue(input.getStringByField("su"),
					SensorUpdate.class);
			internalSub = mapper.readValue(input.getStringByField("subsdoc"),
					InternalSubscription.class);
			sourceSOId = input.getStringByField("soid");
			streamId = input.getStringByField("streamid");
			if(suCache.check(internalSub.getId(), su.getLastUpdate())){
				// This SU or a posterior one has already been sent, do not send this one.
				collector.ack(input);
				return;
			}
		}catch (Exception e) {
			LOG.error("FAIL", e);
			collector.fail(input);
			return;
		}

		String suStr = input.getStringByField("su");
		try {
			if(!publisher.isConnected()){
				publisher.connect(dc.internalPubUser,
						dc.internalPubPassword);
			}
            pco.setUserId(internalSub.getUserId());
            pco = this.pdp.checkAuthorization(null, null, mapper.readTree(mapper.writeValueAsString(su.getSecurity())), pco,
                    PDP.operationID.DispatchData);
            su.setSecurity(null);
            if(!pco.isPermission()){
                // TODO Needs logging
                collector.ack(input);
                return;
            }
			publisher.publishMessage(internalSub.getDestination()+"/"+sourceSOId+"/streams/"+streamId+"/updates", suStr);
		} catch (Exception e) {
			LOG.error("FAIL", e);
			collector.fail(input);
			return;
		}
		suCache.put(internalSub.getId(), su.getLastUpdate());
		collector.ack(input);
	}

	public void cleanup() {
	}

	public void declareOutputFields(OutputFieldsDeclarer declarer) {
	}

	public Map<String, Object> getComponentConfiguration() {
		return null;
	}

}
